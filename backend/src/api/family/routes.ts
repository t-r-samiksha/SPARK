import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireSession } from '../auth/requireSession';
import { issuePurseToken } from '../purse/issuePurseToken';

// Phase 9 — Family Wallet. Not in docs/api-contract.md at all (added later, like escrow — see
// src/api/escrow/routes.ts for the same situation). A parent funds a scoped purse token for a
// child device out of the parent's own real_balance, composing the same primitives POST
// /purse/load and POST /escrow/create already established rather than inventing new ones: an
// atomic real_balance debit (purse/load) and a computed-not-stored spend figure derived from
// settled transaction history (escrowLedger.ts's getAvailablePurseBalance).

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DECIMAL_PAISE_PATTERN = /^[0-9]+$/;

interface FamilyAllocationRow {
  id: string;
  parentAccountId: string;
  childDeviceId: string;
  tokenId: string;
  allocatedAmount: string;
  cap: string;
  active: boolean;
  createdAt: Date;
}

/**
 * Computes spent_amount the same way escrowLedger.ts's getAvailablePurseBalance computes settled
 * spend — summing settled Transaction.amount for the allocation's own token_id — rather than
 * trusting FamilyAllocation.spentAmount (which is never written; see the schema.prisma comment on
 * that column). Matches the computed-not-stored philosophy already established for escrow and
 * purse balances: no mutable running total that could drift from what settled history actually
 * supports.
 */
async function computeSpentAmount(tokenId: string): Promise<string> {
  const settled = await prisma.transaction.findMany({ where: { tokenId } });
  return settled.reduce((sum, row) => sum + BigInt(row.amount), 0n).toString();
}

async function serializeAllocation(allocation: FamilyAllocationRow) {
  return {
    id: allocation.id,
    parent_account_id: allocation.parentAccountId,
    child_device_id: allocation.childDeviceId,
    allocated_amount: allocation.allocatedAmount,
    spent_amount: await computeSpentAmount(allocation.tokenId),
    cap: allocation.cap,
    active: allocation.active,
    created_at: Math.floor(allocation.createdAt.getTime() / 1000),
  };
}

export default async function familyRoutes(fastify: FastifyInstance): Promise<void> {
  // --- POST /family/allocate -----------------------------------------------------------------
  interface FamilyAllocateBody {
    parent_account_id: string;
    child_device_id: string;
    allocated_amount: string;
    cap: string;
  }

  const familyAllocateSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['parent_account_id', 'child_device_id', 'allocated_amount', 'cap'],
      properties: {
        parent_account_id: { type: 'string' },
        child_device_id: { type: 'string' },
        allocated_amount: { type: 'string' },
        cap: { type: 'string' },
      },
    },
  };

  fastify.post<{ Body: FamilyAllocateBody }>(
    '/family/allocate',
    { schema: familyAllocateSchema },
    async (request: FastifyRequest<{ Body: FamilyAllocateBody }>, reply: FastifyReply) => {
      const sessionDeviceId = requireSession(request, reply);
      if (!sessionDeviceId) {
        return;
      }

      const { parent_account_id, child_device_id, allocated_amount, cap } = request.body;

      if (!UUID_V4_PATTERN.test(parent_account_id) || !UUID_V4_PATTERN.test(child_device_id)) {
        return reply
          .code(400)
          .send({ error: 'parent_account_id and child_device_id must be canonical (lowercase) UUID v4' });
      }
      if (!DECIMAL_PAISE_PATTERN.test(allocated_amount)) {
        return reply.code(400).send({ error: 'allocated_amount must be a decimal-paise string, e.g. "25000"' });
      }
      if (!DECIMAL_PAISE_PATTERN.test(cap)) {
        return reply.code(400).send({ error: 'cap must be a decimal-paise string, e.g. "5000"' });
      }

      // The caller's own session must belong to the parent account — same pattern
      // POST /escrow/create uses to confirm the caller is the buyer it claims to be.
      const sessionDevice = await prisma.device.findUnique({ where: { deviceId: sessionDeviceId } });
      if (!sessionDevice) {
        return reply.code(404).send({ error: 'session device not found' });
      }
      if (sessionDevice.accountId !== parent_account_id) {
        return reply.code(403).send({ error: 'session does not belong to the parent account' });
      }

      const childDevice = await prisma.device.findUnique({ where: { deviceId: child_device_id } });
      if (!childDevice) {
        return reply.code(400).send({ error: 'child_device_id does not belong to a real enrolled device' });
      }

      const account = await prisma.account.findUnique({ where: { id: parent_account_id } });
      if (!account) {
        return reply.code(404).send({ error: 'parent account not found' });
      }

      const requestedAmount = BigInt(allocated_amount);
      if (requestedAmount > account.realBalance) {
        return reply.code(400).send({
          error: `insufficient balance: requested ${allocated_amount} paise, account has ${account.realBalance} paise`,
        });
      }

      // The child's purse token is capped at whichever is smaller: what the parent asked for, or
      // the total pot being allocated — a per-spend cap larger than the whole allocation wouldn't
      // mean anything.
      const requestedCap = BigInt(cap);
      const effectiveCap = requestedCap < requestedAmount ? cap : allocated_amount;

      // Same "continue from the child's last purse token" pattern POST /purse/load uses — see
      // that route for the caveat this carries forward (no real settled-history-derived
      // counter_start yet).
      const previousToken = await prisma.purseToken.findFirst({
        where: { deviceId: child_device_id },
        orderBy: { createdAt: 'desc' },
      });
      const counterStart = previousToken ? previousToken.counterStart : 0;

      const { purseToken, pem } = issuePurseToken({
        device_id: child_device_id,
        value: allocated_amount,
        cap: effectiveCap,
        counter_start: counterStart,
      });

      // Debit the parent's real_balance, mint the child's purse token, and record the allocation
      // all in one DB transaction — same atomicity reasoning as POST /purse/load (see that
      // route's comment on the accepted TOCTOU gap between the sufficiency check above and this
      // transaction, which applies here identically).
      const allocation = await prisma.$transaction(async (tx) => {
        await tx.account.update({
          where: { id: parent_account_id },
          data: { realBalance: { decrement: requestedAmount } },
        });
        await tx.purseToken.create({
          data: {
            tokenId: purseToken.token_id,
            deviceId: purseToken.device_id,
            value: purseToken.value,
            cap: purseToken.cap,
            counterStart: purseToken.counter_start,
            expiry: purseToken.expiry,
            signature: purseToken.signature,
          },
        });
        return tx.familyAllocation.create({
          data: {
            parentAccountId: parent_account_id,
            childDeviceId: child_device_id,
            tokenId: purseToken.token_id,
            allocatedAmount: allocated_amount,
            cap: effectiveCap,
          },
        });
      });

      return reply.code(200).send({ allocation: await serializeAllocation(allocation), purse_token: pem });
    },
  );

  // --- GET /family/activity --------------------------------------------------------------------
  interface FamilyActivityQuery {
    parent_account_id: string;
  }

  const familyActivitySchema = {
    querystring: {
      type: 'object',
      additionalProperties: false,
      required: ['parent_account_id'],
      properties: {
        parent_account_id: { type: 'string' },
      },
    },
  };

  fastify.get<{ Querystring: FamilyActivityQuery }>(
    '/family/activity',
    { schema: familyActivitySchema },
    async (request: FastifyRequest<{ Querystring: FamilyActivityQuery }>, reply: FastifyReply) => {
      const sessionDeviceId = requireSession(request, reply);
      if (!sessionDeviceId) {
        return;
      }

      const parentAccountId = request.query.parent_account_id;
      if (!UUID_V4_PATTERN.test(parentAccountId)) {
        return reply.code(400).send({ error: 'parent_account_id must be a canonical (lowercase) UUID v4' });
      }

      // Session-scoped: the caller can only see allocations for the parent account their own
      // device belongs to, not any account_id they happen to pass — same check as
      // POST /family/allocate above.
      const sessionDevice = await prisma.device.findUnique({ where: { deviceId: sessionDeviceId } });
      if (!sessionDevice) {
        return reply.code(404).send({ error: 'session device not found' });
      }
      if (sessionDevice.accountId !== parentAccountId) {
        return reply.code(403).send({ error: 'session does not belong to the parent account' });
      }

      const allocations = await prisma.familyAllocation.findMany({
        where: { parentAccountId },
        orderBy: { createdAt: 'asc' },
      });

      return reply.code(200).send({ allocations: await Promise.all(allocations.map(serializeAllocation)) });
    },
  );
}

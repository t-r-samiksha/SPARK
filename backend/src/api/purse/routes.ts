import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireSession } from '../auth/requireSession';
import { getRecommendedCap } from './limitStub';
import { issuePurseToken } from './issuePurseToken';

// Integer paise as a decimal string, per docs/id-conventions.md#amounts.
const DECIMAL_PAISE_PATTERN = /^[0-9]+$/;

// POST /api/v1/purse/load, POST /api/v1/purse/topup, GET /api/v1/purse/status, and
// GET /api/v1/limit/recommendation — see docs/api-contract.md and docs/purse-token-format.md.
// `limit/recommendation` isn't nested under `/purse` in the contract, but it's grouped here since
// it's the input to sizing a purse token's cap — a judgment call, flag if trust/ fits better.
export default async function purseRoutes(fastify: FastifyInstance): Promise<void> {
  // RESOLVED (was an open question): `value` is a transfer from the account's real_balance to
  // the offline purse, not a live external bank check. docs/api-contract.md's /purse/load request
  // body was originally documented as `{}` (empty) — we deviate from that literal schema and
  // require `value` in the request body; see the docs/api-contract.md update alongside this
  // change. The account's real_balance is debited by `value` below (atomically with issuing the
  // token) — see the comment further down for why this simulates bank debiting rather than
  // calling out to a real bank.
  interface PurseLoadBody {
    value: string;
  }

  const purseLoadSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['value'],
      properties: {
        value: { type: 'string' },
      },
    },
  };

  fastify.post<{ Body: PurseLoadBody }>(
    '/purse/load',
    { schema: purseLoadSchema },
    async (request: FastifyRequest<{ Body: PurseLoadBody }>, reply: FastifyReply) => {
      const deviceId = requireSession(request, reply);
      if (!deviceId) {
        return; // requireSession already sent the 401.
      }

      const { value } = request.body;
      if (!DECIMAL_PAISE_PATTERN.test(value)) {
        return reply.code(400).send({ error: 'value must be a decimal-paise string, e.g. "25000"' });
      }

      // cap is server-determined (the AI-recommended offline spend limit), never client-supplied
      // — see limitStub.ts. Enforcing value <= cap is a direct reading of
      // docs/purse-token-format.md#value-vs-cap ("cap is... the maximum the backend is willing to
      // authorize this device to hold offline"), distinct from the open question above about
      // where the value number itself is sourced from.
      const cap = getRecommendedCap();
      const requestedValue = BigInt(value);
      if (requestedValue > BigInt(cap)) {
        return reply.code(400).send({ error: `value (${value}) exceeds the recommended cap (${cap})` });
      }

      // requireSession already confirmed this device_id belongs to a real, previously-enrolled
      // session, so this should always find a row — the null check is defensive, not an expected
      // path (e.g. the device row was deleted between session issuance and this request).
      const device = await prisma.device.findUnique({ where: { deviceId } });
      if (!device) {
        return reply.code(404).send({ error: 'device not found' });
      }

      const account = await prisma.account.findUnique({ where: { id: device.accountId } });
      if (!account) {
        return reply.code(404).send({ error: 'account not found' });
      }
      if (requestedValue > account.realBalance) {
        return reply.code(400).send({
          error: `insufficient balance: requested ${value} paise, account has ${account.realBalance} paise`,
        });
      }

      // JUDGMENT CALL: counter_start "continues from the device's last purse if one exists" (per
      // the task) rather than always resetting to 0. But without the sync/settlement engine
      // (src/settlement/, not implemented yet) there's no way to know how many transactions were
      // actually spent against a previous purse, so this carries the previous token's
      // counter_start forward unchanged — a placeholder that avoids silently regressing a
      // device's counter on every reload. Once sync/settlement exists, this should instead derive
      // counter_start from the device's actual synced transaction history. Also unresolved
      // upstream (see transaction-format.md's open questions): whether device_counter is scoped
      // per purse token or a single ever-increasing counter per device — this implementation
      // assumes the latter (hence "continue"), but that's not confirmed.
      const previousToken = await prisma.purseToken.findFirst({
        where: { deviceId },
        orderBy: { createdAt: 'desc' },
      });
      const counterStart = previousToken ? previousToken.counterStart : 0;

      const { purseToken, pem } = issuePurseToken({
        device_id: deviceId,
        value,
        cap,
        counter_start: counterStart,
      });

      // Simulates the bank debiting the linked account to fund the offline purse. Real
      // bank-account integration is out of scope per the project spec (see docs root). The
      // decrement and the PurseToken insert run in one DB transaction so a crash between the two
      // steps can never leave the account debited without an issued token, or vice versa.
      // NOTE: the sufficiency check above isn't part of this same transaction, so a narrow TOCTOU
      // race exists under concurrent /purse/load calls against the same account — an accepted
      // hackathon-scope simplification; production should use a conditional decrement (e.g.
      // `UPDATE ... WHERE real_balance >= value`) instead.
      await prisma.$transaction(async (tx) => {
        await tx.account.update({
          where: { id: device.accountId },
          data: { realBalance: { decrement: requestedValue } },
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
      });

      return reply.code(200).send({ purse_token: pem });
    },
  );

  fastify.post('/purse/topup', async (_request, reply) => {
    // TODO: given { token_id, amount }, issue a refilled PurseToken (open question: new
    // token_id or same one re-signed — see purse-token-format.md open questions).
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.get('/purse/status', async (_request, reply) => {
    // TODO: return { remaining, cap, expiry } for the authenticated device's current purse.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.get('/limit/recommendation', async (_request, reply) => {
    // TODO: return { recommended_cap } — AI-suggested offline spend cap for the authenticated
    // device. Response shape beyond this field is unspecified in kickoff.
    reply.code(501).send({ error: 'not implemented' });
  });
}

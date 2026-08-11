import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireSession } from '../auth/requireSession';
import { settleTransactionBatch } from '../../settlement/engine';
import { getRecommendedCap } from '../purse/limitStub';
import { buildAttestationPem } from '../trust/buildAttestationPem';
import { Transaction } from '../../types';

// Mirrors the `party` and `Transaction` schemas in docs/api-contract.md /
// docs/transaction-format.md exactly, so malformed batches are rejected at the HTTP layer before
// ever reaching the settlement engine.
const partySchema = {
  type: 'object',
  additionalProperties: false,
  required: ['device_id', 'account_id', 'cert'],
  properties: {
    device_id: { type: 'string' },
    account_id: { type: 'string' },
    cert: { type: 'string' },
  },
};

const transactionSchema = {
  type: 'object',
  additionalProperties: false,
  required: [
    'tx_id',
    'token_id',
    'amount',
    'payer',
    'payee',
    'device_counter',
    'prev_tx_hash',
    'timestamp',
    'signature',
  ],
  properties: {
    tx_id: { type: 'string' },
    token_id: { type: 'string' },
    amount: { type: 'string', pattern: '^[0-9]+$' },
    payer: partySchema,
    payee: partySchema,
    device_counter: { type: 'integer', minimum: 0 },
    prev_tx_hash: { type: ['string', 'null'] },
    timestamp: { type: 'integer', minimum: 0 },
    signature: { type: 'string' },
  },
};

interface SyncTransactionsBody {
  transactions: Transaction[];
}

const syncTransactionsSchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['transactions'],
    properties: {
      transactions: { type: 'array', items: transactionSchema },
    },
  },
};

// GET /api/v1/sync/updates. docs/api-contract.md documents this as taking no query parameters at
// all and returning {crl, flags, trust_attestations} with `crl` as the *full* list of revoked
// serials every time. That doesn't scale over the weak/metered links this system is designed
// for, so we add a `since` cursor param and two new response fields (`crl_cursor`,
// `recommended_cap`) not in the current doc — see docs/api-contract.md's own update alongside
// this change, and the JUDGMENT CALL comments below for the specific choices.
interface SyncUpdatesQuery {
  since?: string;
}

const syncUpdatesSchema = {
  querystring: {
    type: 'object',
    additionalProperties: false,
    properties: {
      // Unix epoch seconds, as a string (querystring values arrive as strings) — see the
      // JUDGMENT CALL comment in the handler for why epoch seconds, not ISO 8601.
      since: { type: 'string', pattern: '^[0-9]+$' },
    },
  },
};

// POST /api/v1/sync/transactions and GET /api/v1/sync/updates — see docs/api-contract.md.
export default async function syncRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: SyncTransactionsBody }>(
    '/sync/transactions',
    { schema: syncTransactionsSchema },
    async (request: FastifyRequest<{ Body: SyncTransactionsBody }>, reply: FastifyReply) => {
      // docs/api-contract.md#authentication: required on every endpoint except
      // /enroll, /auth/challenge, /auth/verify. Any authenticated, enrolled device may sync a
      // batch — the caller doesn't have to be the payer or payee of every transaction in it (a
      // merchant device syncing many customers' transactions is the payee in each, never the
      // payer, for example).
      const callerDeviceId = requireSession(request, reply);
      if (!callerDeviceId) {
        return;
      }

      const { results, incidents } = await settleTransactionBatch(request.body.transactions);
      return reply.code(200).send({ results, incidents });
    },
  );

  fastify.get<{ Querystring: SyncUpdatesQuery }>(
    '/sync/updates',
    { schema: syncUpdatesSchema },
    async (request: FastifyRequest<{ Querystring: SyncUpdatesQuery }>, reply: FastifyReply) => {
      const deviceId = requireSession(request, reply);
      if (!deviceId) {
        return;
      }

      // JUDGMENT CALL: docs/api-contract.md doesn't document a `since` param at all yet (see the
      // route-level comment below) — `since` is Unix epoch seconds, not an ISO 8601 string,
      // despite the task offering both as options. id-conventions.md is explicit that everything
      // defaults to epoch seconds except two named exceptions (cert validity window,
      // trust_attestation.timestamp) and that a third format shouldn't be introduced without a
      // docs PR — `since` isn't one of those two exceptions, so epoch seconds is what's
      // consistent with the rest of the system, not a coin flip.
      const sinceDate = request.query.since !== undefined ? new Date(Number(request.query.since) * 1000) : undefined;

      // `>=`, not `>`: epoch-*seconds* precision means a revocation landing in the same second as
      // a previously-returned cursor could otherwise be silently skipped. This can cause a client
      // to receive an already-delivered serial again on rare occasion — harmless (CRL entries are
      // idempotent to reapply) — rather than ever missing one, which is the wrong failure mode
      // for a revocation list.
      const revoked = await prisma.revokedCertificate.findMany({
        where: sinceDate ? { revokedAt: { gte: sinceDate } } : undefined,
        orderBy: { revokedAt: 'asc' },
      });

      // Per the task: no device-location data exists yet, so every currently-active disaster
      // event goes to every device, unfiltered. Refine with real region matching once dashboard
      // location data exists.
      const activeDisasters = await prisma.disasterEvent.findMany({ where: { active: true } });

      // Trust attestations involving the calling device — same query shape and PEM-building logic
      // as GET /trust/attestations (see buildAttestationPem.ts), unfiltered by `since` (that
      // cursor only applies to the CRL above; attestations don't have a delta mechanism yet).
      const trustEdges = await prisma.trustAttestation.findMany({
        where: { OR: [{ subjectA: deviceId }, { subjectB: deviceId }] },
      });

      // Best-effort observability write — see the field comment on Device.lastSyncUpdatesAt in
      // schema.prisma for why this isn't part of the delta computation itself.
      await prisma.device.updateMany({ where: { deviceId }, data: { lastSyncUpdatesAt: new Date() } });

      return reply.code(200).send({
        crl: revoked.map((r) => r.certSerial),
        // Not in docs/api-contract.md today — the cursor a client should send back as `since` on
        // its next call. Using "now" (not the max revokedAt among returned rows) so an empty
        // delta still advances the cursor instead of the client re-querying the same window
        // forever.
        crl_cursor: Math.floor(Date.now() / 1000),
        flags: activeDisasters.map((event) => ({
          // Discriminator for the flags array — docs/api-contract.md's open question about
          // `flags` explicitly considers "per-account fraud flags, disaster-mode region flags, or
          // both"; `kind` lets a future fraud-flag shape coexist in the same array unambiguously.
          kind: 'disaster' as const,
          type: event.type,
          region_geo: event.regionGeo,
          active: event.active,
          higher_cap: event.higherCap,
          essential_only: event.essentialOnly,
          started_at: Math.floor(event.startedAt.getTime() / 1000),
          ended_at: event.endedAt ? Math.floor(event.endedAt.getTime() / 1000) : null,
        })),
        // Not in docs/api-contract.md today — same field name as GET /limit/recommendation's own
        // response for consistency. Still Member C's placeholder (see limitStub.ts).
        recommended_cap: await getRecommendedCap(deviceId),
        trust_attestations: trustEdges.map(buildAttestationPem),
      });
    },
  );
}

import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { requireSession } from '../auth/requireSession';
import { settleTransactionBatch } from '../../settlement/engine';
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

  fastify.get('/sync/updates', async (_request, reply) => {
    // TODO: return { crl, flags, trust_attestations } for offline caching. "flags" shape is
    // unspecified in kickoff — confirm before implementing.
    reply.code(501).send({ error: 'not implemented' });
  });
}

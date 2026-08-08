import { FastifyInstance } from 'fastify';

// POST /api/v1/sync/transactions and GET /api/v1/sync/updates — see docs/api-contract.md.
export default async function syncRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post('/sync/transactions', async (_request, reply) => {
    // TODO: validate + persist a batch of signed transactions. Per token_id: (1) check
    // device_counter/prev_tx_hash continuity, (2) sum(amount) <= token.value, (3) each
    // amount <= token.cap — see purse-token-format.md#spend-enforcement-decided. Delegate to
    // src/settlement/. Return { results: [{ tx_id, status, reason? }] }.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.get('/sync/updates', async (_request, reply) => {
    // TODO: return { crl, flags, trust_attestations } for offline caching. "flags" shape is
    // unspecified in kickoff — confirm before implementing.
    reply.code(501).send({ error: 'not implemented' });
  });
}

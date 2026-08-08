import { FastifyInstance } from 'fastify';

// GET /api/v1/trust/attestations?subject={id} and GET /api/v1/merchant/{id}/trust — see
// docs/api-contract.md and docs/trust-attestation-format.md.
export default async function trustRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.get('/trust/attestations', async (_request, reply) => {
    // TODO: query param `subject` (device_id). Return { attestations } — signed edges involving
    // that subject, as PEM envelopes.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.get('/merchant/:id/trust', async (_request, reply) => {
    // TODO: "reputation bundle" shape TBD beyond the name (see api-contract.md open question).
    // Return { merchant_id, attestations, summary? }.
    reply.code(501).send({ error: 'not implemented' });
  });
}

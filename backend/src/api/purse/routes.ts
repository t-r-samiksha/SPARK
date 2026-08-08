import { FastifyInstance } from 'fastify';

// POST /api/v1/purse/load, POST /api/v1/purse/topup, GET /api/v1/purse/status, and
// GET /api/v1/limit/recommendation — see docs/api-contract.md and docs/purse-token-format.md.
// `limit/recommendation` isn't nested under `/purse` in the contract, but it's grouped here since
// it's the input to sizing a purse token's cap — a judgment call, flag if trust/ fits better.
export default async function purseRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post('/purse/load', async (_request, reply) => {
    // TODO: issue a new PurseToken for the authenticated device, signed with the Bank
    // operational key, return { purse_token } as a PEM envelope.
    reply.code(501).send({ error: 'not implemented' });
  });

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

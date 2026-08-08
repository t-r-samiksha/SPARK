import { FastifyInstance } from 'fastify';

// POST /api/v1/auth/challenge and POST /api/v1/auth/verify — see docs/api-contract.md.
// Not one of the domains listed in the task (enroll/purse/sync/trust/admin), but auth is its own
// concern in the contract and doesn't fit cleanly into any of those five — kept as a separate
// domain folder rather than bolted onto enroll.
export default async function authRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post('/auth/challenge', async (_request, reply) => {
    // TODO: generate + persist a server-side nonce, return { nonce } as base64url.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.post('/auth/verify', async (_request, reply) => {
    // TODO: look up device's current non-revoked cert by device_id, verify signed_nonce as an
    // Ed25519 signature over the nonce issued by /auth/challenge, issue { session_token }.
    reply.code(501).send({ error: 'not implemented' });
  });
}

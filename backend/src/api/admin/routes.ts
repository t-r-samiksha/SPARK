import { FastifyInstance } from 'fastify';

// GET /api/v1/admin/incidents, POST /api/v1/admin/revoke, POST /api/v1/admin/disaster/toggle —
// see docs/api-contract.md. All admin endpoints; open question in the doc about whether admin
// sessions need a distinct auth scope from device sessions — not addressed by this stub.
export default async function adminRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.get('/admin/incidents', async (_request, reply) => {
    // TODO: query param `type` (double_spend | fraud_flag | all, default all). Return
    // { incidents: [{ type, ... }] } — shape TBD beyond the type discriminator.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.post('/admin/revoke', async (_request, reply) => {
    // TODO: given { serial_number, device_id?, reason }, mark the Device cert revoked, return
    // { serial_number, revoked_at }. Revoked serials must show up in the next /sync/updates CRL.
    reply.code(501).send({ error: 'not implemented' });
  });

  fastify.post('/admin/disaster/toggle', async (_request, reply) => {
    // TODO: given { region, enabled }, toggle service availability for that region, return
    // { region, enabled, updated_at }.
    reply.code(501).send({ error: 'not implemented' });
  });
}

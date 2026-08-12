import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireAdminKey } from './requireAdminKey';
import { getFraudFlags } from './fraudClient';
import { revokeCertificate } from '../../settlement/doubleSpendResolver';

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// GET /api/v1/admin/incidents, POST /api/v1/admin/revoke, POST /api/v1/admin/disaster/toggle —
// see docs/api-contract.md. All admin endpoints; open question in the doc about whether admin
// sessions need a distinct auth scope from device sessions — not addressed here (see
// requireAdminKey.ts for what IS in place: a bare shared-secret header check).
export default async function adminRoutes(fastify: FastifyInstance): Promise<void> {
  // --- GET /admin/incidents -------------------------------------------------------------------
  interface IncidentsQuery {
    type?: 'double_spend' | 'fraud_flag' | 'all';
  }

  const incidentsQuerySchema = {
    querystring: {
      type: 'object',
      additionalProperties: false,
      properties: {
        type: { type: 'string', enum: ['double_spend', 'fraud_flag', 'all'] },
      },
    },
  };

  fastify.get<{ Querystring: IncidentsQuery }>(
    '/admin/incidents',
    { schema: incidentsQuerySchema },
    async (request: FastifyRequest<{ Querystring: IncidentsQuery }>, reply: FastifyReply) => {
      if (!requireAdminKey(request, reply)) {
        return;
      }

      const type = request.query.type ?? 'all';

      // Fraud flags come from Member C's intelligence service (ai/), which scores behaviour and
      // returns incidents. It is a reader with no authority to act — a flag is a prompt for an
      // operator to review, never an automatic revocation.
      //
      // If the service is unreachable we return 503 rather than an empty list: "no fraud found"
      // and "we could not look" are different answers, and the console must not present the
      // second as the first.
      const fraudFlagIncidents = await getFraudFlags();

      if (type === 'fraud_flag') {
        if (fraudFlagIncidents === null) {
          return reply
            .code(503)
            .send({ error: 'fraud intelligence service unavailable' });
        }
        return reply.code(200).send({ incidents: fraudFlagIncidents });
      }

      const rows = await prisma.doubleSpendIncident.findMany({ orderBy: { detectedAt: 'desc' } });
      const doubleSpendIncidents = rows.map((row) => ({
        type: 'double_spend' as const,
        id: row.id,
        token_id: row.tokenId,
        device_id: row.deviceId,
        tx_id_a: row.txIdA,
        tx_id_b: row.txIdB,
        detected_at: Math.floor(row.detectedAt.getTime() / 1000),
      }));

      // For type=all a fraud outage degrades rather than fails: double-spend incidents are real and
      // must still reach the operator. The console's dedicated fraud tab is where the outage is
      // reported honestly, via the 503 above.
      const incidents =
        type === 'double_spend'
          ? doubleSpendIncidents
          : [...doubleSpendIncidents, ...(fraudFlagIncidents ?? [])];
      return reply.code(200).send({ incidents });
    },
  );

  // --- POST /admin/revoke ----------------------------------------------------------------------
  // RESOLVED (was inconsistent with docs/api-contract.md's serial_number-keyed shape): request is
  // {device_id, reason} only — no client-supplied serial_number. An admin revoking a lost/stolen
  // device has the device_id (from a support ticket, account lookup, etc.), not necessarily its
  // current cert serial; the route resolves that internally via the Device row. See the
  // docs/api-contract.md update alongside this change.
  interface AdminRevokeBody {
    device_id: string;
    reason: string;
  }

  const adminRevokeSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['device_id', 'reason'],
      properties: {
        device_id: { type: 'string' },
        reason: { type: 'string', minLength: 1 },
      },
    },
  };

  fastify.post<{ Body: AdminRevokeBody }>(
    '/admin/revoke',
    { schema: adminRevokeSchema },
    async (request: FastifyRequest<{ Body: AdminRevokeBody }>, reply: FastifyReply) => {
      if (!requireAdminKey(request, reply)) {
        return;
      }

      const { device_id, reason } = request.body;
      if (!UUID_V4_PATTERN.test(device_id)) {
        return reply.code(400).send({ error: 'device_id must be a canonical (lowercase) UUID v4' });
      }

      const device = await prisma.device.findUnique({ where: { deviceId: device_id } });
      if (!device) {
        return reply.code(404).send({ error: 'device not found' });
      }

      // Reuses the same helper the double-spend flow uses (src/settlement/doubleSpendResolver.ts)
      // so RevokedCertificate and Device.revokedAt/revokedReason stay in sync via one code path,
      // not two that could drift.
      await revokeCertificate({ certSerial: device.serialNumber, deviceId: device_id, reason });

      return reply.code(200).send({
        device_id,
        serial_number: device.serialNumber,
        reason,
        revoked_at: Math.floor(Date.now() / 1000),
      });
    },
  );

  // --- POST /admin/disaster/toggle --------------------------------------------------------------
  // RESOLVED (was {region, enabled}, stale relative to the DisasterEvent model — see
  // docs/api-contract.md's update from the previous session): {region_geo, type, enabled,
  // higher_cap?, essential_only?}. Toggle-off identifies which event to end by region_geo (the
  // active DisasterEvent for that region), not a client-supplied event id.
  interface DisasterToggleBody {
    region_geo: string;
    type: string;
    enabled: boolean;
    higher_cap?: string | null;
    essential_only?: boolean;
  }

  const disasterToggleSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['region_geo', 'type', 'enabled'],
      properties: {
        region_geo: { type: 'string' },
        type: { type: 'string' },
        enabled: { type: 'boolean' },
        higher_cap: { type: ['string', 'null'], pattern: '^[0-9]+$' },
        essential_only: { type: 'boolean' },
      },
    },
  };

  function serializeDisasterEvent(event: {
    id: string;
    regionGeo: string;
    type: string;
    active: boolean;
    higherCap: string | null;
    essentialOnly: boolean;
  }) {
    return {
      id: event.id,
      region_geo: event.regionGeo,
      type: event.type,
      enabled: event.active,
      higher_cap: event.higherCap,
      essential_only: event.essentialOnly,
      // Not a column on DisasterEvent (only started_at/ended_at are) — this is "when this toggle
      // request was processed," which is the only sensible meaning available for a field named
      // updated_at when there's no generic last-modified column to read back.
      updated_at: Math.floor(Date.now() / 1000),
    };
  }

  fastify.post<{ Body: DisasterToggleBody }>(
    '/admin/disaster/toggle',
    { schema: disasterToggleSchema },
    async (request: FastifyRequest<{ Body: DisasterToggleBody }>, reply: FastifyReply) => {
      if (!requireAdminKey(request, reply)) {
        return;
      }

      const { region_geo, type, enabled, higher_cap, essential_only } = request.body;
      const activeEvent = await prisma.disasterEvent.findFirst({ where: { regionGeo: region_geo, active: true } });

      if (enabled) {
        // JUDGMENT CALL: updating an already-active event fully overwrites type/higher_cap/
        // essential_only with what's in this request (omitted optional fields reset to their
        // defaults: higher_cap -> null, essential_only -> false) rather than leaving unspecified
        // fields untouched. The task frames this as a dashboard toggle form, which is expected to
        // submit the full current state each time — a partial-patch semantics would need a
        // different (documented) contract if that assumption turns out wrong.
        const event = activeEvent
          ? await prisma.disasterEvent.update({
              where: { id: activeEvent.id },
              data: {
                type,
                higherCap: higher_cap ?? null,
                essentialOnly: essential_only ?? false,
              },
            })
          : await prisma.disasterEvent.create({
              data: {
                regionGeo: region_geo,
                type,
                active: true,
                higherCap: higher_cap ?? null,
                essentialOnly: essential_only ?? false,
              },
            });
        return reply.code(200).send(serializeDisasterEvent(event));
      }

      // enabled === false: end the active event for this region, if any.
      if (!activeEvent) {
        return reply.code(404).send({ error: `no active disaster event for region_geo ${region_geo}` });
      }
      const event = await prisma.disasterEvent.update({
        where: { id: activeEvent.id },
        data: { active: false, endedAt: new Date() },
      });
      return reply.code(200).send(serializeDisasterEvent(event));
    },
  );
}

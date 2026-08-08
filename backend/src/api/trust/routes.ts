import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireSession } from '../auth/requireSession';
import { buildAttestationPem } from './buildAttestationPem';

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// GET /api/v1/trust/attestations?subject={id} and GET /api/v1/merchant/{id}/trust — see
// docs/api-contract.md and docs/trust-attestation-format.md.
export default async function trustRoutes(fastify: FastifyInstance): Promise<void> {
  // --- GET /trust/attestations -----------------------------------------------------------------
  interface AttestationsQuery {
    subject?: string;
  }

  const attestationsQuerySchema = {
    querystring: {
      type: 'object',
      additionalProperties: false,
      required: ['subject'],
      properties: {
        subject: { type: 'string' },
      },
    },
  };

  fastify.get<{ Querystring: AttestationsQuery }>(
    '/trust/attestations',
    { schema: attestationsQuerySchema },
    async (request: FastifyRequest<{ Querystring: AttestationsQuery }>, reply: FastifyReply) => {
      if (!requireSession(request, reply)) {
        return;
      }

      const subject = request.query.subject!;
      if (!UUID_V4_PATTERN.test(subject)) {
        return reply.code(400).send({ error: 'subject must be a canonical (lowercase) UUID v4' });
      }

      // subject is a device_id — trust edges are device-keyed (docs/trust-attestation-format.md:
      // "subject_a: device_id of the first party"). Symmetric edges (see the JUDGMENT CALL on the
      // TrustAttestation model) mean `subject` can appear in either column depending on
      // lexicographic order relative to its counterpart.
      const edges = await prisma.trustAttestation.findMany({
        where: { OR: [{ subjectA: subject }, { subjectB: subject }] },
      });

      return reply.code(200).send({ attestations: edges.map(buildAttestationPem) });
    },
  );

  // --- GET /merchant/:id/trust -----------------------------------------------------------------
  // JUDGMENT CALL: `id` is an account_id, not a device_id — "treat any account as a potential
  // merchant" per the task. Trust edges themselves are device-keyed (see above), so this looks up
  // every device belonging to the account and aggregates edges touching any of them. An account
  // with no devices (or that doesn't exist at all) gets a zeroed bundle, not a 404 — "no
  // reputation yet" is a legitimate answer for a fresh/unknown account at this stage, not an error.
  fastify.get<{ Params: { id: string } }>(
    '/merchant/:id/trust',
    async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
      if (!requireSession(request, reply)) {
        return;
      }

      const merchantId = request.params.id;
      if (!UUID_V4_PATTERN.test(merchantId)) {
        return reply.code(400).send({ error: 'id must be a canonical (lowercase) UUID v4' });
      }

      const devices = await prisma.device.findMany({ where: { accountId: merchantId } });
      const deviceIds = devices.map((d) => d.deviceId);

      const edges =
        deviceIds.length === 0
          ? []
          : await prisma.trustAttestation.findMany({
              where: { OR: [{ subjectA: { in: deviceIds } }, { subjectB: { in: deviceIds } }] },
            });

      const settledCount = edges.reduce((sum, e) => sum + e.settlementCount, 0);
      const settledValue = edges.reduce((sum, e) => sum + BigInt(e.settledAmount), 0n).toString();
      const lastSettledAt = edges.reduce<Date | null>((latest, e) => {
        return !latest || e.lastSettledAt > latest ? e.lastSettledAt : latest;
      }, null);

      return reply.code(200).send({
        merchant_id: merchantId,
        attestations: edges.map(buildAttestationPem),
        summary: {
          settled_count: settledCount,
          settled_value: settledValue,
          last_settled_at: lastSettledAt ? Math.floor(lastSettledAt.getTime() / 1000) : null,
          // TODO: real merchant-specific scoring belongs to Member C — we're just serving
          // storage. Left null/0 rather than fabricating a number.
          dispute_rate: null,
          delivery_score: null,
          offline_reliability: null,
        },
      });
    },
  );
}

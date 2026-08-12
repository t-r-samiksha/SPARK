import Fastify, { type FastifyInstance } from 'fastify';
import { recommendCap, recommendDefaultCap } from './capModel.ts';
import { scanForFraud } from './fraud.ts';
import { computeTrust } from './trustGraph.ts';

/**
 * SPARK intelligence service (Member C).
 *
 * Runs as its own process, exactly as backend/src/api/purse/limitStub.ts anticipated: "replace
 * this with a real call to Member C's /limit/recommendation once available". The bank server
 * calls in; this service never calls out, and never writes to the bank's database.
 */
export function buildServer(): FastifyInstance {
  const app = Fastify({ logger: true });

  app.get('/health', async () => ({ status: 'ok', service: 'spark-ai' }));

  /**
   * Recommended offline spend cap. `device_id` is optional: with one, the answer is that
   * device's adaptive cap; without, it is the fleet-median network default, which is what the
   * bank's own GET /api/v1/limit/recommendation needs when asked without a device context.
   */
  app.get<{ Querystring: { device_id?: string } }>('/limit/recommendation', async (request, reply) => {
    const deviceId = request.query.device_id;
    try {
      const result = deviceId ? await recommendCap(deviceId) : await recommendDefaultCap();
      return result;
    } catch (error) {
      const status = (error as { statusCode?: number }).statusCode ?? 500;
      if (status === 404) {
        return reply.code(404).send({ error: `Unknown device ${deviceId}` });
      }
      request.log.error(error);
      return reply.code(500).send({ error: 'Cap model failed' });
    }
  });

  /** Fraud intelligence — incidents in the shape GET /admin/incidents?type=fraud_flag serves. */
  app.get('/fraud/flags', async (_request, reply) => {
    try {
      return { incidents: await scanForFraud() };
    } catch (error) {
      reply.log.error(error);
      return reply.code(500).send({ error: 'Fraud scan failed' });
    }
  });

  /** Trust-graph view for a device — bounded ≤3-hop decayed traversal. */
  app.get<{ Params: { deviceId: string } }>('/trust/:deviceId', async (request) => {
    const trust = await computeTrust(request.params.deviceId);
    return {
      device_id: request.params.deviceId,
      weight: trust.weight,
      direct_counterparties: trust.directCounterparties,
      reached_by_hop: trust.reachedByHop,
      direct_settled_paise: trust.directSettledPaise.toString(),
    };
  });

  return app;
}

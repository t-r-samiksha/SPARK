import Fastify, { FastifyInstance } from 'fastify';
import enrollRoutes from './api/enroll/routes';
import authRoutes from './api/auth/routes';
import purseRoutes from './api/purse/routes';
import syncRoutes from './api/sync/routes';
import trustRoutes from './api/trust/routes';
import adminRoutes from './api/admin/routes';
import escrowRoutes from './api/escrow/routes';
import familyRoutes from './api/family/routes';

export function buildServer(): FastifyInstance {
  // Fastify's default AJV config sets `removeAdditional: true`, which *silently strips* fields
  // not listed in a route schema instead of rejecting the request. That's looser than what
  // docs/api-contract.md specifies (`additionalProperties: false` on every request body) —
  // override it so an unexpected field is a hard 400, matching the contract.
  const app = Fastify({
    logger: true,
    ajv: { customOptions: { removeAdditional: false } },
  });

  app.get('/health', async () => ({ status: 'ok' }));

  // All route files declare paths exactly as they appear in docs/api-contract.md (e.g.
  // `/limit/recommendation`, not nested under `/purse`), so every plugin shares the same
  // `/api/v1` prefix rather than a per-domain one.
  app.register(enrollRoutes, { prefix: '/api/v1' });
  app.register(authRoutes, { prefix: '/api/v1' });
  app.register(purseRoutes, { prefix: '/api/v1' });
  app.register(syncRoutes, { prefix: '/api/v1' });
  app.register(trustRoutes, { prefix: '/api/v1' });
  app.register(adminRoutes, { prefix: '/api/v1' });
  app.register(escrowRoutes, { prefix: '/api/v1' });
  app.register(familyRoutes, { prefix: '/api/v1' });

  return app;
}

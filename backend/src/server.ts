import Fastify, { FastifyInstance } from 'fastify';
import rateLimit from '@fastify/rate-limit';
import enrollRoutes from './api/enroll/routes';
import authRoutes from './api/auth/routes';
import purseRoutes from './api/purse/routes';
import syncRoutes from './api/sync/routes';
import trustRoutes from './api/trust/routes';
import adminRoutes from './api/admin/routes';
import escrowRoutes from './api/escrow/routes';
import familyRoutes from './api/family/routes';

export interface BuildServerOptions {
  /**
   * Forces the rate limiter on even when NODE_ENV === 'test' — see the comment on the
   * @fastify/rate-limit registration below for why tests are exempt by default. Only
   * tests/api/rateLimit/routes.test.ts, which exists specifically to exercise the real limit,
   * should need this.
   */
  forceRateLimit?: boolean;
}

export function buildServer(options: BuildServerOptions = {}): FastifyInstance {
  // Fastify's default AJV config sets `removeAdditional: true`, which *silently strips* fields
  // not listed in a route schema instead of rejecting the request. That's looser than what
  // docs/api-contract.md specifies (`additionalProperties: false` on every request body) —
  // override it so an unexpected field is a hard 400, matching the contract.
  const app = Fastify({
    logger: true,
    ajv: { customOptions: { removeAdditional: false } },
  });

  // HACKATHON-SCOPE, NOT PRODUCTION-GRADE: a flat, in-memory, per-process, per-IP limiter. It's
  // defense against accidental hammering during testing/demo (e.g. a buggy client retry loop
  // pointed at this server), not a real anti-abuse system — no distributed store (a second
  // instance behind a load balancer would track its own separate counts), no per-route tuning,
  // and trivially bypassed by rotating source IP. A production deployment would want a shared
  // store (Redis, via the plugin's `store` option), tighter per-endpoint limits on
  // sensitive/expensive routes (auth, sync/transactions), and real abuse detection — out of scope
  // here.
  //
  // Registered before every route so the global default (`global: true` is this plugin's
  // default) applies everywhere except where a route explicitly opts out via
  // `config: { rateLimit: false }` — see /health below.
  //
  // Disabled by default when NODE_ENV === 'test' (set automatically by Jest — verified: a plain
  // `npx jest` run has it set with no config needed). Most existing test files build ONE app
  // instance in `beforeAll` and then fire many requests against it across all their `it()` blocks
  // — all within the same 1-minute window this limiter tracks. At the real 100/min default,
  // several of the larger suites (e.g. tests/api/purse/load.test.ts's 14 tests, each making
  // several requests) would trip it well before finishing, failing for a reason unrelated to what
  // they're actually testing. tests/api/rateLimit/routes.test.ts passes `forceRateLimit: true` to
  // build a dedicated app instance with the real limiter active, so the feature itself is still
  // verified end-to-end against its real default rather than a stand-in number.
  if (options.forceRateLimit || process.env.NODE_ENV !== 'test') {
    app.register(rateLimit, { max: 100, timeWindow: '1 minute' });
  }

  // Exempted from the global rate limit: a monitoring/uptime check hitting this on a schedule
  // shouldn't be able to trip the same counter real API traffic shares, and it does nothing
  // expensive worth guarding.
  app.get('/health', { config: { rateLimit: false } }, async () => ({ status: 'ok' }));

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

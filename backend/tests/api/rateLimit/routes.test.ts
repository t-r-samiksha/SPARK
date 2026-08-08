import { randomUUID } from 'crypto';
import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';

// No jest.mock('../../../src/db/client') here on purpose: POST /api/v1/auth/challenge (the route
// this file bursts against) never touches Prisma — see the comment on that handler in
// src/api/auth/routes.ts ("Deliberately not checking enrollment here"). That makes it the
// cheapest real, registered, DB-independent route to fire many requests at.
//
// Every test in this file builds its OWN app via buildServer({ forceRateLimit: true }) so each
// gets a fresh, empty rate-limit store — a shared instance would let one test's request count
// bleed into the next. forceRateLimit is needed because src/server.ts disables the limiter by
// default under NODE_ENV==='test' (which Jest sets automatically) — see that file's comment for
// why: most other test files build one long-lived app instance and fire far more than 100
// requests against it across all their `it()` blocks, which would otherwise trip a real 100/min
// limiter well before the file finishes, for a reason unrelated to what they're testing.

function challenge(app: FastifyInstance) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/auth/challenge',
    payload: { device_id: randomUUID() },
  });
}

describe('global rate limiting', () => {
  let app: FastifyInstance;

  afterEach(async () => {
    await app.close();
  });

  it('allows requests under the limit through', async () => {
    app = buildServer({ forceRateLimit: true });
    await app.ready();

    const response = await challenge(app);
    expect(response.statusCode).toBe(200);
    // @fastify/rate-limit's default headers — confirms the limiter is actually wired up and not
    // silently a no-op, rather than just inferring that from a 200 status alone.
    expect(response.headers['x-ratelimit-limit']).toBe('100');
    expect(Number(response.headers['x-ratelimit-remaining'])).toBeLessThan(100);
  });

  it('a burst past the limit gets a 429, with the requests up to the limit succeeding', async () => {
    app = buildServer({ forceRateLimit: true });
    await app.ready();

    const responses = [];
    // 100/minute is the configured default (see src/server.ts) — 101 requests from the same IP
    // (light-my-request's injected requests all report 127.0.0.1) inside one timeWindow should
    // let exactly the first 100 through and reject the 101st.
    for (let i = 0; i < 101; i += 1) {
      responses.push(await challenge(app));
    }

    const statusCodes = responses.map((r) => r.statusCode);
    expect(statusCodes.slice(0, 100).every((code) => code === 200)).toBe(true);
    expect(statusCodes[100]).toBe(429);

    const limited = responses[100].json();
    expect(limited.statusCode).toBe(429);
    expect(limited.error).toMatch(/Too Many Requests/i);
  });

  it('/health is exempt from the limit even after the limit has been exhausted elsewhere', async () => {
    app = buildServer({ forceRateLimit: true });
    await app.ready();

    for (let i = 0; i < 101; i += 1) {
      await challenge(app);
    }
    // The 101st /auth/challenge above already confirmed the limiter is genuinely tripped for that
    // route at this point (see the previous test) — this checks /health specifically isn't caught
    // by the same global registration.
    const healthResponse = await app.inject({ method: 'GET', url: '/health' });
    expect(healthResponse.statusCode).toBe(200);
    expect(healthResponse.json()).toEqual({ status: 'ok' });
  });

  it('rate limiting stays off by default under the test environment (sanity check for the other suites)', async () => {
    app = buildServer(); // no forceRateLimit — exercises the actual default the rest of the suite relies on
    await app.ready();

    const responses = [];
    for (let i = 0; i < 105; i += 1) {
      responses.push(await challenge(app));
    }
    expect(responses.every((r) => r.statusCode === 200)).toBe(true);
  });
});

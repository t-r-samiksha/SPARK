import { FastifyReply, FastifyRequest } from 'fastify';
import { getSession } from './sessionStore';

const BEARER_PREFIX = 'Bearer ';

/** Validates `Authorization: Bearer <session_token>` against the session store (see
 * sessionStore.ts). On success returns the session's device_id. On failure it sends the 401
 * response itself and returns null — callers must `return` immediately when this returns null,
 * without sending any further response. Shared by every endpoint that requires a session per
 * docs/api-contract.md#authentication. */
export function requireSession(request: FastifyRequest, reply: FastifyReply): string | null {
  const header = request.headers.authorization;
  if (!header || !header.startsWith(BEARER_PREFIX)) {
    reply.code(401).send({ error: 'missing or malformed Authorization header' });
    return null;
  }

  const token = header.slice(BEARER_PREFIX.length);
  const session = getSession(token);
  if (!session) {
    reply.code(401).send({ error: 'invalid or expired session token' });
    return null;
  }

  return session.deviceId;
}

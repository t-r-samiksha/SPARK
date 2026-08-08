import { FastifyReply, FastifyRequest } from 'fastify';
import { adminApiKey } from '../../config';

const ADMIN_KEY_HEADER = 'x-admin-key';

// TODO (hackathon simplification): there's no real admin auth system — no separate admin
// accounts, no login flow, no roles/scopes on top of the device session tokens (see the open
// question in docs/api-contract.md#authentication about whether admin sessions need a distinct
// scope). This is a bare shared-secret header check, not authentication or authorization: anyone
// holding the ADMIN_API_KEY value can call every admin endpoint, and there's no per-admin audit
// trail of who made a given change. Replace with real admin accounts + auth before this is
// anywhere near production.

/** Checks `X-Admin-Key` against ADMIN_API_KEY. On failure sends the 401 response itself and
 * returns false — callers must `return` immediately when this returns false. */
export function requireAdminKey(request: FastifyRequest, reply: FastifyReply): boolean {
  const provided = request.headers[ADMIN_KEY_HEADER];
  if (typeof provided !== 'string' || provided !== adminApiKey()) {
    reply.code(401).send({ error: 'missing or invalid X-Admin-Key header' });
    return false;
  }
  return true;
}

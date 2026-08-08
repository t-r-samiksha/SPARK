import { randomBytes } from 'crypto';
import { base64urlEncode } from '../../crypto';

// Session tokens: a random opaque token stored server-side, not a signed JWT.
//
// JUDGMENT CALL: a JWT would need its own signing key, a library, and a claims schema for no
// benefit here — every endpoint that checks `Authorization: Bearer <session_token>` is served by
// this same backend process, so a server-side map lookup (same pattern as the nonce store) is
// simpler, requires no extra dependency, and is trivially revocable (delete the map entry) —
// something a stateless JWT can't do without also maintaining a server-side denylist, at which
// point it's no simpler than this. Revisit only if session verification ever needs to happen
// somewhere that doesn't share this process's memory (e.g. a separate verifier/gateway service).

/** Not specified in docs/api-contract.md — judgment call. */
export const SESSION_TTL_MS = 24 * 60 * 60 * 1000;
const SESSION_TOKEN_LENGTH_BYTES = 32;

export interface SessionRecord {
  deviceId: string;
  expiresAt: number;
}

const sessionStore = new Map<string, SessionRecord>();

export function issueSessionToken(deviceId: string): string {
  const token = base64urlEncode(randomBytes(SESSION_TOKEN_LENGTH_BYTES));
  sessionStore.set(token, { deviceId, expiresAt: Date.now() + SESSION_TTL_MS });
  return token;
}

/** Returns the session's device_id if the token is known and unexpired, else undefined. Not yet
 * wired into any route as auth middleware — scaffolded here for when other endpoints implement
 * their `bearerAuth` requirement from docs/api-contract.md. */
export function getSession(token: string): SessionRecord | undefined {
  const record = sessionStore.get(token);
  if (!record || record.expiresAt < Date.now()) {
    return undefined;
  }
  return record;
}

import { randomBytes } from 'crypto';
import { base64urlEncode } from '../../crypto';

// In-memory, single-process nonce store — fine for a hackathon single-instance backend. Would
// need Redis or a DB table for horizontal scaling or surviving a restart.
//
// Keyed by device_id rather than the nonce value itself: see the design-decision comment in
// routes.ts — docs/api-contract.md's /auth/challenge schema takes no device_id, which would
// force a brute-force signature search across every pending nonce at /verify time. We deviate
// from that literal schema and require device_id at /auth/challenge instead, for a direct O(1)
// lookup at /verify.

/** Not specified in docs/api-contract.md — judgment call. */
export const NONCE_TTL_MS = 2 * 60 * 1000;
const NONCE_LENGTH_BYTES = 32;

export interface NonceRecord {
  nonce: string;
  expiresAt: number;
  used: boolean;
}

const nonceStore = new Map<string, NonceRecord>();

/** Issues a fresh nonce for a device, overwriting (invalidating) any previous pending nonce for
 * that device — only the most recently issued challenge is redeemable. */
export function issueNonce(deviceId: string): string {
  const nonce = base64urlEncode(randomBytes(NONCE_LENGTH_BYTES));
  nonceStore.set(deviceId, { nonce, expiresAt: Date.now() + NONCE_TTL_MS, used: false });
  return nonce;
}

export function getNonceRecord(deviceId: string): NonceRecord | undefined {
  return nonceStore.get(deviceId);
}

export function markNonceUsed(deviceId: string): void {
  const record = nonceStore.get(deviceId);
  if (record) {
    record.used = true;
  }
}

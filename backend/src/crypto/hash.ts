import { createHash } from 'crypto';
import { base64urlEncode } from './base64';

/** SHA-256 of raw bytes, returned as a Buffer. */
export function sha256(data: Buffer): Buffer {
  return createHash('sha256').update(data).digest();
}

/** SHA-256 of raw bytes, base64url-encoded (unpadded) — the form used in JSON fields like
 * `prev_tx_hash` per docs/crypto.md#hashing--sha-256. */
export function sha256Base64url(data: Buffer): string {
  return base64urlEncode(sha256(data));
}

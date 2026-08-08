import { createCipheriv, createDecipheriv, randomBytes } from 'crypto';

// AES-256-GCM per docs/crypto.md#symmetric-encryption--aes-256-gcm.
//
// JUDGMENT CALL (flagged as undecided in the doc): the doc says nonce/tag placement relative to
// ciphertext is "implementation-dependent... pick one convention when this is implemented and
// document it here." This module concatenates as `nonce (12 bytes) || ciphertext || tag (16
// bytes)` into a single Buffer, so callers only need to persist/transmit one blob. If another
// component (Android) picks a different layout, this needs to be reconciled — flag in review.
//
// AAD is also undecided in the doc ("not yet decided — flag if a use case needs to bind
// ciphertext to context"). Exposed as an optional parameter here so callers can start binding
// context (e.g. device_id) without a signature-shape change later; omit it if unused.

const KEY_LENGTH = 32;
const NONCE_LENGTH = 12;
const TAG_LENGTH = 16;

export function aesGcmEncrypt(key: Buffer, plaintext: Buffer, aad?: Buffer): Buffer {
  if (key.length !== KEY_LENGTH) {
    throw new Error(`AES-256-GCM key must be ${KEY_LENGTH} bytes, got ${key.length}`);
  }
  const nonce = randomBytes(NONCE_LENGTH);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  if (aad) {
    cipher.setAAD(aad);
  }
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([nonce, ciphertext, tag]);
}

export function aesGcmDecrypt(key: Buffer, blob: Buffer, aad?: Buffer): Buffer {
  if (key.length !== KEY_LENGTH) {
    throw new Error(`AES-256-GCM key must be ${KEY_LENGTH} bytes, got ${key.length}`);
  }
  if (blob.length < NONCE_LENGTH + TAG_LENGTH) {
    throw new Error('ciphertext blob too short to contain nonce and tag');
  }
  const nonce = blob.subarray(0, NONCE_LENGTH);
  const tag = blob.subarray(blob.length - TAG_LENGTH);
  const ciphertext = blob.subarray(NONCE_LENGTH, blob.length - TAG_LENGTH);
  const decipher = createDecipheriv('aes-256-gcm', key, nonce);
  if (aad) {
    decipher.setAAD(aad);
  }
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

export function generateAesKey(): Buffer {
  return randomBytes(KEY_LENGTH);
}

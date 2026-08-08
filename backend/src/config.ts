// Reads required Bank signing key seeds from the environment. Kept as functions (not top-level
// constants) rather than read once at import time, so tests can set process.env before calling
// and so a missing var fails at the point of use with a clear message instead of silently.
//
// Per the confirmed key-separation decision (docs/crypto.md open question, resolved): the Bank
// Root CA key and the Bank operational signing key are separate Ed25519 keypairs.
//   - BANK_ROOT_CA_KEY_SEED signs device certificates only.
//   - BANK_SIGNING_KEY_SEED signs purse tokens and trust attestations only.
// TODO: both are file/env-based for hackathon speed; docs/crypto.md#key-management flags this as
// needing KMS/HSM-backed storage before anything resembling production.

import { publicKeyFromSeed } from './crypto';

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`missing required environment variable: ${name}`);
  }
  return value;
}

export function bankRootCaKeySeed(): string {
  return requireEnv('BANK_ROOT_CA_KEY_SEED');
}

export function bankSigningKeySeed(): string {
  return requireEnv('BANK_SIGNING_KEY_SEED');
}

/** Derived from the seed on every call rather than cached — cheap, and avoids a stale value if
 * the env var were ever rotated within a process lifetime (not expected, but free to get right). */
export function bankRootCaPublicKey(): string {
  return publicKeyFromSeed(bankRootCaKeySeed());
}

/** Same rationale as bankRootCaPublicKey() above, for the operational key (purse tokens, trust
 * attestations). */
export function bankSigningPublicKey(): string {
  return publicKeyFromSeed(bankSigningKeySeed());
}

/** Shared secret for the admin endpoints (X-Admin-Key header) — see requireAdminKey.ts. A bare
 * shared secret, not a real admin auth system; see that file for why. */
export function adminApiKey(): string {
  return requireEnv('ADMIN_API_KEY');
}

import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify, KeyObject } from 'crypto';
import { base64urlDecode, base64urlEncode } from './base64';

// Ed25519 (PureEdDSA, RFC 8032) per docs/crypto.md#signing--ed25519-pureeddsa. Node's `crypto`
// signs/verifies Ed25519 natively (no prehash — pass `null` as the algorithm to crypto.sign,
// exactly what PureEdDSA requires), but it doesn't accept raw 32-byte seeds/public keys directly
// via `createPrivateKey`/`createPublicKey` for the 'der' format — only full PKCS8/SPKI DER
// structures. Ed25519's DER wrapping is fixed-size and fixed-content (RFC 8410), so we prepend
// the constant prefix to go from raw <-> DER. Verified against the docs/canonical-serialization.md
// test vector in tests/crypto/testVector.test.ts.

const PKCS8_PREFIX = Buffer.from('302e020100300506032b657004220420', 'hex');
const SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

const SEED_LENGTH = 32;
const PUBLIC_KEY_LENGTH = 32;
const SIGNATURE_LENGTH = 64;

export interface Ed25519KeyPair {
  /** 32-byte private seed, base64url unpadded — see docs/id-conventions.md#public-key-encoding. */
  privateSeed: string;
  /** 32-byte public key, base64url unpadded. */
  publicKey: string;
}

function privateKeyFromSeed(seed: Buffer): KeyObject {
  if (seed.length !== SEED_LENGTH) {
    throw new Error(`Ed25519 seed must be ${SEED_LENGTH} bytes, got ${seed.length}`);
  }
  return createPrivateKey({
    key: Buffer.concat([PKCS8_PREFIX, seed]),
    format: 'der',
    type: 'pkcs8',
  });
}

function publicKeyFromRaw(raw: Buffer): KeyObject {
  if (raw.length !== PUBLIC_KEY_LENGTH) {
    throw new Error(`Ed25519 public key must be ${PUBLIC_KEY_LENGTH} bytes, got ${raw.length}`);
  }
  return createPublicKey({
    key: Buffer.concat([SPKI_PREFIX, raw]),
    format: 'der',
    type: 'spki',
  });
}

function rawPublicKeyOf(key: KeyObject): Buffer {
  const spki = key.export({ format: 'der', type: 'spki' });
  return spki.subarray(spki.length - PUBLIC_KEY_LENGTH);
}

/** Generate a new Ed25519 keypair using Node's CSPRNG-backed generator. */
export function generateEd25519KeyPair(): Ed25519KeyPair {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const pkcs8 = privateKey.export({ format: 'der', type: 'pkcs8' });
  const seed = pkcs8.subarray(pkcs8.length - SEED_LENGTH);
  const rawPub = rawPublicKeyOf(publicKey);
  return {
    privateSeed: base64urlEncode(Buffer.from(seed)),
    publicKey: base64urlEncode(rawPub),
  };
}

/** Derive the base64url public key from a base64url private seed. */
export function publicKeyFromSeed(privateSeed: string): string {
  const key = privateKeyFromSeed(base64urlDecode(privateSeed));
  return base64urlEncode(rawPublicKeyOf(createPublicKey(key)));
}

/** Sign `message` bytes with a base64url-encoded 32-byte seed. Returns a base64url signature. */
export function ed25519Sign(privateSeed: string, message: Buffer): string {
  const key = privateKeyFromSeed(base64urlDecode(privateSeed));
  const signature = sign(null, message, key);
  return base64urlEncode(signature);
}

/** Verify a base64url signature over `message` bytes with a base64url-encoded 32-byte public key. */
export function ed25519Verify(publicKey: string, message: Buffer, signature: string): boolean {
  const sigBytes = base64urlDecode(signature);
  if (sigBytes.length !== SIGNATURE_LENGTH) {
    return false;
  }
  const key = publicKeyFromRaw(base64urlDecode(publicKey));
  return verify(null, message, key, sigBytes);
}

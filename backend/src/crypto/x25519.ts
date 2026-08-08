import { createPrivateKey, createPublicKey, diffieHellman, generateKeyPairSync, KeyObject } from 'crypto';
import { base64urlDecode, base64urlEncode } from './base64';

// X25519 (RFC 7748) key agreement per docs/crypto.md#key-agreement--x25519. Same raw-key-via-DER
// approach as ed25519.ts, with X25519's own fixed PKCS8/SPKI prefix (OID 1.3.101.110).
// docs/crypto.md flags the exact protocol flow (HKDF vs. raw use of the shared secret) as an open
// question — this module exposes the raw ECDH shared secret only; deriving a symmetric key from
// it (e.g. via HKDF) is left to the caller once that's decided.

const PKCS8_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex');
const SPKI_PREFIX = Buffer.from('302a300506032b656e032100', 'hex');

const KEY_LENGTH = 32;

export interface X25519KeyPair {
  privateKey: string;
  publicKey: string;
}

function privateKeyFromRaw(raw: Buffer): KeyObject {
  if (raw.length !== KEY_LENGTH) {
    throw new Error(`X25519 private key must be ${KEY_LENGTH} bytes, got ${raw.length}`);
  }
  return createPrivateKey({
    key: Buffer.concat([PKCS8_PREFIX, raw]),
    format: 'der',
    type: 'pkcs8',
  });
}

function publicKeyFromRaw(raw: Buffer): KeyObject {
  if (raw.length !== KEY_LENGTH) {
    throw new Error(`X25519 public key must be ${KEY_LENGTH} bytes, got ${raw.length}`);
  }
  return createPublicKey({
    key: Buffer.concat([SPKI_PREFIX, raw]),
    format: 'der',
    type: 'spki',
  });
}

export function generateX25519KeyPair(): X25519KeyPair {
  const { privateKey, publicKey } = generateKeyPairSync('x25519');
  const pkcs8 = privateKey.export({ format: 'der', type: 'pkcs8' });
  const spki = publicKey.export({ format: 'der', type: 'spki' });
  return {
    privateKey: base64urlEncode(Buffer.from(pkcs8.subarray(pkcs8.length - KEY_LENGTH))),
    publicKey: base64urlEncode(Buffer.from(spki.subarray(spki.length - KEY_LENGTH))),
  };
}

/** Compute the raw 32-byte X25519 shared secret from our private key and their public key. */
export function x25519SharedSecret(privateKey: string, theirPublicKey: string): Buffer {
  const priv = privateKeyFromRaw(base64urlDecode(privateKey));
  const pub = publicKeyFromRaw(base64urlDecode(theirPublicKey));
  return diffieHellman({ privateKey: priv, publicKey: pub });
}

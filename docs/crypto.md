# Cryptography

The exact algorithms, key sizes, and encodings used across SPARK. Every other contract doc that
mentions "signed," "encrypted," or "hashed" refers back to this file rather than restating
choices — if you need a different primitive than what's listed here, that's a docs PR, not a
local decision in one component.

**Consumed by:** Android, backend — both implement every primitive below. Dashboard only verifies
signatures (never signs/decrypts device-side data), so it needs the signing/hashing algorithms
but not necessarily the AES/X25519 stack unless it handles admin-side secrets directly.

## Summary (from kickoff)

| Purpose | Algorithm | Key size | Notes |
|---|---|---|---|
| Signing | **Ed25519** (PureEdDSA) | 32-byte keys | All signed objects: transactions, certificates, purse tokens, trust attestations |
| Key agreement (ECDH) | **X25519** (Montgomery form) | 32-byte keys | For any encrypted-channel/key-wrapping use cases |
| Symmetric encryption | **AES-256-GCM** | 32-byte key, 12-byte nonce, 16-byte tag | |
| Hashing | **SHA-256** | — | `prev_tx_hash`, content-addressing, general integrity checks |
| Field encoding (keys/sigs/hashes inside JSON) | base64url, unpadded | — | See [Encoding](#encoding-decided) below |
| Envelope encoding (certs/tokens/attestations) | standard PEM base64 (RFC 7468), padded | — | See [Encoding](#encoding-decided) below |

## Signing — Ed25519 (PureEdDSA)

- Algorithm: **Ed25519**, pure EdDSA (RFC 8032) — i.e. sign the message directly, **not**
  `Ed25519ph` (prehashed) and **not** `Ed25519ctx`. Do not hash the payload yourself before
  signing; Ed25519 does that internally.
- Private key: 32-byte seed.
- Public key: 32 bytes.
- Signature: 64 bytes.
- What gets signed is always the **canonical serialization** of the payload (see
  [canonical-serialization.md](canonical-serialization.md)), not raw JSON.stringify output from
  whatever language produced it.
- Used for: transaction signatures (payer's device key), device certificates (Bank Root CA key),
  purse tokens (Bank key), trust attestations (Bank key).

> **Open question:** kickoff notes say certs are "signed by Bank Root CA" while purse tokens and
> trust attestations are just "signed by Bank." Confirm whether these are the *same* Ed25519
> keypair or intentionally separate keys (recommended: keep the Root CA key offline/cold and use
> a separate operational signing key for purse tokens/attestations, so a compromised operational
> key can't mint new device certs). Whatever's decided, record the key IDs here.

## Key agreement — X25519

- Algorithm: **X25519** (Curve25519 in Montgomery form), RFC 7748.
- Keys: 32 bytes (private and public).
- Kickoff locked in the algorithm and key size; the specific protocol flow that uses it
  (e.g. deriving a shared secret to wrap a symmetric key for an encrypted channel or at-rest
  blob) wasn't detailed yet — **open question for next sync**: where exactly in the flow does
  ECDH happen, and what's derived from the shared secret (raw use vs. HKDF)?

## Symmetric encryption — AES-256-GCM

- Algorithm: **AES-256-GCM**.
- Key: 32 bytes (256 bits).
- Nonce/IV: **12 bytes**, must be unique per (key, message) — never reuse a nonce with the same
  key. Recommended: random 12 bytes per encryption when the key is short-lived, or a counter when
  the key is long-lived and a duplicate-nonce guarantee can be made.
- Auth tag: **16 bytes**, appended (implementation-dependent whether concatenated to ciphertext
  or returned separately — pick one convention when this is implemented and document it here).
- AAD (additional authenticated data): not yet decided — flag if a use case needs to bind
  ciphertext to context (e.g. device_id) without encrypting it.

## Hashing — SHA-256

- Algorithm: **SHA-256** everywhere a hash is needed (e.g. `transaction.prev_tx_hash`, the
  offline hash-chain that lets a device or the backend detect reordered/forked/replayed
  transactions in a device's local ledger).
- Output: 32 bytes, base64url-encoded when embedded in JSON (consistent with keys/signatures —
  see below).

## Encoding (decided)

This is the **single source of truth** for which base64 variant applies where. Every other doc
in `docs/` that mentions encoding a key, signature, hash, or PEM container links back to this
section rather than restating the rule — if you find a restatement elsewhere, that's a docs bug.

Two variants are used, deliberately, at two different layers:

1. **Inside JSON fields — base64url, unpadded.** Every public key, signature, and hash that
   appears as a *value in a JSON object* (`device_public_key`, `signature`, `prev_tx_hash`, etc.)
   is encoded as **base64url** ([RFC 4648 §5](https://www.rfc-editor.org/rfc/rfc4648#section-5):
   `-`/`_` alphabet), **without padding** (no trailing `=`).
2. **Around PEM envelopes — standard PEM base64, padded.** The *outer envelope* wrapping a full
   certificate, purse token, or trust attestation object (see each format's doc) uses **standard
   PEM encoding per [RFC 7468](https://www.rfc-editor.org/rfc/rfc7468)** — standard base64
   alphabet (`+`/`/`), padded, wrapped at 64 characters per line, between `-----BEGIN ...-----`
   and `-----END ...-----` markers.

These never mix: a field's base64url value is never itself re-wrapped in PEM, and a PEM envelope's
body is never base64url. To parse a cert/token/attestation: unwrap the PEM (standard base64
decode) first, *then* parse the resulting JSON and decode its individual fields as base64url.
See [canonical-serialization.md](canonical-serialization.md#pem-envelopes-vs-field-encoding) for
where this fits in the sign/verify pipeline.

## Key management (open question)

Not covered in kickoff — needs a decision before implementation starts:
- Where device Ed25519/X25519 keys are generated/stored on Android (expectation: Android
  Keystore, hardware-backed where available).
- Where the Bank's signing key(s) live on the backend (expectation: KMS/HSM, not a file on disk).
- Key rotation policy for the Bank Root CA and any operational signing keys.

## Random number generation

Not covered in kickoff. Both platforms must use a CSPRNG — `SecureRandom` (Android) /
`crypto.randomBytes` (Node) — for nonces, key generation, and anything else requiring randomness.
Never use a non-cryptographic PRNG (e.g. `Math.random()`) for cryptographic material.

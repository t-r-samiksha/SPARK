# Canonical Serialization

The exact, deterministic byte sequence that gets **signed** (and, symmetrically, that a verifier
must reconstruct before checking a signature) for every signed object in SPARK: transactions,
device certificates, purse tokens, and trust attestations. All components must produce
byte-identical output for the same logical object, or signatures will not verify across
implementations — this is the single most important cross-team contract in the repo.

**Consumed by:** Android (signs transactions, verifies certs/tokens/attestations) and backend
(signs certs/tokens/attestations, verifies transactions) — both must implement this identically.
Dashboard only needs the verification side.

## Why canonical serialization

A naive `JSON.stringify()` (TypeScript) or `Gson`/`kotlinx.serialization` default (Kotlin) does
**not** guarantee the same byte output for logically-equal objects: key order, whitespace, number
formatting, and Unicode normalization can all differ between languages, or even between library
versions. If the signer and verifier serialize differently, a correct signature over a correct
payload will fail to verify. Canonical serialization removes every degree of freedom that could
cause that.

## Rules (from kickoff)

1. **Key ordering:** object keys sorted **lexicographically** (byte-wise on the UTF-8 key), at
   every nesting level. Not insertion order, not schema-declaration order.
2. **Whitespace:** **compact** — no spaces, no newlines, anywhere. `{"a":1,"b":2}`, never
   `{"a": 1, "b": 2}`.
3. **Unicode normalization:** all string values normalized to **NFC**
   ([Unicode Normalization Form C](https://unicode.org/reports/tr15/)) before serialization.
4. **Amounts:** any integer-paise amount field (`amount`, `value`, `cap`, and equivalents) is
   written as a **decimal string** (`"25000"`), not a JSON number. See
   [id-conventions.md](id-conventions.md#amounts) for why.
5. **Binary fields (keys, signatures, hashes):** **base64url**, URL-safe alphabet, **unpadded**
   (no `=`). See [crypto.md](crypto.md#encoding--base64url-everywhere-unpadded).
6. **The `signature` field itself is excluded** from the bytes that get signed — you canonicalize
   every field *except* `signature`, sign that, then add `signature` to produce the final object.
   A verifier does the same: strip `signature`, canonicalize the rest, verify.
7. Encode the final canonical string as **UTF-8** before signing/hashing — the signature is over
   bytes, not over a JS/Kotlin string object.

## Algorithm

To produce the bytes that get signed for any SPARK object:

1. Take the object as a map of field → value, **excluding** `signature`.
2. Recursively apply steps 3–5 above to every nested object/array.
3. Sort keys lexicographically at every level.
4. Serialize compactly (no whitespace) with amounts as decimal strings and binary fields as
   base64url.
5. UTF-8 encode the resulting string.
6. Sign (or verify) those bytes with Ed25519 per [crypto.md](crypto.md).

To verify: do the same to the received object (again excluding its `signature` field) and check
the signature against the recomputed bytes with the signer's public key.

## PEM envelopes vs. field encoding

Certificates, purse tokens, and trust attestations are additionally wrapped in a **PEM envelope**
for storage/transport (see each format's doc), applied to the canonical JSON bytes (steps 1–5
above, run on the *entire* object including its `signature` field, since by that point the object
is finished and the PEM layer is just a container, not something re-signed).

Which base64 variant applies at which layer (field values vs. the PEM envelope itself) is decided
once, in [crypto.md](crypto.md#encoding-decided) — refer there rather than this doc for the rule
itself.

## Test vector

Input object (already in canonical field order and form):

```json
{"amount":"25000","device_id":"x"}
```

This is real ASCII with no Unicode normalization edge cases and is already lexicographically
sorted (`amount` < `device_id`), compact, with `amount` as a decimal string — i.e. this exact
string is what gets UTF-8 encoded and signed.

Reference Ed25519 test keypair (generated once, fixed here so implementations can cross-check —
**this is a shared test-only key, never use it for anything real**):

| | Value (base64url, unpadded) |
|---|---|
| Private seed | `rWUD47KRVvCyr9N4knN-ZyKP1z2o0UKQEJCoVuNrSRw` |
| Public key | `luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE` |

Signing `{"amount":"25000","device_id":"x"}` (UTF-8 bytes, PureEdDSA/Ed25519, no prehash) with
that private key produces:

```
Signature (base64url): 6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw
```

This was generated and verified with Node's `crypto` module (`crypto.sign(null, message,
privateKey)` / `crypto.verify(...)` both returned a match) — treat it as a known-answer test:
any implementation (Kotlin, TypeScript, or otherwise) that canonicalizes the input object and
signs it with the private seed above must reproduce this exact 64-byte signature. If it doesn't,
the bug is in your canonicalization, not in this test vector.

> Once real reference implementations exist in `android/` and `backend/`, add unit tests that
> assert against this vector directly, and extend this section with 2–3 more vectors covering
> nested objects (payer/payee) and non-ASCII strings (to exercise NFC normalization).

## Reference implementations

- Android (Kotlin): `TODO` — link once written.
- Backend (TypeScript): `TODO` — link once written.

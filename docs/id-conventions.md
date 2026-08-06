# ID Conventions

Formats and conventions for identifiers, timestamps, and amounts used across every SPARK
component and every other document in `docs/`. This file is the source of truth for these
primitive types — other docs reference it rather than redefining them.

**Consumed by:** Android, backend, dashboard — all three.

## Decisions (from kickoff)

| Primitive | Format |
|---|---|
| `device_id`, `account_id`, `tx_id`, `token_id` | UUID v4 |
| Timestamps | Unix epoch seconds (integer) |
| Amounts | Integer paise (1 INR = 100 paise) — **never** floats |
| Public keys | base64url, raw 32-byte key, unpadded |

## UUIDs (`device_id`, `account_id`, `tx_id`, `token_id`)

- Version: **UUID v4** (random), per [RFC 4122](https://www.rfc-editor.org/rfc/rfc4122).
- Canonical text form: lowercase, hyphenated, 36 characters —
  `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx` (`y` ∈ `{8,9,a,b}`).
- Generated client-side (device or dashboard) unless otherwise noted per-endpoint in
  [api-contract.md](api-contract.md) — e.g. `tx_id` is minted by the paying device, `token_id`
  is minted by the backend when it issues a purse token.
- Never reused. A `tx_id` collision is treated as a duplicate/replay, not an update.

Example: `4a1e6e2b-9c3e-4a2e-8f1a-6b2c9d4e7f10`

## Timestamps

- **Unix epoch seconds**, UTC, as a JSON integer (not milliseconds, not a string).
- Used for: `transaction.timestamp`, and any other plain "when did this happen" field.

> **Exception — ISO 8601 is used instead in two places, per kickoff:**
> - `certificate.not_before` / `certificate.not_after` (see [certificate-format.md](certificate-format.md))
> - `trust_attestation.timestamp` (see [trust-attestation-format.md](trust-attestation-format.md))
>
> These were called out explicitly in the kickoff as ISO 8601 while everything else defaults to
> Unix epoch seconds. This is a deliberate two-format system, not an oversight — **do not
> "normalize" one to match the other** without a docs PR. ISO 8601 strings use `YYYY-MM-DDTHH:MM:SSZ`
> (UTC, `Z` suffix, no fractional seconds).

## Amounts

- Always **integer paise**. `amount: 25000` means ₹250.00. Never represent money as a float in
  any signed payload, database column, or wire message.
- **Encoding note:** wherever an amount participates in a *signed* payload (transaction
  `amount`, purse token `value`/`cap`, trust attestation settled amount), it is written as a
  **decimal string** (`"25000"`, not `25000`), per the canonicalization rule in
  [canonical-serialization.md](canonical-serialization.md). This is deliberate: JS `number` and
  Kotlin `Long`/`Int` don't always round-trip identically through JSON re-serialization, and a
  signature computed over a re-serialized number can silently stop verifying. Non-signed,
  display-only amounts (e.g. in an admin dashboard table) may use whatever numeric type is
  convenient locally, but must be converted from the canonical decimal-string form, not parsed
  independently.
- No currency field — the system is INR-only for v1. If multi-currency is ever needed this doc
  and every amount field across `docs/` needs revisiting.

## Public key encoding

- Ed25519 and X25519 public keys are the **raw 32-byte key**, base64url-encoded, **unpadded**
  (no `=`). Not PEM, not SPKI/DER-wrapped, not standard (`+`/`/`) base64.
- See [crypto.md](crypto.md) for the algorithms and [canonical-serialization.md](canonical-serialization.md)
  for how this interacts with the PEM containers used by certificates/tokens/attestations.

## Open questions for next sync

- `device_counter` / `counter_start` (see [transaction-format.md](transaction-format.md),
  [purse-token-format.md](purse-token-format.md)) are integers but were not explicitly typed as
  "amounts," so they stay plain JSON integers, not strings — confirm this reading.

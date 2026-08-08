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

## Timestamps — two formats, deliberately, permanently

> **This split is confirmed intentional, not a gap to be "fixed" later.** It comes up often
> enough as a question that it's stated here plainly, twice, so it's unmissable:
>
> - **Everything defaults to Unix epoch seconds** (UTC, JSON integer, not milliseconds, not a
>   string) — this includes `transaction.timestamp`, `purse_token.expiry`, and any other
>   timestamp not listed below.
> - **Exactly two fields use ISO 8601 instead**, by explicit decision:
>   - `certificate.not_before` / `certificate.not_after` (see [certificate-format.md](certificate-format.md))
>   - `trust_attestation.timestamp` (see [trust-attestation-format.md](trust-attestation-format.md))
>
> **Rationale:** certs and trust attestations are the two formats a human (an admin in the
> dashboard, an auditor, a support agent) is expected to read directly and reason about validity
> windows/generation time for; ISO 8601 is legible at a glance where epoch seconds aren't.
> Everything else is machine-to-machine only, where epoch seconds are simpler to compare and
> arithmetic-on without a datetime library. This is a closed decision — do not normalize one
> format to match the other without a docs PR that changes this rule explicitly, and do not add
> a third timestamp format anywhere in `docs/` without updating this section.
>
> ISO 8601 strings use `YYYY-MM-DDTHH:MM:SSZ` (UTC, `Z` suffix, no fractional seconds).

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

## Resolved

- `device_counter` / `counter_start` are plain JSON integers, not decimal strings — confirmed by
  the purse token spend-enforcement decision (see
  [purse-token-format.md](purse-token-format.md#spend-enforcement-decided)), which treats
  `device_counter` purely as an incrementing count for continuity checking, not a paise amount.
  The decimal-string rule under [Amounts](#amounts) above does not apply to it.

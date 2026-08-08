# Purse Token Format

The purse token is what makes offline spending possible: the backend issues a signed,
Bank-authorized "you may spend up to this much, up to this cap, until this expiry" credential
that a device holds locally and draws down from when creating transactions — without needing
network access at spend time.

**Consumed by:** Android (holds it, decrements `value` locally as it spends, includes `token_id`
in every transaction it signs) and backend (issues via `POST /api/v1/purse/load`, refills via
`POST /api/v1/purse/topup`, and reconciles against synced transactions to catch overspend).

## Fields (from kickoff)

| Field | Type | Required | Description |
|---|---|---|---|
| `device_id` | string (UUID v4) | yes | The device this purse token is issued to. |
| `value` | string (decimal, integer paise) | yes | Current spendable balance loaded into the purse. Decrements locally on-device with each offline spend. |
| `cap` | string (decimal, integer paise) | yes | The hard ceiling this purse token may ever hold/spend against — the offline risk limit set by the backend (see `GET /api/v1/limit/recommendation`). |
| `counter_start` | integer | yes | The `device_counter` value (see [transaction-format.md](transaction-format.md)) at which this purse token became valid. Lets the backend detect a device replaying transactions from before this purse was issued. |
| `expiry` | integer (Unix epoch seconds) | yes | When this purse token stops being valid for offline spend; the device must resync (top up or reload) before then. |
| `token_id` | string (UUID v4) | yes | Unique ID for this purse token, referenced by every transaction that spends against it. |
| `signature` | string (base64url, 64 bytes) | yes | Ed25519 signature by the **Bank** key, over the canonical serialization of every field above except `signature`. |

## PEM envelope

Wrapped the same way as certificates — see
[certificate-format.md](certificate-format.md#pem-envelope) for the exact mechanics:

```
-----BEGIN SPARK PURSE TOKEN-----
<standard base64, RFC 7468, 64-char lines, of the canonical JSON UTF-8 bytes>
-----END SPARK PURSE TOKEN-----
```

## Value vs. cap

- `cap` is fixed for the lifetime of the token — the maximum the backend is willing to authorize
  this device to hold offline, informed by `GET /api/v1/limit/recommendation`. It also bounds
  every individual transaction: no single spend against this token may exceed `cap`.
- `value` is the balance **at issuance**, signed once and never mutated. The device may track a
  local "remaining" figure for its own UI, but that local figure is not signed and is not what
  the backend trusts — see **Spend enforcement** below. `POST /api/v1/purse/topup` is how a
  device gets a **new**, freshly-signed token once it's back online (see open questions for
  whether this reuses `token_id`).

## Spend enforcement (decided)

The signature on a purse token covers only the **initial `value` at issuance** — the backend does
**not** re-verify a "current value" at sync time, since there is no signed "current value" to
check (mutating a signed field would break the signature). Instead, enforcement happens entirely
at sync, from the device's uploaded transaction history:

1. **Completeness check:** for every transaction spending against this `token_id`, the payer's
   `device_counter` must be present with no gaps, and each `prev_tx_hash` must chain correctly
   (see [transaction-format.md](transaction-format.md)) — incrementing by exactly 1 per
   transaction. This is what lets the backend trust it has seen the *complete* set of spends
   against this token, not a partial/cherry-picked subset, before doing the check below.
2. **Aggregate cap check:** `sum(transaction.amount for all transactions with this token_id) <=`
   the token's signed `value`.
3. **Per-transaction cap check:** every individual `transaction.amount <=` the token's signed
   `cap`.

A device that tries to spend more than `value` (in total) or more than `cap` (in any single
transaction) produces a batch that fails step 2 or 3 at `POST /api/v1/sync/transactions` — see
[api-contract.md](api-contract.md). This is enforced entirely server-side at sync; a compromised
or buggy device can *locally* believe it's spent past its limit while offline, but that spend
won't clear sync.

## Example

```json
{
  "device_id": "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
  "value": "50000",
  "cap": "100000",
  "counter_start": 40,
  "expiry": 1770600000,
  "token_id": "9f2c1a3e-5b4d-4e6f-8a1b-2c3d4e5f6a7b",
  "signature": "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
}
```

> `signature` above is illustrative-format only — see
> [canonical-serialization.md](canonical-serialization.md#test-vector) for a real, verified test
> vector.

## Open questions for next sync

- Whether `POST /api/v1/purse/topup` issues a brand-new `token_id` or reuses the existing one
  with a new `value`/`expiry`/signature. Still unresolved — not covered by the spend-enforcement
  decision above.

Resolved:
- ~~Value mutation vs. signature~~ — see **Spend enforcement** above: `value` is issuance-time
  only, never mutated; enforcement is done server-side from synced transaction history.
- ~~`expiry` format~~ — Unix epoch seconds, confirmed. Certificates and trust attestations are the
  only two ISO 8601 exceptions in the system; everything else, including this field, follows the
  default. See [id-conventions.md](id-conventions.md#timestamps).

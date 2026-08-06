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
  this device to hold offline, informed by `GET /api/v1/limit/recommendation`.
- `value` is the current balance and only ever decreases on-device as the wallet spends (the
  device does not re-sign the token to update `value` locally — see open question below).
  `POST /api/v1/purse/topup` is how a device gets a **new**, freshly-signed token with a
  refilled `value` once it's back online.

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

- Since `value` decreases locally as the device spends, but the token is a *signed* object whose
  signature covers `value` — does the on-device `value` after local spends no longer match the
  signed value, and is that expected (i.e. `value` in the signed token represents the balance
  *at issuance*, and the true remaining balance is computed as `value` minus the sum of synced/
  pending transactions against this `token_id`, rather than mutated in place)? This needs to be
  nailed down before implementation — as written, mutating a signed field would break the
  signature.
- Whether `POST /api/v1/purse/topup` issues a brand-new `token_id` or reuses the existing one
  with a new `value`/`expiry`/signature.
- Confirm `expiry` is Unix epoch seconds (per the general rule in
  [id-conventions.md](id-conventions.md#timestamps)) and not ISO 8601 like certificates —
  kickoff notes didn't say explicitly, unlike `certificate.not_before`/`not_after` which were
  called out.

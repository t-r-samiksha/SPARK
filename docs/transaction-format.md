# Transaction Format

The JSON schema for a single signed, offline-capable payment between two devices. This is the
core unit of the protocol: it's created and signed entirely offline by the payer's device,
handed to the payee's device (e.g. over BLE/NFC/QR — out of scope for this doc), and later
uploaded to the backend in a batch via `POST /api/v1/sync/transactions` (see
[api-contract.md](api-contract.md)).

**Consumed by:** Android (creates + signs as payer, receives + verifies as payee) and backend
(validates + persists on sync). Dashboard only sees these indirectly through backend
`admin/incidents` endpoints.

## Fields (from kickoff)

| Field | Type | Required | Description |
|---|---|---|---|
| `tx_id` | string (UUID v4) | yes | Unique ID for this transaction, minted by the payer's device. See [id-conventions.md](id-conventions.md). |
| `token_id` | string (UUID v4) | yes | The purse token this spend draws down. See [purse-token-format.md](purse-token-format.md). |
| `amount` | string (decimal, integer paise) | yes | The amount moving from payer to payee. Decimal string, not a JSON number — see [canonical-serialization.md](canonical-serialization.md). |
| `payer` | object | yes | `{ device_id, account_id, cert }` — see below. |
| `payee` | object | yes | `{ device_id, account_id, cert }` — see below. |
| `device_counter` | integer | yes | Monotonically increasing per-device counter, incremented by the payer for every transaction it signs. Used with `prev_tx_hash` to detect replayed/reordered/forked offline transactions. |
| `prev_tx_hash` | string (base64url SHA-256) or `null` | yes | SHA-256 hash (base64url) of the canonical serialization of the payer's previous transaction. `null` for a device's first-ever transaction. |
| `timestamp` | integer (Unix epoch seconds) | yes | When the payer's device signed the transaction (device-local clock — not trusted, informational + ordering hint only). |
| `signature` | string (base64url, 64 bytes) | yes | Ed25519 signature by the **payer's device key**, over the canonical serialization of every field above except `signature` itself. |

### `payer` / `payee` object

| Field | Type | Description |
|---|---|---|
| `device_id` | string (UUID v4) | The device involved. |
| `account_id` | string (UUID v4) | The bank account the device is enrolled against. |
| `cert` | string (PEM) | The device's certificate, embedded in full as its PEM envelope (same string form used everywhere else — see [certificate-format.md](certificate-format.md)). Embedded, not just referenced, so the transaction can be verified fully offline without a network round-trip to fetch the cert. |

## Signing

- **Who signs:** the payer's device, with its Ed25519 device private key (the same key whose
  public half is bound in `payer.cert.device_public_key`).
- **What's signed:** the canonical serialization (sorted keys, compact, NFC, amounts as decimal
  strings, binary fields base64url — full rules in
  [canonical-serialization.md](canonical-serialization.md)) of every field in this document
  **except** `signature`.
- **Who verifies:** the payee's device on receipt (before accepting the payment offline), and the
  backend again on sync. Both verify using `payer.cert.device_public_key`, after first checking
  that `payer.cert` itself is validly signed by the Bank Root CA and not expired/revoked.

## JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "SparkTransaction",
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "tx_id": { "type": "string", "format": "uuid" },
    "token_id": { "type": "string", "format": "uuid" },
    "amount": { "type": "string", "pattern": "^[0-9]+$" },
    "payer": { "$ref": "#/$defs/party" },
    "payee": { "$ref": "#/$defs/party" },
    "device_counter": { "type": "integer", "minimum": 0 },
    "prev_tx_hash": {
      "oneOf": [
        { "type": "string", "contentEncoding": "base64url", "minLength": 43, "maxLength": 43 },
        { "type": "null" }
      ]
    },
    "timestamp": { "type": "integer", "minimum": 0 },
    "signature": { "type": "string", "contentEncoding": "base64url", "minLength": 86, "maxLength": 86 }
  },
  "required": [
    "tx_id", "token_id", "amount", "payer", "payee",
    "device_counter", "prev_tx_hash", "timestamp", "signature"
  ],
  "$defs": {
    "party": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "device_id": { "type": "string", "format": "uuid" },
        "account_id": { "type": "string", "format": "uuid" },
        "cert": { "type": "string", "$comment": "PEM envelope — see certificate-format.md." }
      },
      "required": ["device_id", "account_id", "cert"]
    }
  }
}
```

## Example

```json
{
  "tx_id": "4a1e6e2b-9c3e-4a2e-8f1a-6b2c9d4e7f10",
  "token_id": "9f2c1a3e-5b4d-4e6f-8a1b-2c3d4e5f6a7b",
  "amount": "25000",
  "payer": {
    "device_id": "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
    "account_id": "2b3c4d5e-2222-4b3c-9d2e-3f4a5b6c7d8e",
    "cert": "-----BEGIN SPARK DEVICE CERTIFICATE-----\n...\n-----END SPARK DEVICE CERTIFICATE-----"
  },
  "payee": {
    "device_id": "3c4d5e6f-3333-4c5d-ae3f-4a5b6c7d8e9f",
    "account_id": "4d5e6f7a-4444-4d6e-bf4a-5b6c7d8e9fa0",
    "cert": "-----BEGIN SPARK DEVICE CERTIFICATE-----\n...\n-----END SPARK DEVICE CERTIFICATE-----"
  },
  "device_counter": 42,
  "prev_tx_hash": "kZ7X2mN4pQ8rS1tU6vW9xY0zA3bC5dE7fG9hJ1kL3mN",
  "timestamp": 1770000000,
  "signature": "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
}
```

> `cert` fields above are placeholders — see [certificate-format.md](certificate-format.md) for
> the real structure. `prev_tx_hash` and `signature` are illustrative-format only (correct length
> and encoding), not values computed from this exact example object — don't copy them as a test
> vector. For a real, verified signature test vector see
> [canonical-serialization.md](canonical-serialization.md#test-vector).

## Open questions for next sync

- Confirm `prev_tx_hash: null` is the right representation for a device's first transaction
  (vs. e.g. hashing a fixed genesis value).
- Confirm whether `device_counter` restarts per `token_id` (per purse) or is a single
  ever-increasing counter per device across all purses — this affects how the backend validates
  it against `purse_token.counter_start`.

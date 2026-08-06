# Certificate Format

The device certificate binds a device's Ed25519 public key to a `device_id`/`account_id` pair,
signed by the Bank Root CA. It is **not** X.509 — it's a lightweight, PEM-wrapped JSON structure
designed to be small enough to embed directly in a transaction (see
[transaction-format.md](transaction-format.md)) so payments can be verified fully offline.

**Consumed by:** Android (holds its own cert, embeds it in every transaction it signs, verifies
counterparty certs offline) and backend (issues certs on enrollment via `POST /api/v1/enroll`,
verifies certs on sync, revokes them via `POST /api/v1/admin/revoke`).

## Fields (from kickoff)

| Field | Type | Required | Description |
|---|---|---|---|
| `device_id` | string (UUID v4) | yes | The device this cert is issued to. |
| `account_id` | string (UUID v4) | yes | The bank account the device is enrolled against. |
| `device_public_key` | string (base64url, 32 bytes) | yes | The device's Ed25519 public key. |
| `serial_number` | string | yes | Unique serial for this cert, used for revocation/CRL lookups (see `GET /api/v1/sync/updates`). |
| `not_before` | string (ISO 8601) | yes | Cert becomes valid at this time. **ISO 8601, not Unix epoch** — see the exception noted in [id-conventions.md](id-conventions.md#timestamps). |
| `not_after` | string (ISO 8601) | yes | Cert expires at this time. |
| `signature` | string (base64url, 64 bytes) | yes | Ed25519 signature by the **Bank Root CA** key, over the canonical serialization of every field above except `signature`. |

## PEM envelope

The full JSON object above (including `signature`) is wrapped in a PEM envelope for storage and
transport:

```
-----BEGIN SPARK DEVICE CERTIFICATE-----
<standard base64, RFC 7468, 64-char lines, of the canonical JSON UTF-8 bytes>
-----END SPARK DEVICE CERTIFICATE-----
```

See [canonical-serialization.md](canonical-serialization.md#pem-envelopes-vs-field-encoding) for
why this is a *different* base64 variant than the base64url used for `device_public_key`/
`signature` inside the JSON — don't conflate the two when writing a parser.

## Issuance

- Issued by the backend in response to `POST /api/v1/enroll` (see
  [api-contract.md](api-contract.md)), after the device submits its public key and an attestation
  blob (see [trust-attestation-format.md](trust-attestation-format.md) — note: enrollment
  attestation and the trust-attestation "settlement history" format are conceptually different
  uses of "attestation"; confirm at next sync whether `attestation_blob` in `/enroll` reuses this
  format or is a separate platform-attestation payload, e.g. Play Integrity).
- Signed with the **Bank Root CA** Ed25519 key (see the key-separation open question in
  [crypto.md](crypto.md#signing--ed25519-pureeddsa)).

## Revocation

- A cert is revoked via `POST /api/v1/admin/revoke` (dashboard/admin action), keyed by
  `serial_number` or `device_id`.
- Revoked serials are distributed to devices as a CRL (certificate revocation list) via
  `GET /api/v1/sync/updates`. Devices must check incoming counterparty certs against their
  locally cached CRL even when verifying fully offline — a stale/pre-revocation CRL is an
  accepted, bounded risk of offline operation, not a bug.

## Example

```json
{
  "device_id": "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
  "account_id": "2b3c4d5e-2222-4b3c-9d2e-3f4a5b6c7d8e",
  "device_public_key": "luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE",
  "serial_number": "SPARK-CERT-000001",
  "not_before": "2026-08-06T00:00:00Z",
  "not_after": "2027-08-06T00:00:00Z",
  "signature": "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
}
```

> `device_public_key` above is a real test key from
> [canonical-serialization.md](canonical-serialization.md#test-vector); `signature` is
> illustrative-format only (right length/encoding), not computed from this exact example — don't
> use it as a known-answer test.

## Open questions for next sync

- Exact `serial_number` format/allocation scheme (shown above as illustrative).
- Whether the Root CA key also signs purse tokens/trust attestations or a separate operational
  key does (see [crypto.md](crypto.md)).
- Cert renewal flow: does a device re-enroll before `not_after`, or is there a dedicated renew
  endpoint? Not in the current [api-contract.md](api-contract.md) endpoint list.

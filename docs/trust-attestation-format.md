# Trust Attestation Format

A trust attestation is a Bank-signed statement summarizing settlement history between two
devices — e.g. "device_a settled 25000 paise with device_b, 14 times." It's the underlying edge
in the trust graph used for merchant reputation and offline spend-limit recommendations.

**Consumed by:** backend (computes and signs these from synced transaction history), Android
(may hold/present relevant attestations offline as evidence of trust history), dashboard (reads
via `GET /api/v1/trust/attestations` / `GET /api/v1/merchant/{id}/trust` for reputation views).

## Fields (derived from the kickoff example)

Kickoff described this as the sentence: *"device_a:uuid settled 25000 paise with device_b:uuid,
14 times"* plus an ISO 8601 timestamp, signed by the Bank. That sentence is decomposed into
structured fields below — **this decomposition is inferred, not verbatim from the meeting**, and
should be confirmed at the next sync.

| Field | Type | Required | Description |
|---|---|---|---|
| `subject_a` | string (UUID v4) | yes | `device_id` of the first party (`device_a` in the example sentence). |
| `subject_b` | string (UUID v4) | yes | `device_id` of the second party (`device_b`). |
| `settled_amount` | string (decimal, integer paise) | yes | Total amount settled between the two devices ("25000 paise" in the example). |
| `settlement_count` | integer | yes | Number of settlements the amount above represents ("14 times"). |
| `timestamp` | string (ISO 8601) | yes | When this attestation was generated. **ISO 8601, not Unix epoch** — explicitly called out in kickoff, same exception as `certificate.not_before`/`not_after` (see [id-conventions.md](id-conventions.md#timestamps)). |
| `signature` | string (base64url, 64 bytes) | yes | Ed25519 signature by the **Bank** key, over the canonical serialization of every field above except `signature`. |

## PEM envelope

Wrapped the same way as certificates and purse tokens — see
[certificate-format.md](certificate-format.md#pem-envelope):

```
-----BEGIN SPARK TRUST ATTESTATION-----
<standard base64, RFC 7468, 64-char lines, of the canonical JSON UTF-8 bytes>
-----END SPARK TRUST ATTESTATION-----
```

## Example

```json
{
  "subject_a": "1a2b3c4d-1111-4a2b-8c1d-2e3f4a5b6c7d",
  "subject_b": "3c4d5e6f-3333-4c5d-ae3f-4a5b6c7d8e9f",
  "settled_amount": "25000",
  "settlement_count": 14,
  "timestamp": "2026-08-06T12:00:00Z",
  "signature": "6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw"
}
```

Rendered as the human-readable summary from the kickoff, this reads: *"device_a:1a2b3c4d-1111-…
settled 25000 paise with device_b:3c4d5e6f-3333-…, 14 times."*

> `signature` above is illustrative-format only — see
> [canonical-serialization.md](canonical-serialization.md#test-vector) for a real, verified test
> vector.

## Open questions for next sync

- Is `settled_amount` a running cumulative total (all-time) or scoped to a rolling window (e.g.
  last 90 days)? Affects whether attestations are reissued/superseded or append-only.
- Is the relationship between `subject_a`/`subject_b` directional (a paid b) or symmetric (total
  settled regardless of direction)? The example sentence ("settled ... with") reads as symmetric,
  but confirm.
- Whether an `attestation_id` (UUID) is needed so individual attestations can be looked up,
  deduplicated, or superseded — not in the kickoff notes, but `GET /api/v1/trust/attestations?subject={id}`
  returning "signed edges" (plural) suggests callers may need to reference one specifically.
- Whether `GET /api/v1/merchant/{id}/trust`'s "reputation bundle" is just an array of these
  attestations or a separately-shaped aggregate — see the open question in
  [api-contract.md](api-contract.md).

# API Contract

OpenAPI 3.0 definition for the SPARK backend REST API, consumed by the Android wallet (core
endpoints) and the admin dashboard (core + admin endpoints).

**Consumed by:** backend (implements), Android (calls core endpoints), dashboard (calls core +
admin endpoints).

> The schemas below (`Transaction`, `Certificate`, `PurseToken`, `TrustAttestation`) are inlined
> and must be kept in sync by hand with [transaction-format.md](transaction-format.md),
> [certificate-format.md](certificate-format.md), [purse-token-format.md](purse-token-format.md),
> and [trust-attestation-format.md](trust-attestation-format.md). Once implementation starts,
> consider extracting them to shared `docs/schemas/*.json` files that both this doc and the
> format docs `$ref`, so there's a single copy instead of two that can drift.

## Endpoint summary (from kickoff)

### Core

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/v1/enroll` | `{account_id, public_key, attestation_blob}` | device cert |
| POST | `/api/v1/auth/challenge` | `{device_id}` | `{nonce}` |
| POST | `/api/v1/auth/verify` | `{device_id, signed_nonce}` | `{session_token}` |
| POST | `/api/v1/purse/load` | `{value}` — debited from account real_balance | `{purse_token}` |
| POST | `/api/v1/purse/topup` | refill existing purse | `{purse_token}` |
| GET | `/api/v1/purse/status` | — | `{remaining, cap, expiry}` |
| POST | `/api/v1/sync/transactions` | batch of signed txs | per-tx results + double-spend incidents |
| GET | `/api/v1/sync/updates` | `?since=<epoch_seconds>` (optional) | CRL delta + cursor + flags + cap + trust attestations |
| GET | `/api/v1/trust/attestations?subject={id}` | — | signed edges |
| GET | `/api/v1/merchant/{id}/trust` | — | reputation bundle |
| GET | `/api/v1/limit/recommendation` | — | AI cap suggestion |

### Admin

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/v1/admin/incidents?type=double_spend\|fraud_flag\|all` | — | incidents list (replaces the old separate `/admin/fraud`) |
| POST | `/api/v1/admin/revoke` | revoke a device cert | revocation result |
| POST | `/api/v1/admin/disaster/toggle` | enable/disable by region | toggle result |

## Authentication

`Authorization: Bearer <session_token>` obtained from `/api/v1/auth/verify`, required on every
endpoint except `/enroll`, `/auth/challenge`, and `/auth/verify` themselves.

> **Open question:** kickoff didn't specify a separate admin login flow. Admin endpoints are
> shown below requiring the same `bearerAuth` scheme as core endpoints, but dashboard sessions
> almost certainly need different scope/claims (admin role) than a device session — confirm
> whether that's a claim inside the same token type or a genuinely separate auth mechanism.

## Versioning

All routes are under `/api/v1`. No deprecation policy decided yet — add one before `/api/v2` is
ever needed.

## OpenAPI 3.0 document

```yaml
openapi: 3.0.3
info:
  title: SPARK Backend API
  version: 0.1.0
  description: >
    API surface exposed by the SPARK backend to the wallet app (Android) and admin dashboard.
servers:
  - url: https://TODO/api/v1
security:
  - bearerAuth: []
paths:
  /enroll:
    post:
      summary: Enroll a device and receive a signed device certificate
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [account_id, public_key, attestation_blob]
              properties:
                account_id:
                  type: string
                  format: uuid
                public_key:
                  type: string
                  description: Device Ed25519 public key, base64url, unpadded, 32 raw bytes.
                attestation_blob:
                  type: string
                  description: >
                    Opaque platform attestation payload proving device/app integrity.
                    Open question: relationship to trust-attestation-format.md's attestation
                    concept — likely a distinct platform-attestation payload (e.g. Play
                    Integrity), not a SparkTrustAttestation. Confirm at next sync.
      responses:
        "200":
          description: Enrollment succeeded
          content:
            application/json:
              schema:
                type: object
                required: [cert]
                properties:
                  cert:
                    $ref: "#/components/schemas/CertificatePem"
        "409":
          description: Device or account already enrolled

  /auth/challenge:
    post:
      summary: Request a fresh nonce to authenticate with
      description: >
        Decided: requires device_id (not an empty body as originally documented). Nonces must be
        device-scoped at issuance so /auth/verify can do an O(1) lookup, since verify only sends
        {device_id, signed_nonce} and never echoes the plaintext nonce back.
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [device_id]
              properties:
                device_id:
                  type: string
                  format: uuid
                  description: The device requesting a nonce to authenticate with.
      responses:
        "200":
          description: Nonce issued
          content:
            application/json:
              schema:
                type: object
                required: [nonce]
                properties:
                  nonce:
                    type: string
                    description: base64url, unpadded, server-generated random nonce.

  /auth/verify:
    post:
      summary: Prove possession of the device key over the issued nonce, get a session token
      description: >
        Decided: the server looks up the device's public key (via its current, non-revoked
        certificate) by device_id, then verifies signed_nonce as an Ed25519 signature over the
        nonce previously issued by /auth/challenge.
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [device_id, signed_nonce]
              properties:
                device_id:
                  type: string
                  format: uuid
                  description: Identifies which device's public key to verify signed_nonce against.
                signed_nonce:
                  type: string
                  description: base64url Ed25519 signature over the nonce from /auth/challenge.
      responses:
        "200":
          description: Verified
          content:
            application/json:
              schema:
                type: object
                required: [session_token]
                properties:
                  session_token:
                    type: string
        "401":
          description: Invalid signature or expired/unknown nonce

  /purse/load:
    post:
      summary: Request a new purse token
      description: >
        Decided: value is a transfer from the account's real_balance to the offline purse, debited
        atomically on issuance — not a live external bank check.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [value]
              properties:
                value:
                  type: string
                  pattern: "^[0-9]+$"
                  description: >
                    Integer paise to load into the purse, as a decimal string. Debited from the
                    account's real_balance; rejected if it exceeds either the recommended cap or
                    the account's available real_balance.
      responses:
        "200":
          description: Purse token issued
          content:
            application/json:
              schema:
                type: object
                required: [purse_token]
                properties:
                  purse_token:
                    $ref: "#/components/schemas/PurseTokenPem"

  /purse/topup:
    post:
      summary: Refill an existing purse
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [token_id, amount]
              properties:
                token_id:
                  type: string
                  format: uuid
                amount:
                  type: string
                  pattern: "^[0-9]+$"
                  description: Integer paise to add, as a decimal string.
      responses:
        "200":
          description: >
            Purse refilled. Open question (see purse-token-format.md): whether this returns a
            new token_id or the same one re-signed with an updated value/expiry.
          content:
            application/json:
              schema:
                type: object
                required: [purse_token]
                properties:
                  purse_token:
                    $ref: "#/components/schemas/PurseTokenPem"

  /purse/status:
    get:
      summary: Current purse status
      responses:
        "200":
          description: Purse status
          content:
            application/json:
              schema:
                type: object
                required: [remaining, cap, expiry]
                properties:
                  remaining:
                    type: string
                    pattern: "^[0-9]+$"
                    description: Integer paise, decimal string.
                  cap:
                    type: string
                    pattern: "^[0-9]+$"
                  expiry:
                    type: integer
                    description: Unix epoch seconds.

  /sync/transactions:
    post:
      summary: Upload a batch of signed offline transactions
      description: >
        Decided spend-enforcement rule (see purse-token-format.md#spend-enforcement-decided):
        the purse token's signature covers only its issuance-time value, so the backend does not
        re-verify a "current value." Instead, for each token_id referenced by the uploaded
        transactions, the backend (1) checks device_counter/prev_tx_hash continuity to confirm
        it has the complete spend history for that token, (2) verifies
        sum(transaction.amount for that token_id) <= the token's signed value, and (3) verifies
        each individual transaction.amount <= the token's signed cap. Transactions that fail
        these checks are rejected at sync, not accepted-then-reconciled.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [transactions]
              properties:
                transactions:
                  type: array
                  items:
                    $ref: "#/components/schemas/Transaction"
      responses:
        "200":
          description: >
            Decided: adds `incidents` alongside `results` (not in the original kickoff-inferred
            shape) — a double-spend detected during this batch must not be silently dropped, so
            it's surfaced directly in the sync response for a future admin endpoint/dashboard to
            read, not just recorded server-side. See
            src/settlement/doubleSpendResolver.ts (backend).
          content:
            application/json:
              schema:
                type: object
                required: [results, incidents]
                properties:
                  results:
                    type: array
                    items:
                      type: object
                      required: [tx_id, status]
                      properties:
                        tx_id:
                          type: string
                          format: uuid
                        status:
                          type: string
                          enum: [accepted, rejected, duplicate]
                        reason:
                          type: string
                  incidents:
                    type: array
                    description: Double-spend incidents detected while settling this batch.
                    items:
                      type: object
                      required: [id, token_id, device_id, tx_id_a, tx_id_b, detected_at]
                      properties:
                        id:
                          type: string
                          format: uuid
                        token_id:
                          type: string
                          format: uuid
                        device_id:
                          type: string
                          format: uuid
                        tx_id_a:
                          type: string
                          format: uuid
                        tx_id_b:
                          type: string
                          format: uuid
                        detected_at:
                          type: integer
                          description: Unix epoch seconds.

  /sync/updates:
    get:
      summary: Pull CRL, flags, cap, and trust attestations for offline caching
      description: >
        Decided: adds a `since` cursor query param and a `crl_cursor` response field, neither of
        which existed in the original kickoff-inferred shape. Returning the *full* CRL on every
        call doesn't scale over the weak/metered links this system is designed for, so `crl` is
        now a delta: certs revoked since `since` (Unix epoch seconds — not ISO 8601; see
        id-conventions.md#timestamps, `since` isn't one of the two documented exceptions), or the
        full current CRL if `since` is omitted (first-sync case). Also resolves part of the
        `flags` open question below for the disaster-event case specifically (fraud flags remain
        undecided) and adds `recommended_cap` (not in the original shape either) — same field name
        as GET /limit/recommendation's own response, still Member C's placeholder. See
        src/api/sync/routes.ts (backend).
      parameters:
        - name: since
          in: query
          required: false
          schema:
            type: string
            pattern: "^[0-9]+$"
          description: >
            Unix epoch seconds. Returns CRL entries revoked at or after this cursor. Omit for the
            full current CRL (e.g. a device's first sync, or one that lost its local cursor).
      responses:
        "200":
          description: >
            `flags` items include a `kind` discriminator so a future fraud-flag shape can coexist
            in the same array unambiguously — see the open question on `flags` below, now
            partially resolved for the "disaster-mode region flags" case.
          content:
            application/json:
              schema:
                type: object
                required: [crl, crl_cursor, flags, recommended_cap, trust_attestations]
                properties:
                  crl:
                    type: array
                    description: >
                      Revoked certificate serial numbers revoked at or after `since` (or the full
                      CRL if `since` was omitted).
                    items:
                      type: string
                  crl_cursor:
                    type: integer
                    description: >
                      Unix epoch seconds. Pass this back as `since` on the next call. Advances even
                      when the delta is empty, so a client never has to re-query the same window.
                  flags:
                    type: array
                    description: >
                      Open question, partially resolved: shape/purpose beyond disaster-mode region
                      flags (kind=disaster) not specified in kickoff — whether/how per-account
                      fraud flags fit into this same array (a different `kind`, or a separate
                      field) is still undecided.
                    items:
                      oneOf:
                        - type: object
                          required:
                            [kind, type, region_geo, active, higher_cap, essential_only, started_at, ended_at]
                          properties:
                            kind:
                              type: string
                              enum: [disaster]
                            type:
                              type: string
                              description: Disaster category, e.g. "flood" — freeform, no enum decided.
                            region_geo:
                              type: string
                              description: >
                                Freeform region name/code today; real geographic boundary
                                representation (polygon, geohash, etc.) is undecided.
                            active:
                              type: boolean
                            higher_cap:
                              type: [string, "null"]
                              description: Elevated offline spend cap during the disaster, decimal paise.
                            essential_only:
                              type: boolean
                            started_at:
                              type: integer
                              description: Unix epoch seconds.
                            ended_at:
                              type: [integer, "null"]
                              description: Unix epoch seconds.
                  recommended_cap:
                    type: string
                    pattern: "^[0-9]+$"
                    description: >
                      Integer paise, decimal string. Same value/source as
                      GET /limit/recommendation — still Member C's placeholder model.
                  trust_attestations:
                    type: array
                    description: "TODO: stubbed as empty — real trust-graph logic belongs to Member C."
                    items:
                      $ref: "#/components/schemas/TrustAttestationPem"

  /trust/attestations:
    get:
      summary: Signed trust attestations for a subject device
      parameters:
        - name: subject
          in: query
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "200":
          description: Signed edges involving the subject
          content:
            application/json:
              schema:
                type: object
                required: [attestations]
                properties:
                  attestations:
                    type: array
                    items:
                      $ref: "#/components/schemas/TrustAttestationPem"

  /merchant/{id}/trust:
    get:
      summary: Reputation bundle for a merchant
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "200":
          description: >
            "Reputation bundle" shape not specified in kickoff beyond the name — shown here as
            an array of attestations plus a placeholder summary; confirm real shape at next
            sync.
          content:
            application/json:
              schema:
                type: object
                required: [merchant_id, attestations]
                properties:
                  merchant_id:
                    type: string
                    format: uuid
                  attestations:
                    type: array
                    items:
                      $ref: "#/components/schemas/TrustAttestationPem"
                  summary:
                    type: object
                    description: Open question — aggregate score/shape TBD.

  /limit/recommendation:
    get:
      summary: AI-suggested offline spend cap for the authenticated device
      responses:
        "200":
          description: >
            Response shape inferred — kickoff only says "AI cap suggestion"; confirm fields
            (e.g. whether reasoning/confidence is returned) at next sync.
          content:
            application/json:
              schema:
                type: object
                required: [recommended_cap]
                properties:
                  recommended_cap:
                    type: string
                    pattern: "^[0-9]+$"
                    description: Integer paise, decimal string.

  /admin/incidents:
    get:
      summary: List operational incidents, optionally filtered by type
      description: >
        Decided: this endpoint replaces the separate /admin/fraud endpoint. Fraud cases are
        incidents with type=fraud_flag, not a distinct resource.
      parameters:
        - name: type
          in: query
          required: false
          schema:
            type: string
            enum: [double_spend, fraud_flag, all]
            default: all
          description: Filter incidents by type; omit or "all" for every incident type.
      responses:
        "200":
          description: Incidents list
          content:
            application/json:
              schema:
                type: object
                required: [incidents]
                properties:
                  incidents:
                    type: array
                    items:
                      type: object
                      required: [type]
                      description: Shape TBD beyond the type discriminator.
                      properties:
                        type:
                          type: string
                          enum: [double_spend, fraud_flag]

  /admin/revoke:
    post:
      summary: Revoke a device certificate
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [serial_number, reason]
              properties:
                serial_number:
                  type: string
                device_id:
                  type: string
                  format: uuid
                  description: Optional convenience lookup if serial_number isn't on hand.
                reason:
                  type: string
      responses:
        "200":
          description: Revoked
          content:
            application/json:
              schema:
                type: object
                required: [serial_number, revoked_at]
                properties:
                  serial_number:
                    type: string
                  revoked_at:
                    type: integer
                    description: Unix epoch seconds.

  /admin/disaster/toggle:
    post:
      summary: Enable or disable service for a region (disaster mode)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [region, enabled]
              properties:
                region:
                  type: string
                enabled:
                  type: boolean
      responses:
        "200":
          description: Toggled
          content:
            application/json:
              schema:
                type: object
                required: [region, enabled, updated_at]
                properties:
                  region:
                    type: string
                  enabled:
                    type: boolean
                  updated_at:
                    type: integer
                    description: Unix epoch seconds.

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: session_token

  schemas:
    CertificatePem:
      type: string
      description: >
        PEM-encoded device certificate. See certificate-format.md for the JSON structure inside
        the envelope.

    PurseTokenPem:
      type: string
      description: >
        PEM-encoded purse token. See purse-token-format.md for the JSON structure inside the
        envelope.

    TrustAttestationPem:
      type: string
      description: >
        PEM-encoded trust attestation. See trust-attestation-format.md for the JSON structure
        inside the envelope.

    Transaction:
      type: object
      additionalProperties: false
      description: See transaction-format.md for the full schema and signing rules.
      required:
        - tx_id
        - token_id
        - amount
        - payer
        - payee
        - device_counter
        - prev_tx_hash
        - timestamp
        - signature
      properties:
        tx_id:
          type: string
          format: uuid
        token_id:
          type: string
          format: uuid
        amount:
          type: string
          pattern: "^[0-9]+$"
        payer:
          $ref: "#/components/schemas/TransactionParty"
        payee:
          $ref: "#/components/schemas/TransactionParty"
        device_counter:
          type: integer
          minimum: 0
        prev_tx_hash:
          type: [string, "null"]
        timestamp:
          type: integer
        signature:
          type: string

    TransactionParty:
      type: object
      additionalProperties: false
      required: [device_id, account_id, cert]
      properties:
        device_id:
          type: string
          format: uuid
        account_id:
          type: string
          format: uuid
        cert:
          $ref: "#/components/schemas/CertificatePem"
```

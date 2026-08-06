# SPARK Shared Contracts

These documents are the binding source of truth for anything that crosses a process boundary
(wallet ↔ backend, backend ↔ dashboard, device ↔ device). Every workstream (android/, backend/,
dashboard/) implements against these, not against each other's code.

Changes to any file here must be PR'd to `dev` like any other change, and require sign-off from
every workstream the change affects.

## Index

| Doc | Covers |
|---|---|
| [transaction-format.md](transaction-format.md) | JSON schema for a signed transaction |
| [certificate-format.md](certificate-format.md) | Device certificate format |
| [purse-token-format.md](purse-token-format.md) | Purse token format |
| [trust-attestation-format.md](trust-attestation-format.md) | Trust attestation format |
| [api-contract.md](api-contract.md) | OpenAPI 3.0 definitions for the backend API |
| [canonical-serialization.md](canonical-serialization.md) | Exact byte serialization used for signing |
| [crypto.md](crypto.md) | Algorithms, key sizes, encodings |
| [id-conventions.md](id-conventions.md) | ID formats and conventions used across components |

> Status: populated from the kickoff meeting (2026-08-06). Several details were inferred beyond
> what was explicitly decided and are marked "Open questions for next sync" in the relevant doc —
> resolve those before implementation depends on them.

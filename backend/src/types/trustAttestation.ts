import { Base64Url, DecimalPaise, Iso8601Timestamp, Uuid } from './common';

// Mirrors docs/trust-attestation-format.md. Field decomposition is inferred from the kickoff
// example sentence per the doc, not verbatim from a spec — flagged there as unconfirmed.

export interface TrustAttestation {
  subject_a: Uuid;
  subject_b: Uuid;
  settled_amount: DecimalPaise;
  settlement_count: number;
  /** ISO 8601 — exception to the epoch-seconds default, see docs/id-conventions.md#timestamps. */
  timestamp: Iso8601Timestamp;
  /** Ed25519 signature by the Bank key, over every field above except this one. */
  signature: Base64Url;
}

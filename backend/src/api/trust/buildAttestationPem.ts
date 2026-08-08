import { pemEncode, canonicalizeFull, JsonValue } from '../../crypto';

const TRUST_ATTESTATION_PEM_LABEL = 'SPARK TRUST ATTESTATION';

export interface TrustAttestationRow {
  subjectA: string;
  subjectB: string;
  settledAmount: string;
  settlementCount: number;
  timestamp: Date;
  signature: string;
}

function toIso8601(date: Date): string {
  return `${date.toISOString().split('.')[0]}Z`;
}

/**
 * Wraps a TrustAttestation row in its PEM envelope. Shared by GET /trust/attestations,
 * GET /merchant/{id}/trust, and GET /sync/updates so all three build the exact same envelope from
 * the same row shape. Does NOT sign anything — the row's `signature` is already valid for its
 * current field values (src/settlement/trustEdges.ts re-signs on every write), so this just
 * reconstructs the exact canonical object that was signed and wraps it.
 */
export function buildAttestationPem(row: TrustAttestationRow): string {
  const attestation: Record<string, JsonValue> = {
    subject_a: row.subjectA,
    subject_b: row.subjectB,
    settled_amount: row.settledAmount,
    settlement_count: row.settlementCount,
    timestamp: toIso8601(row.timestamp),
    signature: row.signature,
  };
  return pemEncode(TRUST_ATTESTATION_PEM_LABEL, canonicalizeFull(attestation));
}

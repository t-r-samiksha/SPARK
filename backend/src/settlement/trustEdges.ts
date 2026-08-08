import { Prisma } from '@prisma/client';
import { canonicalizeForSigning, ed25519Sign } from '../crypto';
import { bankSigningKeySeed } from '../config';

// Trust edges (docs/trust-attestation-format.md) — the underlying edge in the trust graph used
// for merchant reputation and offline spend-limit recommendations, per the Phase 8 spec's bounded
// (<=3 hop), decaying-weight trust chain design. This module is the ONLY place trust edges are
// written — always called from the settlement engine's persist phase (src/settlement/engine.ts),
// only for transactions that end up `accepted`. This is the Sybil-resistance property: trust is
// anchored to SETTLED transaction history only, never a client-supplied claim or an
// unsettled/rejected/flagged transaction.

/** ISO 8601 UTC, `YYYY-MM-DDTHH:MM:SSZ` — trust_attestation.timestamp is one of the two
 * documented exceptions to the epoch-seconds default (docs/id-conventions.md#timestamps). */
function toIso8601(date: Date): string {
  return `${date.toISOString().split('.')[0]}Z`;
}

/** Canonical (lexicographic) ordering for a symmetric edge — see the JUDGMENT CALL comment on
 * the TrustAttestation model in schema.prisma for why this edge is symmetric, not directional. */
function canonicalPair(deviceA: string, deviceB: string): [string, string] {
  return deviceA < deviceB ? [deviceA, deviceB] : [deviceB, deviceA];
}

/**
 * Creates or updates the trust edge between two devices after a transaction between them
 * SETTLES. Must run inside the same DB transaction as persisting the settled Transaction row
 * (the `tx` param is a Prisma.TransactionClient, not the module-level client), so a crash between
 * the two can never leave a settled transaction with no corresponding trust update, or vice versa.
 *
 * Re-signs the edge's attestation on every call (write-time signing, not read-time) — see the
 * schema.prisma comment on the `timestamp` field for why: this keeps the stored `signature`
 * always valid for the row's current field values, so GET /trust/attestations and
 * GET /sync/updates can serve it straight from storage with no signing work at read time.
 */
export async function upsertTrustEdge(
  tx: Prisma.TransactionClient,
  deviceAId: string,
  deviceBId: string,
  amount: string,
): Promise<void> {
  const [subjectA, subjectB] = canonicalPair(deviceAId, deviceBId);
  const now = new Date();

  const existing = await tx.trustAttestation.findUnique({
    where: { subjectA_subjectB: { subjectA, subjectB } },
  });

  const settledAmount = existing ? (BigInt(existing.settledAmount) + BigInt(amount)).toString() : amount;
  const settlementCount = existing ? existing.settlementCount + 1 : 1;

  const unsigned = {
    subject_a: subjectA,
    subject_b: subjectB,
    settled_amount: settledAmount,
    settlement_count: settlementCount,
    timestamp: toIso8601(now),
  };
  const signature = ed25519Sign(bankSigningKeySeed(), canonicalizeForSigning(unsigned));

  if (existing) {
    await tx.trustAttestation.update({
      where: { id: existing.id },
      data: { settledAmount, settlementCount, timestamp: now, lastSettledAt: now, signature },
    });
  } else {
    await tx.trustAttestation.create({
      data: { subjectA, subjectB, settledAmount, settlementCount, timestamp: now, lastSettledAt: now, signature },
    });
  }
}

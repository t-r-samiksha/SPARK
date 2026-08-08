import { prisma } from '../db/client';
import { Certificate, Transaction } from '../types';

// A double-spend is exactly two DIFFERENT tx_ids claiming the same (token_id, device_counter)
// slot. Both are validly signed by the same device (verified before this module is ever
// consulted — see engine.ts), which is what makes it self-incriminating: no additional proof is
// needed beyond the two conflicting signed objects themselves.

export interface CandidateTx {
  transaction: Transaction;
  payerCert: Certificate;
}

function slotKey(tokenId: string, deviceCounter: number): string {
  return `${tokenId}:${deviceCounter}`;
}

/**
 * Groups candidates by (token_id, device_counter) slot and returns every slot claimed by more
 * than one DISTINCT tx_id within this batch. A slot hit twice by the exact same tx_id (a relay
 * duplicate uploaded twice in one batch) is not a conflict and is excluded.
 */
export function findWithinBatchConflicts(candidates: CandidateTx[]): Map<string, CandidateTx[]> {
  const bySlot = new Map<string, CandidateTx[]>();
  for (const candidate of candidates) {
    const key = slotKey(candidate.transaction.token_id, candidate.transaction.device_counter);
    const existing = bySlot.get(key);
    if (existing) {
      existing.push(candidate);
    } else {
      bySlot.set(key, [candidate]);
    }
  }

  const conflicts = new Map<string, CandidateTx[]>();
  for (const [key, group] of bySlot) {
    const distinctTxIds = new Set(group.map((c) => c.transaction.tx_id));
    if (distinctTxIds.size > 1) {
      conflicts.set(key, group);
    }
  }
  return conflicts;
}

/**
 * Checks whether a candidate's (token_id, device_counter) slot is already claimed by a
 * DIFFERENT, previously-persisted (settled) transaction — a double-spend against history rather
 * than within the current batch.
 */
export async function findHistoryConflict(candidate: CandidateTx): Promise<{ txId: string } | null> {
  const existing = await prisma.transaction.findUnique({
    where: {
      tokenId_deviceCounter: {
        tokenId: candidate.transaction.token_id,
        deviceCounter: candidate.transaction.device_counter,
      },
    },
  });
  if (existing && existing.txId !== candidate.transaction.tx_id) {
    return { txId: existing.txId };
  }
  return null;
}

/**
 * Revokes a certificate by serial number (the RevokedCertificate table, which is also the CRL
 * source for GET /api/v1/sync/updates) and mirrors the revocation onto the Device row's
 * revokedAt/revokedReason — what POST /api/v1/auth/verify actually checks — so a revoked device
 * can't obtain a new session either. Upserts, so revoking an already-revoked serial is never an
 * error — but it's NOT a no-op on repeat calls: `reason` is last-write-wins on both rows (e.g.
 * double-spend auto-revokes a device, then an admin revokes the same device via
 * POST /api/v1/admin/revoke with a different reason, or vice versa — whichever call happened
 * last should be reflected consistently everywhere, not have RevokedCertificate.reason and
 * Device.revokedReason silently diverge). `revokedAt` on RevokedCertificate is deliberately NOT
 * bumped on repeat calls, unlike Device.revokedAt: it means "when this cert first became
 * untrusted," which shouldn't move just because it was revoked again for a different reason —
 * Device.revokedAt's own bump-every-time behavior is about a different question ("was this
 * device revoked, and how recently") and is unaffected by this fix.
 */
export async function revokeCertificate(params: {
  certSerial: string;
  deviceId: string;
  reason: string;
}): Promise<void> {
  await prisma.revokedCertificate.upsert({
    where: { certSerial: params.certSerial },
    create: { certSerial: params.certSerial, reason: params.reason },
    update: { reason: params.reason },
  });

  // updateMany (not update): the Device row may not exist for this device_id in principle, and
  // this revocation must not fail just because the mirror has nothing to update.
  await prisma.device.updateMany({
    where: { deviceId: params.deviceId },
    data: { revokedAt: new Date(), revokedReason: params.reason },
  });
}

export interface RecordedIncident {
  id: string;
  tokenId: string;
  deviceId: string;
  txIdA: string;
  txIdB: string;
  detectedAt: Date;
}

/** Persists a DoubleSpendIncident and revokes the offending device's certificate. */
export async function recordDoubleSpendIncident(params: {
  tokenId: string;
  deviceId: string;
  certSerial: string;
  txIdA: string;
  txIdB: string;
}): Promise<RecordedIncident> {
  const incident = await prisma.doubleSpendIncident.create({
    data: {
      tokenId: params.tokenId,
      deviceId: params.deviceId,
      txIdA: params.txIdA,
      txIdB: params.txIdB,
    },
  });

  await revokeCertificate({
    certSerial: params.certSerial,
    deviceId: params.deviceId,
    reason: `double-spend detected: token_id=${params.tokenId}, tx_id_a=${params.txIdA}, tx_id_b=${params.txIdB}`,
  });

  return incident;
}

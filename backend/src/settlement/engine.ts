import { prisma } from '../db/client';
import { canonicalizeForSigning, canonicalizeFull, ed25519Verify, JsonValue, sha256Base64url } from '../crypto';
import { Transaction } from '../types';
import { verifyPayerCertificate } from './verifyCertificate';
import {
  CandidateTx,
  findHistoryConflict,
  findWithinBatchConflicts,
  recordDoubleSpendIncident,
} from './doubleSpendResolver';
import { upsertTrustEdge } from './trustEdges';

// Settlement engine for POST /api/v1/sync/transactions. Orchestrates, per
// docs/purse-token-format.md#spend-enforcement-decided and docs/api-contract.md's
// /sync/transactions description:
//
//   1. Idempotency (relay duplicates short-circuit before any other check — see the note below).
//   2. Per-transaction static validation: payer cert trust chain, transaction signature, token
//      existence/expiry/ownership, per-transaction cap.
//   3. Double-spend detection (within this batch, and against previously-settled history).
//   4. Per-token_id counter continuity + prev_tx_hash chaining + aggregate value check.
//   5. Persisting whatever's left standing, and recording any double-spend incidents.

export interface TxSettlementResult {
  tx_id: string;
  status: 'accepted' | 'rejected' | 'duplicate';
  reason?: string;
}

export interface IncidentSummary {
  id: string;
  token_id: string;
  device_id: string;
  tx_id_a: string;
  tx_id_b: string;
  /** Unix epoch seconds. Not part of any docs/*.md wire format (this is a purely internal
   * admin/audit record), so it defaults to the project's stated default per
   * docs/id-conventions.md#timestamps rather than inventing a third timestamp convention. */
  detected_at: number;
}

export interface SettlementBatchResult {
  results: TxSettlementResult[];
  incidents: IncidentSummary[];
}

export interface PersistedTransactionRow {
  txId: string;
  tokenId: string;
  amount: string;
  payerDeviceId: string;
  payerAccountId: string;
  payerCert: string;
  payeeDeviceId: string;
  payeeAccountId: string;
  payeeCert: string;
  deviceCounter: number;
  prevTxHash: string | null;
  timestamp: number;
  signature: string;
}

// Exported so src/api/escrow/routes.ts can extend the SAME device_counter/prev_tx_hash chain an
// escrow settlement's token_id already has from normal synced transactions — see that file for
// why an escrow-settled transaction needs to participate in this chain at all.
export function reconstructTransaction(row: PersistedTransactionRow): Transaction {
  return {
    tx_id: row.txId,
    token_id: row.tokenId,
    amount: row.amount,
    payer: { device_id: row.payerDeviceId, account_id: row.payerAccountId, cert: row.payerCert },
    payee: { device_id: row.payeeDeviceId, account_id: row.payeeAccountId, cert: row.payeeCert },
    device_counter: row.deviceCounter,
    prev_tx_hash: row.prevTxHash,
    timestamp: row.timestamp,
    signature: row.signature,
  };
}

/**
 * JUDGMENT CALL: transaction-format.md says prev_tx_hash is "SHA-256 hash of the canonical
 * serialization of the payer's previous transaction" without saying whether that includes the
 * previous transaction's own `signature` field. We hash the FULL object (signature included),
 * matching the convention canonical-serialization.md already establishes for PEM envelopes:
 * "the entire object including its signature field, since by that point the object is finished."
 * If Android implements this differently, hash-chain checks below will spuriously reject —
 * flag and reconcile before this depends on real device-generated chains.
 */
// Exported so tests/helpers/makeSignedTx.ts computes chained prev_tx_hash values with the exact
// same logic the engine itself uses to verify them — duplicating this in test code would risk the
// two silently drifting apart.
export function hashTransaction(tx: Transaction): string {
  // Cast, not spread: Transaction has nested typed objects (payer/payee), and a shallow spread
  // only makes the *outer* object a fresh literal — the nested ones are still references to
  // TransactionParty, which TypeScript still rejects against the JsonValue index signature. See
  // the note in crypto/canonical.ts.
  return sha256Base64url(canonicalizeFull(tx as unknown as Record<string, JsonValue>));
}

function txResult(tx_id: string, status: TxSettlementResult['status'], reason?: string): TxSettlementResult {
  return reason ? { tx_id, status, reason } : { tx_id, status };
}

export async function settleTransactionBatch(transactions: Transaction[]): Promise<SettlementBatchResult> {
  const results: TxSettlementResult[] = new Array(transactions.length);
  const incidents: IncidentSummary[] = [];

  // --- Phase 1: idempotency -------------------------------------------------------------------
  // Checked first, before any other verification: a resend of an already-settled transaction
  // should always succeed as "duplicate" regardless of the device's *current* state (e.g. even
  // if the device has since been revoked for an unrelated reason) — we're not creating any new
  // state, just acknowledging we already have it. This is a deliberate reordering relative to the
  // task's step list (which lists idempotency after the per-transaction checks); doing it first
  // is both cheaper (skips redundant signature/cert verification) and avoids retroactively
  // failing a resend of a transaction that was legitimately settled in the past.
  const seenInThisBatch = new Map<string, string>(); // tx_id -> signature, first occurrence only
  const pendingIndices: number[] = []; // indices into `transactions` still needing processing

  for (let i = 0; i < transactions.length; i++) {
    const tx = transactions[i];
    const priorSignatureInBatch = seenInThisBatch.get(tx.tx_id);
    if (priorSignatureInBatch !== undefined) {
      results[i] =
        priorSignatureInBatch === tx.signature
          ? txResult(tx.tx_id, 'duplicate', 'relay duplicate within this batch')
          : txResult(tx.tx_id, 'duplicate', 'tx_id reused with a different signature within this batch — treated as duplicate/replay, not an update (see docs/id-conventions.md#uuids)');
      continue;
    }
    seenInThisBatch.set(tx.tx_id, tx.signature);

    const existing = await prisma.transaction.findUnique({ where: { txId: tx.tx_id } });
    if (existing) {
      results[i] =
        existing.signature === tx.signature
          ? txResult(tx.tx_id, 'duplicate', 'already settled')
          : txResult(tx.tx_id, 'duplicate', 'tx_id reused with a different signature than the settled transaction — treated as duplicate/replay, not an update (see docs/id-conventions.md#uuids)');
      continue;
    }

    pendingIndices.push(i);
  }

  // --- Phase 2: per-transaction static validation ---------------------------------------------
  // a. payer cert trust (Bank Root CA signature, validity window, not revoked, bound to
  //    payer.device_id) — b is folded into this same check (verifyPayerCertificate consults
  //    RevokedCertificate).
  // c/d. token_id refers to a real, non-expired PurseToken owned by the payer, and amount doesn't
  //    exceed that token's cap.
  const candidates: CandidateTx[] = [];
  const candidateIndex = new Map<string, number>(); // tx_id -> index into `transactions`/`results`
  const candidateToken = new Map<string, { deviceId: string; value: string; cap: string; expiry: number; counterStart: number }>();

  for (const i of pendingIndices) {
    const tx = transactions[i];

    const certCheck = await verifyPayerCertificate(tx.payer.cert, tx.payer.device_id);
    if (!certCheck.valid) {
      results[i] = txResult(tx.tx_id, 'rejected', certCheck.reason);
      continue;
    }

    // Cast, not spread — see the note in hashTransaction() above.
    const signingBytes = canonicalizeForSigning(tx as unknown as Record<string, JsonValue>);
    if (!ed25519Verify(certCheck.cert.device_public_key, signingBytes, tx.signature)) {
      results[i] = txResult(tx.tx_id, 'rejected', 'transaction signature is not valid for the payer device key');
      continue;
    }

    const token = await prisma.purseToken.findUnique({ where: { tokenId: tx.token_id } });
    if (!token) {
      results[i] = txResult(tx.tx_id, 'rejected', 'token_id does not refer to a known purse token');
      continue;
    }
    if (Math.floor(Date.now() / 1000) > token.expiry) {
      results[i] = txResult(tx.tx_id, 'rejected', 'purse token has expired');
      continue;
    }
    if (token.deviceId !== tx.payer.device_id) {
      results[i] = txResult(tx.tx_id, 'rejected', 'token_id does not belong to the payer device');
      continue;
    }
    if (BigInt(tx.amount) > BigInt(token.cap)) {
      results[i] = txResult(tx.tx_id, 'rejected', `amount (${tx.amount}) exceeds the token's cap (${token.cap})`);
      continue;
    }

    // TODO (Phase 9, disaster-mode essential_only — deliberately NOT implemented): if an active
    // DisasterEvent has essential_only=true (see disasterContext.ts), transactions during it are
    // meant to be restricted to "essential" purchases only. This can't be enforced here: nothing
    // in the schema classifies a transaction, a payee, or a merchant account as
    // essential/non-essential — that needs Member C's merchant categorization first. Faking this
    // check (e.g. accepting everything, or rejecting everything) would be worse than leaving it
    // absent, so this is intentionally a no-op rather than a guess.

    candidates.push({ transaction: tx, payerCert: certCheck.cert });
    candidateIndex.set(tx.tx_id, i);
    candidateToken.set(tx.token_id, {
      deviceId: token.deviceId,
      value: token.value,
      cap: token.cap,
      expiry: token.expiry,
      counterStart: token.counterStart,
    });
  }

  // --- Phase 3: double-spend detection ---------------------------------------------------------
  const withinBatchConflicts = findWithinBatchConflicts(candidates);
  const conflictedTxIds = new Set<string>();

  for (const group of withinBatchConflicts.values()) {
    const ordered = [...group].sort(
      (a, b) => candidateIndex.get(a.transaction.tx_id)! - candidateIndex.get(b.transaction.tx_id)!,
    );
    const [first, second] = ordered;

    // Simplification for groups of 3+ conflicting tx_ids at the same slot (not expected in
    // practice, and not part of the required test scenarios): record one incident against the
    // first two, but reject every member of the group.
    const incident = await recordDoubleSpendIncident({
      tokenId: first.transaction.token_id,
      deviceId: first.transaction.payer.device_id,
      certSerial: first.payerCert.serial_number,
      txIdA: first.transaction.tx_id,
      txIdB: second.transaction.tx_id,
    });
    incidents.push({
      id: incident.id,
      token_id: incident.tokenId,
      device_id: incident.deviceId,
      tx_id_a: incident.txIdA,
      tx_id_b: incident.txIdB,
      detected_at: Math.floor(incident.detectedAt.getTime() / 1000),
    });

    for (const member of ordered) {
      const idx = candidateIndex.get(member.transaction.tx_id)!;
      const others = ordered.filter((m) => m.transaction.tx_id !== member.transaction.tx_id).map((m) => m.transaction.tx_id);
      results[idx] = txResult(
        member.transaction.tx_id,
        'rejected',
        `double-spend: same (token_id, device_counter) slot as tx_id ${others[0]}`,
      );
      conflictedTxIds.add(member.transaction.tx_id);
    }
  }

  const cleanCandidates: CandidateTx[] = [];
  for (const candidate of candidates) {
    if (conflictedTxIds.has(candidate.transaction.tx_id)) {
      continue;
    }
    const historyConflict = await findHistoryConflict(candidate);
    if (historyConflict) {
      const idx = candidateIndex.get(candidate.transaction.tx_id)!;

      if (historyConflict.isEscrowSettlement) {
        // NOT fraud: this device didn't sign two conflicting transactions — an escrow settlement
        // (POST /escrow/release) advanced this token's device_counter/prev_tx_hash chain
        // server-side, and the device's local ledger doesn't know that yet, so it (correctly, by
        // its own stale knowledge) reused a counter that's no longer free. No incident, no
        // revocation — just a clean rejection telling it to catch up. See GET /sync/updates'
        // escrow_settlements field for how the device is meant to learn the real chain state and
        // re-chain its next transaction on top of it.
        results[idx] = txResult(
          candidate.transaction.tx_id,
          'rejected',
          `stale counter: (token_id, device_counter=${candidate.transaction.device_counter}) was already settled via escrow (tx_id ${historyConflict.txId}) — call GET /sync/updates to learn the current chain state and re-sign on top of it`,
        );
        conflictedTxIds.add(candidate.transaction.tx_id);
        continue;
      }

      const incident = await recordDoubleSpendIncident({
        tokenId: candidate.transaction.token_id,
        deviceId: candidate.transaction.payer.device_id,
        certSerial: candidate.payerCert.serial_number,
        txIdA: historyConflict.txId,
        txIdB: candidate.transaction.tx_id,
      });
      incidents.push({
        id: incident.id,
        token_id: incident.tokenId,
        device_id: incident.deviceId,
        tx_id_a: incident.txIdA,
        tx_id_b: incident.txIdB,
        detected_at: Math.floor(incident.detectedAt.getTime() / 1000),
      });
      results[idx] = txResult(
        candidate.transaction.tx_id,
        'rejected',
        `double-spend: same (token_id, device_counter) slot as previously-settled tx_id ${historyConflict.txId}`,
      );
      conflictedTxIds.add(candidate.transaction.tx_id);
      continue;
    }
    cleanCandidates.push(candidate);
  }

  // --- Phase 4: per-token_id counter continuity + prev_tx_hash chain + aggregate value ---------
  // Per docs/purse-token-format.md#spend-enforcement-decided: the completeness check (gapless
  // device_counter, correct prev_tx_hash chaining) must hold before the aggregate sum(amount) <=
  // token.value check can be trusted — a gap means we can't be sure we've seen the token's
  // complete spend history, so we can't trust the sum.
  //
  // JUDGMENT CALL (flagged, not fully resolved by the docs): device_counter is treated here as
  // scoped PER PURSE TOKEN (expected sequence: counter_start, counter_start+1, ...), not a single
  // ever-increasing counter per device across every purse it has ever held. transaction-format.md
  // explicitly lists this as an open question ("whether device_counter restarts per token_id or
  // is a single ever-increasing counter per device across all purses"). Per-token is what makes
  // this implementable without needing to correlate a device's entire multi-purse history, and is
  // consistent with counter_start's own per-token phrasing in purse-token-format.md ("the
  // device_counter value at which THIS purse token became valid"). If "single ever-increasing
  // per device" is confirmed instead, this whole phase needs to key off device_id, not token_id.
  const candidatesByToken = new Map<string, CandidateTx[]>();
  for (const candidate of cleanCandidates) {
    const list = candidatesByToken.get(candidate.transaction.token_id);
    if (list) {
      list.push(candidate);
    } else {
      candidatesByToken.set(candidate.transaction.token_id, [candidate]);
    }
  }

  const accepted: CandidateTx[] = [];

  for (const [tokenId, tokenCandidates] of candidatesByToken) {
    const token = candidateToken.get(tokenId)!;

    const settledRows = await prisma.transaction.findMany({
      where: { tokenId },
      orderBy: { deviceCounter: 'asc' },
    });

    let settledSum = settledRows.reduce((sum, row) => sum + BigInt(row.amount), 0n);
    let nextExpectedCounter =
      settledRows.length > 0 ? settledRows[settledRows.length - 1].deviceCounter + 1 : token.counterStart;
    let lastHash: string | null =
      settledRows.length > 0 ? hashTransaction(reconstructTransaction(settledRows[settledRows.length - 1])) : null;

    const sorted = [...tokenCandidates].sort((a, b) => a.transaction.device_counter - b.transaction.device_counter);
    let sequenceBroken = false;

    for (const candidate of sorted) {
      const idx = candidateIndex.get(candidate.transaction.tx_id)!;

      if (sequenceBroken) {
        results[idx] = txResult(
          candidate.transaction.tx_id,
          'rejected',
          'skipped: an earlier transaction in this token\'s sequence broke counter/hash continuity in this batch',
        );
        continue;
      }

      if (candidate.transaction.device_counter !== nextExpectedCounter) {
        results[idx] = txResult(
          candidate.transaction.tx_id,
          'rejected',
          `device_counter gap: expected ${nextExpectedCounter}, got ${candidate.transaction.device_counter}`,
        );
        sequenceBroken = true;
        continue;
      }

      if (candidate.transaction.prev_tx_hash !== lastHash) {
        results[idx] = txResult(
          candidate.transaction.tx_id,
          'rejected',
          'prev_tx_hash does not match the expected chain for this token',
        );
        sequenceBroken = true;
        continue;
      }

      const newSum = settledSum + BigInt(candidate.transaction.amount);
      if (newSum > BigInt(token.value)) {
        results[idx] = txResult(
          candidate.transaction.tx_id,
          'rejected',
          `accepting this transaction would exceed the token's signed value (${token.value})`,
        );
        sequenceBroken = true;
        continue;
      }

      results[idx] = txResult(candidate.transaction.tx_id, 'accepted');
      accepted.push(candidate);
      settledSum = newSum;
      nextExpectedCounter += 1;
      lastHash = hashTransaction(candidate.transaction);
    }
  }

  // --- Phase 5: persist + update trust edges ----------------------------------------------------
  // Interactive transaction (not the array form used before Phase 8): each accepted transaction
  // needs a persisted row AND a trust-edge upsert, and the edge upsert is read-then-write logic
  // (look up the existing edge, then create or update) that can't be expressed as a flat array of
  // independent operations. Both happen atomically per transaction, and the whole batch is one DB
  // transaction — a crash partway through can never leave a settled transaction without its trust
  // update, or a trust update without the transaction that justified it.
  if (accepted.length > 0) {
    await prisma.$transaction(async (tx) => {
      for (const { transaction: settledTx } of accepted) {
        await tx.transaction.create({
          data: {
            txId: settledTx.tx_id,
            tokenId: settledTx.token_id,
            amount: settledTx.amount,
            payerDeviceId: settledTx.payer.device_id,
            payerAccountId: settledTx.payer.account_id,
            payerCert: settledTx.payer.cert,
            payeeDeviceId: settledTx.payee.device_id,
            payeeAccountId: settledTx.payee.account_id,
            payeeCert: settledTx.payee.cert,
            deviceCounter: settledTx.device_counter,
            prevTxHash: settledTx.prev_tx_hash,
            timestamp: settledTx.timestamp,
            signature: settledTx.signature,
          },
        });

        // Trust edges are written HERE ONLY, for transactions that just settled — never for
        // rejected/duplicate/flagged ones. See trustEdges.ts for the Sybil-resistance rationale.
        await upsertTrustEdge(tx, settledTx.payer.device_id, settledTx.payee.device_id, settledTx.amount);
      }
    });
  }

  return { results, incidents };
}

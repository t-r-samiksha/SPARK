import { prisma } from './db.ts';
import {
  AMOUNT_ANOMALY_SATURATION,
  CONCENTRATION_SATURATION,
  FRAUD_FLAG_THRESHOLD,
  FRAUD_MODEL_VERSION,
  MATERIAL_SIGNAL_FLOOR,
  MIN_CORROBORATING_SIGNALS,
  MIN_HISTORY_FOR_ANOMALY,
  STANDALONE_SIGNALS,
  STANDALONE_SIGNAL_THRESHOLD,
  SYNC_BAD_SECONDS,
  VELOCITY_SATURATION_COUNT,
} from './config.ts';

/**
 * Fraud intelligence.
 *
 * IMPORTANT — this is an original design decision, not an implementation of a written spec.
 * docs/api-contract.md defines only `type: "fraud_flag"` and says the shape is "TBD once
 * fraud-detection logic exists"; nothing in the repo states what fraud means for SPARK. The four
 * signals below were chosen for what an offline-first payment network can actually observe, and
 * the whole design is open to the team's revision.
 *
 * Deliberately NOT included: anything derived from a device's account holder, location, or
 * transaction *counterparty identity*. A flag here should be answerable by "this device's own
 * behaviour changed", so an operator reviewing it is not asked to act on a proxy for who someone
 * is.
 *
 * A flag is a prompt to look, never an automatic action. Nothing in this module revokes,
 * blocks, or writes — it returns incidents for a human to review in the console.
 */

export interface FraudReason {
  key: string;
  label: string;
  /** Normalised [0,1] — higher is more suspicious. */
  score: number;
  detail: string;
}

export interface FraudFlagIncident {
  type: 'fraud_flag';
  id: string;
  device_id: string;
  /** Composite suspicion [0,1]. */
  score: number;
  reasons: FraudReason[];
  detected_at: number;
  model_version: string;
}

const clamp01 = (n: number): number => Math.min(1, Math.max(0, n));

function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[(sorted.length - 1) >> 1] ?? 0;
}

export interface Tx {
  amount: string;
  timestamp: number;
  syncedAt: Date;
  payeeDeviceId: string;
}

/**
 * Scores one device's recent behaviour. Returns null when the device is not suspicious enough to
 * surface — the console should show real signal, not every device with a score attached.
 */
export function scoreDevice(
  deviceId: string,
  txs: Tx[],
  incomingFrom: Map<string, number>,
  now: number,
): FraudFlagIncident | null {
  if (txs.length === 0) return null;

  const reasons: FraudReason[] = [];

  // ---- 1. Spend velocity ------------------------------------------------------------------
  const dayAgo = now - 86_400;
  const recent = txs.filter((t) => t.timestamp >= dayAgo);
  const velocityScore = clamp01(recent.length / VELOCITY_SATURATION_COUNT);
  if (velocityScore > 0) {
    reasons.push({
      key: 'velocity',
      label: 'Spend velocity',
      score: velocityScore,
      detail: `${recent.length} transaction${recent.length === 1 ? '' : 's'} in the last 24h.`,
    });
  }

  // ---- 2. Offline duration ----------------------------------------------------------------
  // The window between spending offline and settling is exactly the window in which a
  // double-spend cannot be detected, so a long one is exposure regardless of intent.
  const offline = txs.map((t) => Math.max(0, Math.floor(t.syncedAt.getTime() / 1000) - t.timestamp));
  const worstOffline = Math.max(...offline);
  const offlineScore = clamp01(worstOffline / SYNC_BAD_SECONDS);
  if (offlineScore > 0) {
    reasons.push({
      key: 'offline_duration',
      label: 'Offline duration',
      score: offlineScore,
      detail: `Longest gap between offline spend and settlement: ${Math.round(worstOffline / 3600)}h.`,
    });
  }

  // ---- 3. Amount anomaly ------------------------------------------------------------------
  if (txs.length >= MIN_HISTORY_FOR_ANOMALY) {
    const amounts = txs.map((t) => Number(BigInt(t.amount)));
    const med = median(amounts);
    const largest = Math.max(...amounts);
    const ratio = med > 0 ? largest / med : 0;
    const anomalyScore = clamp01((ratio - 1) / (AMOUNT_ANOMALY_SATURATION - 1));
    if (anomalyScore > 0.15) {
      reasons.push({
        key: 'amount_anomaly',
        label: 'Amount anomaly',
        score: anomalyScore,
        detail: `Largest spend is ${ratio.toFixed(1)}× this device's median of ${Math.round(med)} paise.`,
      });
    }
  }

  // ---- 4. Circular flow -------------------------------------------------------------------
  // Concentration alone is NOT suspicious: a customer who only ever pays their local shop looks
  // identical to a ring member by that measure, and flagging them would be flagging poverty of
  // choice. What distinguishes a settlement ring is RECIPROCITY — value cycling back and forth
  // between the same devices rather than flowing one way to a merchant. So the signal requires
  // both directions, and scores on how balanced the loop is.
  if (txs.length >= MIN_HISTORY_FOR_ANOMALY) {
    const outCounts = new Map<string, number>();
    for (const t of txs) outCounts.set(t.payeeDeviceId, (outCounts.get(t.payeeDeviceId) ?? 0) + 1);

    let topPartner = '';
    let topCount = 0;
    for (const [partner, count] of outCounts) {
      if (count > topCount) {
        topCount = count;
        topPartner = partner;
      }
    }

    const share = topCount / txs.length;
    const returned = incomingFrom.get(topPartner) ?? 0;

    if (share > 0.5 && returned > 0) {
      // Balance of the loop: 1.0 when value cycles evenly in both directions.
      const reciprocity = Math.min(topCount, returned) / Math.max(topCount, returned);
      const score = clamp01((share / CONCENTRATION_SATURATION) * reciprocity);
      if (score > 0) {
        reasons.push({
          key: 'circular_flow',
          label: 'Circular flow',
          score,
          detail:
            `${Math.round(share * 100)}% of spends go to one device (${topCount} of ${txs.length}), ` +
            `and it sent ${returned} back — value is cycling, not flowing to a merchant.`,
        });
      }
    }
  }

  // Only material signals compose the score — see MATERIAL_SIGNAL_FLOOR. Immaterial ones are
  // dropped entirely rather than averaged in, so one strong signal is not hidden by noise.
  const material = reasons.filter((r) => r.score >= MATERIAL_SIGNAL_FLOOR);

  // Corroboration required: most of these signals have an innocent explanation on their own
  // (a busy market day, a phone left in a drawer, one big purchase). Two independent signals
  // agreeing is what separates suspicion from an operator chasing noise. The exception is a
  // signal that is structurally conclusive by itself — see STANDALONE_SIGNALS.
  const conclusive = material.some(
    (r) => STANDALONE_SIGNALS.has(r.key) && r.score >= STANDALONE_SIGNAL_THRESHOLD,
  );
  if (!conclusive && material.length < MIN_CORROBORATING_SIGNALS) return null;

  const score = material.reduce((sum, r) => sum + r.score, 0) / material.length;
  if (score < FRAUD_FLAG_THRESHOLD) return null;

  return {
    type: 'fraud_flag',
    // Deterministic per device+day: re-scanning must not produce a new "incident" every poll.
    id: `fraud-${deviceId}-${Math.floor(now / 86_400)}`,
    device_id: deviceId,
    score,
    reasons: material.sort((a, b) => b.score - a.score),
    detected_at: now,
    model_version: FRAUD_MODEL_VERSION,
  };
}

/** Scans every unrevoked device and returns those currently worth an operator's attention. */
export async function scanForFraud(): Promise<FraudFlagIncident[]> {
  const now = Math.floor(Date.now() / 1000);

  const devices = await prisma.device.findMany({
    where: { revokedAt: null },
    select: { deviceId: true },
  });
  if (devices.length === 0) return [];

  const txs = await prisma.transaction.findMany({
    where: { payerDeviceId: { in: devices.map((d) => d.deviceId) } },
    select: {
      payerDeviceId: true,
      payeeDeviceId: true,
      amount: true,
      timestamp: true,
      syncedAt: true,
    },
  });

  const byDevice = new Map<string, Tx[]>();
  // Incoming counts per device, keyed by who paid them — the circular-flow signal needs to know
  // whether the counterparty a device pays also pays it back.
  const incoming = new Map<string, Map<string, number>>();

  for (const t of txs) {
    const list = byDevice.get(t.payerDeviceId);
    const entry: Tx = {
      amount: t.amount,
      timestamp: t.timestamp,
      syncedAt: t.syncedAt,
      payeeDeviceId: t.payeeDeviceId,
    };
    if (list) list.push(entry);
    else byDevice.set(t.payerDeviceId, [entry]);

    let received = incoming.get(t.payeeDeviceId);
    if (!received) {
      received = new Map<string, number>();
      incoming.set(t.payeeDeviceId, received);
    }
    received.set(t.payerDeviceId, (received.get(t.payerDeviceId) ?? 0) + 1);
  }

  const flags: FraudFlagIncident[] = [];
  for (const [deviceId, deviceTxs] of byDevice) {
    const flag = scoreDevice(deviceId, deviceTxs, incoming.get(deviceId) ?? new Map(), now);
    if (flag) flags.push(flag);
  }

  return flags.sort((a, b) => b.score - a.score);
}

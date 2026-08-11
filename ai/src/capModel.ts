import { prisma } from './db.ts';
import { computeTrust } from './trustGraph.ts';
import {
  BASELINE_CAP_PAISE,
  HISTORY_SATURATION_COUNT,
  HISTORY_SATURATION_PAISE,
  INCIDENT_PENALTY_FACTOR,
  MAX_CAP_PAISE,
  MAX_MULTIPLIER,
  MIN_CAP_PAISE,
  MIN_MULTIPLIER,
  MODEL_VERSION,
  SIGNAL_WEIGHTS,
  SYNC_BAD_SECONDS,
  SYNC_GOOD_SECONDS,
  TRUST_SATURATION,
} from './config.ts';

/**
 * Adaptive offline spend-cap model.
 *
 * The cap is the amount the bank is willing to let a device spend while it cannot be checked
 * (docs/purse-token-format.md: `cap` is a hard ceiling for the token's lifetime and bounds every
 * individual spend). So this is an exposure question, not a credit question: how much do we
 * believe this device will not double-spend while offline, and how much can we absorb if it does.
 *
 * Four signals, each normalised to [0,1] and combined by the weights in config.ts. The model is
 * explainable by construction — every response carries its own decomposition, so an operator can
 * see why a device got the cap it got rather than trusting a number.
 */

export interface Signal {
  key: string;
  label: string;
  /** Normalised [0,1] — higher is safer. */
  score: number;
  /** Relative contribution to the composite. */
  weight: number;
  /** Human-readable justification, shown in the console. */
  detail: string;
}

export interface CapRecommendation {
  device_id: string;
  /** Integer paise, decimal string — the contract's required shape. */
  recommended_cap: string;
  baseline_cap: string;
  /** Composite of the signals below, [0,1]. */
  confidence: number;
  signals: Signal[];
  /** Set when an active disaster event raised the cap for this device's region. */
  disaster_override: { region_geo: string; higher_cap: string } | null;
  /** Set when the account's real balance, not the score, was the binding constraint. */
  balance_capped: boolean;
  model_version: string;
  computed_at: number;
}

const clamp01 = (n: number): number => Math.min(1, Math.max(0, n));

/** Linear ramp from `bad` (0) to `good` (1), tolerating either direction. */
function ramp(value: number, good: number, bad: number): number {
  if (good === bad) return 0;
  return clamp01((value - bad) / (good - bad));
}

export async function recommendCap(deviceId: string): Promise<CapRecommendation> {
  const device = await prisma.device.findUnique({
    where: { deviceId },
    select: { deviceId: true, accountId: true, revokedAt: true },
  });

  if (!device) {
    throw Object.assign(new Error(`Unknown device ${deviceId}`), { statusCode: 404 });
  }

  const now = Math.floor(Date.now() / 1000);

  // A revoked device is not a scoring question. Its certificate is invalid; it may hold nothing.
  if (device.revokedAt) {
    return {
      device_id: deviceId,
      recommended_cap: '0',
      baseline_cap: BASELINE_CAP_PAISE.toString(),
      confidence: 0,
      signals: [
        {
          key: 'revoked',
          label: 'Certificate revoked',
          score: 0,
          weight: 1,
          detail: `Device certificate was revoked at ${device.revokedAt.toISOString()}; no offline value may be authorised.`,
        },
      ],
      disaster_override: null,
      balance_capped: false,
      model_version: MODEL_VERSION,
      computed_at: now,
    };
  }

  const [outgoing, incidents, account, trust] = await Promise.all([
    prisma.transaction.findMany({
      where: { payerDeviceId: deviceId },
      select: { amount: true, timestamp: true, syncedAt: true },
    }),
    prisma.doubleSpendIncident.count({ where: { deviceId } }),
    prisma.account.findUnique({
      where: { id: device.accountId },
      select: { realBalance: true },
    }),
    computeTrust(deviceId),
  ]);

  // ---- Signal 1: settlement history -------------------------------------------------------
  const settledCount = outgoing.length;
  const settledPaise = outgoing.reduce((sum, t) => sum + BigInt(t.amount), 0n);
  const historyScore = clamp01(
    0.5 * ramp(settledCount, HISTORY_SATURATION_COUNT, 0) +
      0.5 * ramp(Number(settledPaise), HISTORY_SATURATION_PAISE, 0),
  );

  // ---- Signal 2: trust graph --------------------------------------------------------------
  const trustScore = clamp01(ramp(trust.weight, TRUST_SATURATION, 0));

  // ---- Signal 3: sync discipline ----------------------------------------------------------
  // Offline exposure is the gap between when a transaction happened and when the bank saw it.
  const offlineSeconds = outgoing
    .map((t) => Math.max(0, Math.floor(t.syncedAt.getTime() / 1000) - t.timestamp))
    .sort((a, b) => a - b);
  const medianOffline =
    offlineSeconds.length === 0
      ? null
      : (offlineSeconds[(offlineSeconds.length - 1) >> 1] ?? 0);
  const syncScore =
    medianOffline === null ? 0.5 : clamp01(ramp(medianOffline, SYNC_GOOD_SECONDS, SYNC_BAD_SECONDS));

  // ---- Signal 4: incident record ----------------------------------------------------------
  // Double-spend is the exact failure this cap exists to bound, so one confirmed incident is
  // most of the penalty and three erase the signal entirely.
  const incidentScore = clamp01(1 - incidents / 3);

  const signals: Signal[] = [
    {
      key: 'settlement_history',
      label: 'Settlement history',
      score: historyScore,
      weight: SIGNAL_WEIGHTS.settlement_history,
      detail:
        settledCount === 0
          ? 'No settled outgoing transactions yet — unproven device.'
          : `${settledCount} settled transaction${settledCount === 1 ? '' : 's'} totalling ${settledPaise} paise.`,
    },
    {
      key: 'trust_graph',
      label: 'Trust graph',
      score: trustScore,
      weight: SIGNAL_WEIGHTS.trust_graph,
      detail:
        trust.directCounterparties === 0
          ? 'No settled counterparties — device is not yet in the trust graph.'
          : `${trust.directCounterparties} direct counterpart${trust.directCounterparties === 1 ? 'y' : 'ies'}, ` +
            `${trust.reachedByHop.reduce((a, b) => a + b, 0)} devices within 3 hops, decayed weight ${trust.weight.toFixed(2)}.`,
    },
    {
      key: 'sync_discipline',
      label: 'Sync discipline',
      score: syncScore,
      weight: SIGNAL_WEIGHTS.sync_discipline,
      detail:
        medianOffline === null
          ? 'No sync history — scored neutral.'
          : `Median ${Math.round(medianOffline / 60)} min between offline spend and settlement.`,
    },
    {
      key: 'incident_record',
      label: 'Incident record',
      score: incidentScore,
      weight: SIGNAL_WEIGHTS.incident_record,
      detail:
        incidents === 0
          ? 'No double-spend incidents on record.'
          : `${incidents} confirmed double-spend incident${incidents === 1 ? '' : 's'} — cap additionally reduced to ` +
            `${Math.round(INCIDENT_PENALTY_FACTOR ** incidents * 100)}% by the incident penalty.`,
    },
  ];

  const confidence = signals.reduce((sum, s) => sum + s.score * s.weight, 0);

  // Map [0,1] confidence onto the multiplier band, then apply it to the baseline.
  const multiplier = MIN_MULTIPLIER + confidence * (MAX_MULTIPLIER - MIN_MULTIPLIER);
  let cap = (BASELINE_CAP_PAISE * BigInt(Math.round(multiplier * 1000))) / 1000n;

  // Confirmed double-spend outranks the history that preceded it — see INCIDENT_PENALTY_FACTOR.
  if (incidents > 0) {
    const penalty = INCIDENT_PENALTY_FACTOR ** incidents;
    cap = (cap * BigInt(Math.round(penalty * 1000))) / 1000n;
  }

  if (cap < MIN_CAP_PAISE) cap = MIN_CAP_PAISE;
  if (cap > MAX_CAP_PAISE) cap = MAX_CAP_PAISE;

  // The bank cannot authorise more offline value than the account actually holds.
  let balanceCapped = false;
  const realBalance = account ? BigInt(account.realBalance) : null;
  if (realBalance !== null && realBalance < cap) {
    cap = realBalance < 0n ? 0n : realBalance;
    balanceCapped = true;
  }

  // An active disaster event raises the ceiling for humanitarian transactions — a deliberate,
  // operator-authorised exception to the score, not a model output.
  const disaster = await prisma.disasterEvent.findFirst({
    where: { active: true, higherCap: { not: null } },
    orderBy: { startedAt: 'desc' },
    select: { regionGeo: true, higherCap: true },
  });

  let disasterOverride: CapRecommendation['disaster_override'] = null;
  if (disaster?.higherCap) {
    const elevated = BigInt(disaster.higherCap);
    disasterOverride = { region_geo: disaster.regionGeo, higher_cap: disaster.higherCap };
    if (elevated > cap) cap = elevated;
  }

  return {
    device_id: deviceId,
    recommended_cap: cap.toString(),
    baseline_cap: BASELINE_CAP_PAISE.toString(),
    confidence,
    signals,
    disaster_override: disasterOverride,
    balance_capped: balanceCapped,
    model_version: MODEL_VERSION,
    computed_at: now,
  };
}

/**
 * Network-wide default for callers with no device in hand — the shape
 * GET /api/v1/limit/recommendation has to answer when it is asked nothing. Uses the median of
 * per-device recommendations so the "default" reflects the actual fleet rather than a constant.
 */
export async function recommendDefaultCap(): Promise<CapRecommendation> {
  const devices = await prisma.device.findMany({
    where: { revokedAt: null },
    select: { deviceId: true },
    take: 200,
  });

  const now = Math.floor(Date.now() / 1000);

  if (devices.length === 0) {
    return {
      device_id: 'network-default',
      recommended_cap: BASELINE_CAP_PAISE.toString(),
      baseline_cap: BASELINE_CAP_PAISE.toString(),
      confidence: 0,
      signals: [
        {
          key: 'no_fleet',
          label: 'No enrolled devices',
          score: 0,
          weight: 1,
          detail: 'No unrevoked devices are enrolled, so the network default falls back to the baseline cap.',
        },
      ],
      disaster_override: null,
      balance_capped: false,
      model_version: MODEL_VERSION,
      computed_at: now,
    };
  }

  const caps = (await Promise.all(devices.map((d) => recommendCap(d.deviceId))))
    .map((r) => BigInt(r.recommended_cap))
    .sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));

  const median = caps[(caps.length - 1) >> 1] ?? BASELINE_CAP_PAISE;

  return {
    device_id: 'network-default',
    recommended_cap: median.toString(),
    baseline_cap: BASELINE_CAP_PAISE.toString(),
    confidence: 0.5,
    signals: [
      {
        key: 'fleet_median',
        label: 'Fleet median',
        score: 0.5,
        weight: 1,
        detail: `Median recommendation across ${devices.length} unrevoked device${devices.length === 1 ? '' : 's'}.`,
      },
    ],
    disaster_override: null,
    balance_capped: false,
    model_version: MODEL_VERSION,
    computed_at: now,
  };
}

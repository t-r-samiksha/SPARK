/**
 * Model constants. Every bound here is a policy decision, not a tuned parameter — there is no
 * training data yet, so the model is an explainable rules-and-weights scorer rather than a
 * learned one. Each value is stated once, here, so a reviewer can argue with the policy without
 * reading the scoring code.
 */

/** Baseline cap in integer paise (₹2,000) — the flat value the backend stub returned for everyone. */
export const BASELINE_CAP_PAISE = 200_000n;

/** Hard floor: a brand-new, unproven device can still transact at ₹500. */
export const MIN_CAP_PAISE = 50_000n;

/** Hard ceiling: no offline cap exceeds ₹10,000 regardless of score, because an offline cap is
 *  unrecoverable exposure until the device syncs. */
export const MAX_CAP_PAISE = 1_000_000n;

/** Score multiplier bounds applied to the baseline. */
export const MIN_MULTIPLIER = 0.25;
export const MAX_MULTIPLIER = 3.0;

/** Trust-graph traversal: bounded hop count, per docs/trust-attestation-format.md and the
 *  bounded/decaying design referenced in backend/src/settlement/trustEdges.ts. */
export const MAX_TRUST_HOPS = 3;

/** Weight retained per additional hop — a 3rd-hop endorsement is worth 25% of a direct one. */
export const TRUST_HOP_DECAY = 0.5;

/** Settlement volume (paise) at which the history signal saturates (₹50,000 settled). */
export const HISTORY_SATURATION_PAISE = 5_000_000;

/** Settlement count at which the history signal saturates. */
export const HISTORY_SATURATION_COUNT = 40;

/** Trust weight at which the trust signal saturates. */
export const TRUST_SATURATION = 25;

/** Sync discipline: offline seconds at or below this score full marks (1 hour). */
export const SYNC_GOOD_SECONDS = 3_600;

/** Offline seconds at or beyond which sync discipline scores zero (7 days). */
export const SYNC_BAD_SECONDS = 604_800;

/** Relative contribution of each signal to the composite score. Must sum to 1. */
export const SIGNAL_WEIGHTS = {
  settlement_history: 0.35,
  trust_graph: 0.3,
  sync_discipline: 0.2,
  incident_record: 0.15,
} as const;

/**
 * Multiplicative penalty per confirmed double-spend incident, applied AFTER the weighted score.
 *
 * A weighted signal alone is not enough here: a device with long history and strong trust can
 * out-score its own incident record, which produced the perverse result that a device with a
 * confirmed double-spend was offered a higher offline cap than a device with no history at all.
 * Proven abuse of the exact failure this cap exists to bound must dominate the evidence that
 * preceded it, so each incident halves the cap.
 */
export const INCIDENT_PENALTY_FACTOR = 0.5;

/** Fraud scoring: a device at or above this composite score is flagged. */
export const FRAUD_FLAG_THRESHOLD = 0.6;

/** Velocity: transactions in a 24h window that count as a saturated velocity signal. */
export const VELOCITY_SATURATION_COUNT = 15;

/** Amount anomaly: multiple of a device's median spend that saturates the signal. */
export const AMOUNT_ANOMALY_SATURATION = 8;

/** Counterparty concentration: share of transactions with a single counterparty that saturates. */
export const CONCENTRATION_SATURATION = 0.8;

/** Minimum transactions before concentration/anomaly signals are meaningful. */
export const MIN_HISTORY_FOR_ANOMALY = 4;

/**
 * A signal must reach this score to count toward the composite at all.
 *
 * Without it, a signal that technically fired but scored near zero (every device has *some*
 * offline gap) drags down the mean and hides devices with one genuinely alarming signal — a
 * device transacting 90% with a single counterparty went unflagged purely because it also synced
 * promptly. Averaging noise with evidence loses the evidence.
 */
export const MATERIAL_SIGNAL_FLOOR = 0.2;

export const MODEL_VERSION = 'cap-v1-rules';
export const FRAUD_MODEL_VERSION = 'fraud-v1-heuristic';

/**
 * Number of material signals that must agree before a device is flagged.
 *
 * Each signal alone has an innocent reading — a market trader has high velocity, a rural user
 * syncs late, a family pays each other. Requiring corroboration keeps the console's fraud tab
 * something an operator can trust rather than a list they learn to ignore.
 */
export const MIN_CORROBORATING_SIGNALS = 2;

/**
 * Signals conclusive enough to flag on their own, and the score they must reach to do so.
 *
 * Corroboration is the default because most signals have innocent readings. Circular flow does
 * not: value cycling back and forth between the same two devices at high balance has no retail
 * explanation — a shop does not pay its customers back nine times. Requiring a second signal
 * here would mean seeing a settlement ring and staying silent.
 */
export const STANDALONE_SIGNALS = new Set(['circular_flow']);
export const STANDALONE_SIGNAL_THRESHOLD = 0.8;

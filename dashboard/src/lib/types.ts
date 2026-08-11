/**
 * SPARK API types — mirror docs/api-contract.md exactly.
 * Timestamps are Unix epoch seconds; amounts are integer-paise decimal strings;
 * IDs are canonical UUID v4 (docs/id-conventions.md).
 */

export type IncidentType = 'double_spend' | 'fraud_flag' | 'all';

export interface DoubleSpendIncident {
  type: 'double_spend';
  id: string;
  token_id: string;
  device_id: string;
  tx_id_a: string;
  tx_id_b: string;
  detected_at: number; // Unix epoch seconds
}

/** One reason a device was flagged, from the intelligence service's scoring. */
export interface FraudReason {
  key: string;
  label: string;
  /** Normalised [0,1] — higher is more suspicious. */
  score: number;
  detail: string;
}

/**
 * Produced by the SPARK intelligence service (ai/) and served through
 * GET /admin/incidents?type=fraud_flag. Every field beyond `type` is additive to the contract,
 * which still only guarantees `type`.
 */
export interface FraudFlagIncident {
  type: 'fraud_flag';
  id?: string;
  device_id?: string;
  /** Composite suspicion [0,1]. */
  score?: number;
  reasons?: FraudReason[];
  detected_at?: number;
  model_version?: string;
}

export type Incident = DoubleSpendIncident | FraudFlagIncident;

export interface IncidentsResponse {
  incidents: Incident[];
}

export interface RevokeBody {
  device_id: string;
  reason: string;
}

export interface RevokeResult {
  device_id: string;
  serial_number: string;
  reason: string;
  revoked_at: number; // Unix epoch seconds
}

export interface DisasterToggleBody {
  region_geo: string;
  type: string;
  enabled: boolean;
  higher_cap?: string | null;
  essential_only?: boolean;
}

export interface DisasterEvent {
  id: string;
  region_geo: string;
  type: string;
  enabled: boolean;
  higher_cap: string | null;
  essential_only: boolean;
  updated_at: number; // Unix epoch seconds
}

/** One scored input to the cap recommendation. */
export interface CapSignal {
  key: string;
  label: string;
  /** Normalised [0,1] — higher is safer. */
  score: number;
  /** Relative contribution to the composite. */
  weight: number;
  detail: string;
}

export interface RecommendedCap {
  recommended_cap: string; // integer paise, decimal string
  /** Flat cap the bank falls back to when the model is unavailable. */
  baseline_cap?: string;
  /** Composite of the signals below, [0,1]. */
  confidence?: number;
  signals?: CapSignal[];
  /** Present when an active disaster event raised the cap above the model's own output. */
  disaster_override?: { region_geo: string; higher_cap: string } | null;
  /** True when the account's real balance, not the score, was the binding constraint. */
  balance_capped?: boolean;
  model_version?: string;
  computed_at?: number;
}

/** Backend health endpoint (no auth). */
export interface HealthResponse {
  status: string;
}

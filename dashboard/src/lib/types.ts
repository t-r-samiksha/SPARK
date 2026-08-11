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

/** Shape TBD (contract open question) — backend currently always returns [] for fraud_flag. */
export interface FraudFlagIncident {
  type: 'fraud_flag';
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

export interface RecommendedCap {
  recommended_cap: string; // integer paise, decimal string
}

/** Backend health endpoint (no auth). */
export interface HealthResponse {
  status: string;
}

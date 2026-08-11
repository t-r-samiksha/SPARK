// Client for the fraud intelligence served by Member C's service (ai/), surfaced through
// GET /api/v1/admin/incidents?type=fraud_flag per docs/api-contract.md.
//
// Returns null — not [] — when the service cannot be reached. An empty list is a factual claim
// ("nothing is suspicious right now"); a failed lookup is not, and the console draws the two very
// differently. Collapsing them would let an outage read as an all-clear on a security surface.

/** Budget for the scan. Admin views can wait longer than an interactive purse load. */
const TIMEOUT_MS = Number(process.env.AI_SERVICE_TIMEOUT_MS ?? 4000);

// Opt-in, like the cap client: unset AI_SERVICE_URL means no fraud intelligence is deployed,
// which is the pre-existing "always empty" behaviour rather than an outage.
function serviceUrl(): string | null {
  return process.env.AI_SERVICE_URL || null;
}

export interface FraudReason {
  key: string;
  label: string;
  score: number;
  detail: string;
}

export interface FraudFlagIncident {
  type: 'fraud_flag';
  id: string;
  device_id: string;
  score: number;
  reasons: FraudReason[];
  detected_at: number;
  model_version: string;
}

/** Current fraud flags, or null if the intelligence service could not be reached. */
export async function getFraudFlags(): Promise<FraudFlagIncident[] | null> {
  const base = serviceUrl();
  if (base === null) return [];

  try {
    const response = await fetch(`${base}/fraud/flags`, {
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) return null;

    const payload = (await response.json()) as { incidents?: unknown };
    if (!Array.isArray(payload?.incidents)) return null;

    return payload.incidents as FraudFlagIncident[];
  } catch {
    return null;
  }
}

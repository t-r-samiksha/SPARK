// Client for Member C's intelligence service, served at GET /api/v1/limit/recommendation per
// docs/api-contract.md.
//
// This was a hardcoded ₹2000 placeholder; it now calls the real model in ai/ (spark-ai), which is
// the swap the original TODO here asked for. The file name is kept so the import sites in
// purse/routes.ts and sync/routes.ts do not move — rename freely if you prefer.
//
// FALLBACK IS DELIBERATE: if the intelligence service is slow, down, or returns something
// unexpected, this returns the ₹2000 baseline rather than throwing. A scoring model must never be
// able to take purse loading offline — the bank degrades to a flat conservative cap instead.

/** Placeholder recommended cap: ₹2000 in integer paise, as a decimal string per
 * docs/id-conventions.md#amounts. Now the fallback used when the model is unreachable. */
const PLACEHOLDER_CAP_PAISE = '200000';

const DECIMAL_PAISE = /^[0-9]+$/;

/** Budget for the model call. Purse loading is interactive, so it waits briefly or gives up. */
const TIMEOUT_MS = Number(process.env.AI_SERVICE_TIMEOUT_MS ?? 1500);

// OPT-IN BY DESIGN: with AI_SERVICE_URL unset, this module behaves exactly as the original
// placeholder did. Backend tests and anyone running the bank server alone must not be made to
// depend on a second service being up — enabling the model is a deployment choice, not a default.
function serviceUrl(): string | null {
  return process.env.AI_SERVICE_URL || null;
}

/** True when a cap-intelligence service has been configured. */
export function capIntelligenceConfigured(): boolean {
  return serviceUrl() !== null;
}

export interface CapSignal {
  key: string;
  label: string;
  score: number;
  weight: number;
  detail: string;
}

export interface CapRecommendation {
  recommended_cap: string;
  baseline_cap?: string;
  confidence?: number;
  signals?: CapSignal[];
  disaster_override?: { region_geo: string; higher_cap: string } | null;
  balance_capped?: boolean;
  model_version?: string;
  computed_at?: number;
  /** True when the model could not be reached and the flat baseline was substituted. */
  degraded?: boolean;
}

/**
 * Full recommendation for a device, including the signal decomposition the operations console
 * renders. Never throws: on any failure it reports the baseline with `degraded: true`, so callers
 * can be honest about showing a fallback rather than a model output.
 */
export async function getCapRecommendation(deviceId?: string): Promise<CapRecommendation> {
  const base = serviceUrl();
  if (base === null) {
    return { recommended_cap: PLACEHOLDER_CAP_PAISE };
  }

  const query = deviceId ? `?device_id=${encodeURIComponent(deviceId)}` : '';
  try {
    const response = await fetch(`${base}/limit/recommendation${query}`, {
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) {
      return { recommended_cap: PLACEHOLDER_CAP_PAISE, degraded: true };
    }

    const payload = (await response.json()) as CapRecommendation;
    // The cap feeds BigInt arithmetic downstream; a malformed value must not reach it.
    if (
      typeof payload?.recommended_cap !== 'string' ||
      !DECIMAL_PAISE.test(payload.recommended_cap)
    ) {
      return { recommended_cap: PLACEHOLDER_CAP_PAISE, degraded: true };
    }
    return payload;
  } catch {
    return { recommended_cap: PLACEHOLDER_CAP_PAISE, degraded: true };
  }
}

/** Recommended cap as a decimal-paise string — the shape existing callers already expect. */
export async function getRecommendedCap(deviceId?: string): Promise<string> {
  const recommendation = await getCapRecommendation(deviceId);
  return recommendation.recommended_cap;
}

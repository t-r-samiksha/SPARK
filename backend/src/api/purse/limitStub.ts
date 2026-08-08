// Stub for Member C's AI spend-cap model, served at GET /api/v1/limit/recommendation per
// docs/api-contract.md — that service doesn't exist yet. TODO: replace this with a real call to
// Member C's /limit/recommendation once available. Routes should call this function rather than
// hardcoding the placeholder inline, so swapping it out later is a one-line change here.

/** Placeholder recommended cap: ₹2000 in integer paise, as a decimal string per
 * docs/id-conventions.md#amounts. */
const PLACEHOLDER_CAP_PAISE = '200000';

export function getRecommendedCap(): string {
  return PLACEHOLDER_CAP_PAISE;
}

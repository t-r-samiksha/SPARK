// Shared demo-account constants, split out of seed.ts specifically so other code (e.g.
// scripts/smokeTestEnroll.ts) can import DEMO_ACCOUNT_ID without also importing seed.ts itself —
// seed.ts runs its main() unconditionally at module load (it's a standalone script, not a pure
// module), so importing anything from it anywhere else triggers a real seed run as a side effect.

export const DEMO_ACCOUNT_ID = '00000000-0000-4000-8000-000000000001';
export const DEMO_REAL_BALANCE_PAISE = 5_000_000n; // ₹50,000

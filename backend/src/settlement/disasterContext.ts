import { prisma } from '../db/client';

/**
 * Returns the currently-active DisasterEvent to apply, or null if none are active. Single query,
 * reused wherever disaster-mode context is needed (currently POST /api/v1/purse/load — see that
 * route for how `higher_cap` is applied).
 *
 * HACKATHON SIMPLIFICATION: no device-location field exists anywhere in the schema yet, so this
 * treats ANY active DisasterEvent as globally applicable, rather than matching against the
 * calling device's own region. Real version: filter by `region_geo` against the device's actual
 * location once that data exists — see the same simplification already noted on GET
 * /sync/updates' `flags` field in src/api/sync/routes.ts.
 *
 * JUDGMENT CALL: if multiple DisasterEvents are active simultaneously (possible once real
 * region-matching exists, since different regions could each have their own active event), this
 * returns the most recently started one — arbitrary but deterministic. Returning a single
 * "canonical" active event only makes sense under the treat-everything-as-global simplification
 * above; once region matching exists, callers should query by region instead of taking one global
 * event.
 */
export async function getActiveDisasterEvent() {
  return prisma.disasterEvent.findFirst({
    where: { active: true },
    orderBy: { startedAt: 'desc' },
  });
}

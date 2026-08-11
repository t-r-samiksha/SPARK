/**
 * Display formatters for SPARK primitives (docs/id-conventions.md):
 * amounts are integer paise; timestamps are Unix epoch seconds; IDs are UUID v4.
 */

/**
 * Integer paise → ₹ with Indian digit grouping. Amounts are stored as integer paise,
 * so display divides by 100: "200000" → "₹2,000"; "250050" → "₹2,500.50".
 */
export function formatPaise(paise: string | number): string {
  const n = typeof paise === 'number' ? paise : Number.parseInt(paise, 10);
  if (!Number.isFinite(n)) return '—';
  const whole = Math.floor(n / 100);
  const frac = n % 100;
  return frac === 0 ? `₹${whole.toLocaleString('en-IN')}` : `₹${whole.toLocaleString('en-IN')}.${String(frac).padStart(2, '0')}`;
}

/** Epoch seconds → "YYYY-MM-DD HH:MM UTC". */
export function formatEpoch(epochSeconds: number): string {
  const d = new Date(epochSeconds * 1000);
  const pad = (v: number): string => String(v).padStart(2, '0');
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(
    d.getUTCHours(),
  )}:${pad(d.getUTCMinutes())} UTC`;
}

/** Epoch seconds → short relative label, e.g. "3m ago", "2h ago", "12d ago". */
export function formatRelative(epochSeconds: number, now: number = Date.now()): string {
  const diffSec = Math.max(0, Math.floor((now / 1000 - epochSeconds)));
  if (diffSec < 60) return `${diffSec}s ago`;
  const min = Math.floor(diffSec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.floor(hr / 24);
  if (day < 30) return `${day}d ago`;
  return formatEpoch(epochSeconds);
}

/** UUID → short mono form, e.g. "4a1e6e2b…7f10" (first 8 + last 4). */
export function shortId(id: string): string {
  if (id.length !== 36) return id;
  return `${id.slice(0, 8)}…${id.slice(-4)}`;
}

/** Validate canonical (lowercase) UUID v4. */
export const UUID_V4_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export function isValidUuidV4(value: string): boolean {
  return UUID_V4_RE.test(value);
}

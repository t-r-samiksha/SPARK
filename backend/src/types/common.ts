// Shared primitive type aliases per docs/id-conventions.md. These are branded as plain strings
// (not literal-validated at the type level) — runtime validation happens where objects cross a
// trust boundary (API request parsing), not via the type system.

/** UUID v4, lowercase hyphenated, 36 chars. */
export type Uuid = string;

/** Unix epoch seconds (integer). Used for every timestamp except the two ISO 8601 exceptions
 * below — see docs/id-conventions.md#timestamps. */
export type EpochSeconds = number;

/** ISO 8601 UTC timestamp, `YYYY-MM-DDTHH:MM:SSZ`. Only used by `Certificate.not_before` /
 * `not_after` and `TrustAttestation.timestamp` — everything else uses EpochSeconds. */
export type Iso8601Timestamp = string;

/** Integer paise amount, always a decimal string in signed payloads (never a JSON number) —
 * see docs/id-conventions.md#amounts. */
export type DecimalPaise = string;

/** base64url-encoded (unpadded) binary field — public key, signature, or hash. */
export type Base64Url = string;

/** A full PEM envelope string (BEGIN/END markers, standard padded base64 body). */
export type PemEnvelope = string;

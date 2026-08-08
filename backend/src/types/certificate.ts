import { Base64Url, Iso8601Timestamp, Uuid } from './common';

// Mirrors docs/certificate-format.md. Not X.509 — a lightweight PEM-wrapped JSON structure.

export interface Certificate {
  device_id: Uuid;
  account_id: Uuid;
  device_public_key: Base64Url;
  serial_number: string;
  /** ISO 8601 — exception to the epoch-seconds default, see docs/id-conventions.md#timestamps. */
  not_before: Iso8601Timestamp;
  not_after: Iso8601Timestamp;
  /** Ed25519 signature by the Bank Root CA key, over every field above except this one. */
  signature: Base64Url;
}

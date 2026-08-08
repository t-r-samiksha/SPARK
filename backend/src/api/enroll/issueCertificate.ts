import { randomUUID } from 'crypto';
import { canonicalizeForSigning, canonicalizeFull, ed25519Sign, pemEncode } from '../../crypto';
import { bankRootCaKeySeed } from '../../config';
import { Certificate } from '../../types';

// Cert validity period: not specified in docs/certificate-format.md (only flagged as an open
// question re: renewal flow, not lifetime). Chose 1 year to match the doc's own example
// (`not_before`/`not_after` a year apart) — a judgment call, revisit once a renewal endpoint or
// policy is decided.
const VALIDITY_MS = 365 * 24 * 60 * 60 * 1000;

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

/** ISO 8601 UTC, `YYYY-MM-DDTHH:MM:SSZ`, no fractional seconds — per
 * docs/id-conventions.md#timestamps. */
function toIso8601(date: Date): string {
  return `${date.toISOString().split('.')[0]}Z`;
}

/** Serial allocation scheme is an open question in docs/certificate-format.md (shown there only
 * as illustrative). Using a random suffix rather than an incrementing counter avoids a
 * read-then-write race under concurrent enrollments — revisit if a sequential scheme is decided
 * at a future sync. */
function generateSerialNumber(): string {
  return `SPARK-CERT-${randomUUID().replace(/-/g, '').slice(0, 16).toUpperCase()}`;
}

export interface IssuedCertificate {
  certificate: Certificate;
  pem: string;
}

export function issueCertificate(params: {
  device_id: string;
  account_id: string;
  device_public_key: string;
}): IssuedCertificate {
  const now = new Date();
  const notAfter = new Date(now.getTime() + VALIDITY_MS);

  const unsigned: Omit<Certificate, 'signature'> = {
    device_id: params.device_id,
    account_id: params.account_id,
    device_public_key: params.device_public_key,
    serial_number: generateSerialNumber(),
    not_before: toIso8601(now),
    not_after: toIso8601(notAfter),
  };

  const signingBytes = canonicalizeForSigning(unsigned);
  const signature = ed25519Sign(bankRootCaKeySeed(), signingBytes);

  const certificate: Certificate = { ...unsigned, signature };
  // Spread into a fresh object literal at the call site: TypeScript only permits assigning a
  // named interface type (no index signature) to a Record<string, JsonValue> parameter when the
  // argument is itself a literal — a typed variable reference needs this to stay structurally
  // "fresh". See the note on canonicalizeFull/canonicalizeForSigning in crypto/canonical.ts.
  const pem = pemEncode(CERT_PEM_LABEL, canonicalizeFull({ ...certificate }));

  return { certificate, pem };
}

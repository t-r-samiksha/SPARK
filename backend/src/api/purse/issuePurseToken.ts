import { randomUUID } from 'crypto';
import { canonicalizeForSigning, canonicalizeFull, ed25519Sign, pemEncode } from '../../crypto';
import { bankSigningKeySeed } from '../../config';
import { PurseToken } from '../../types';

const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';

// Validity window: not specified in docs/purse-token-format.md — judgment call, same category as
// the certificate's 1-year choice in issueCertificate.ts, but much shorter: purse tokens exist
// for short-lived *offline* spend ("the device must resync... before then" per the doc), so 30
// days balances staying useful without constant network access against bounding how long a
// lost/stolen/compromised device's purse remains spendable.
const EXPIRY_MS = 30 * 24 * 60 * 60 * 1000;

export interface IssuedPurseToken {
  purseToken: PurseToken;
  pem: string;
}

export function issuePurseToken(params: {
  device_id: string;
  value: string;
  cap: string;
  counter_start: number;
}): IssuedPurseToken {
  // Unix epoch seconds per docs/id-conventions.md#timestamps (purse token expiry is NOT one of
  // the two ISO 8601 exceptions).
  const expiry = Math.floor((Date.now() + EXPIRY_MS) / 1000);

  const unsigned: Omit<PurseToken, 'signature'> = {
    device_id: params.device_id,
    value: params.value,
    cap: params.cap,
    counter_start: params.counter_start,
    expiry,
    token_id: randomUUID(),
  };

  const signingBytes = canonicalizeForSigning(unsigned);
  // Bank operational key, not Root CA — per the confirmed key-separation decision (the Root CA
  // key signs device certificates only; see issueCertificate.ts).
  const signature = ed25519Sign(bankSigningKeySeed(), signingBytes);

  const purseToken: PurseToken = { ...unsigned, signature };
  // Spread into a fresh object literal — see the note in crypto/canonical.ts on why a typed
  // variable needs this to satisfy the Record<string, JsonValue> parameter.
  const pem = pemEncode(PURSE_TOKEN_PEM_LABEL, canonicalizeFull({ ...purseToken }));

  return { purseToken, pem };
}

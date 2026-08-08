import { Base64Url, DecimalPaise, EpochSeconds, Uuid } from './common';

// Mirrors docs/purse-token-format.md.

export interface PurseToken {
  device_id: Uuid;
  /** Balance at issuance — signed once, never mutated. See
   * docs/purse-token-format.md#spend-enforcement-decided for how spend is actually enforced. */
  value: DecimalPaise;
  cap: DecimalPaise;
  counter_start: number;
  expiry: EpochSeconds;
  token_id: Uuid;
  /** Ed25519 signature by the Bank (operational) key, over every field above except this one. */
  signature: Base64Url;
}

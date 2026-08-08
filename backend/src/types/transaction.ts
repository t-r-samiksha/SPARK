import { Base64Url, DecimalPaise, EpochSeconds, PemEnvelope, Uuid } from './common';

// Mirrors docs/transaction-format.md.

export interface TransactionParty {
  device_id: Uuid;
  account_id: Uuid;
  /** Full PEM envelope of the party's device certificate — embedded, not referenced, so the
   * transaction can be verified fully offline. */
  cert: PemEnvelope;
}

export interface Transaction {
  tx_id: Uuid;
  token_id: Uuid;
  amount: DecimalPaise;
  payer: TransactionParty;
  payee: TransactionParty;
  device_counter: number;
  prev_tx_hash: Base64Url | null;
  timestamp: EpochSeconds;
  /** Ed25519 signature by the payer's device key, over every field above except this one. */
  signature: Base64Url;
}

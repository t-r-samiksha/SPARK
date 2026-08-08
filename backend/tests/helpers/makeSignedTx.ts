import { randomUUID } from 'crypto';
import { FastifyInstance } from 'fastify';
import { canonicalizeForSigning, ed25519Sign, generateEd25519KeyPair, JsonValue, pemDecode } from '../../src/crypto';
import { Transaction } from '../../src/types';
import { hashTransaction } from '../../src/settlement/engine';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

export interface TestDevice {
  deviceId: string;
  accountId: string;
  publicKey: string;
  privateSeed: string;
  certPem: string;
  serialNumber: string;
}

/**
 * Enrolls a fresh device via the real POST /api/v1/enroll endpoint, so generated transactions
 * carry a real, Bank-signed cert and pass the settlement engine's trust checks for real, not just
 * by construction. Member A doesn't have real Android-signed fixtures ready yet — this (plus
 * makeSignedTx below) stands in for that using our own crypto library.
 */
export async function enrollTestDevice(app: FastifyInstance, accountId: string = randomUUID()): Promise<TestDevice> {
  const { publicKey, privateSeed } = generateEd25519KeyPair();
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/enroll',
    payload: {
      account_id: accountId,
      public_key: publicKey,
      attestation_blob: 'test-attestation-blob',
    },
  });
  if (response.statusCode !== 200) {
    throw new Error(`enrollment failed in test helper: ${response.statusCode} ${response.body}`);
  }
  const { cert: certPem } = response.json();
  const certJson = JSON.parse(pemDecode(CERT_PEM_LABEL, certPem).toString('utf8'));
  return {
    deviceId: certJson.device_id,
    accountId,
    publicKey,
    privateSeed,
    certPem,
    serialNumber: certJson.serial_number,
  };
}

export interface MakeSignedTxParams {
  payer: TestDevice;
  payee: TestDevice;
  tokenId: string;
  amount: string;
  deviceCounter: number;
  /** The previous transaction in this device's chain for this token, or omitted/null if this is
   * the first transaction ever settled against the token (prev_tx_hash = null). */
  prevTx?: Transaction | null;
  timestamp?: number;
  /** Overrides tx_id — for tests that need a specific or deliberately-duplicate tx_id. Defaults
   * to a fresh random UUID. */
  txId?: string;
}

/** Builds and signs a transaction using our own crypto library and a real enrolled device. */
export function makeSignedTx(params: MakeSignedTxParams): Transaction {
  const unsigned = {
    tx_id: params.txId ?? randomUUID(),
    token_id: params.tokenId,
    amount: params.amount,
    payer: { device_id: params.payer.deviceId, account_id: params.payer.accountId, cert: params.payer.certPem },
    payee: { device_id: params.payee.deviceId, account_id: params.payee.accountId, cert: params.payee.certPem },
    device_counter: params.deviceCounter,
    prev_tx_hash: params.prevTx ? hashTransaction(params.prevTx) : null,
    timestamp: params.timestamp ?? Math.floor(Date.now() / 1000),
  };

  // Cast, not spread — Transaction has nested typed objects (payer/payee); see the note in
  // src/crypto/canonical.ts and src/settlement/engine.ts.
  const signingBytes = canonicalizeForSigning(unsigned as unknown as Record<string, JsonValue>);
  const signature = ed25519Sign(params.payer.privateSeed, signingBytes);

  return { ...unsigned, signature };
}

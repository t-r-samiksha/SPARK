import 'dotenv/config';
import { randomUUID } from 'crypto';
import {
  base64urlDecode,
  canonicalizeForSigning,
  ed25519Sign,
  ed25519Verify,
  generateEd25519KeyPair,
  JsonValue,
  pemDecode,
} from '../src/crypto';
import { bankSigningPublicKey } from '../src/config';
import { DEMO_ACCOUNT_ID } from '../src/db/demoAccount';
import { Transaction, TrustAttestation } from '../src/types';

// Standalone smoke test — NOT part of the Jest suite. Exercises the trust flow end-to-end against
// the real deployed backend: enroll two devices, fund + load a purse for the payer, settle a
// transaction between them, then confirm a real, Bank-signed trust attestation shows up for it.
//
// Run with: npx tsx scripts/smokeTestTrust.ts
//
// Verification caveat: same as smokeTestEnroll.ts — "the known operational public key" is derived
// from the LOCAL BANK_SIGNING_KEY_SEED (backend/.env), not fetched from Render. A ✅ here proves
// the deployed server signs trust attestations with the same key as local .env; if they'd ever
// diverged, this would correctly report a verification failure, not silently pass.

const BASE_URL = 'https://spark-m1pt.onrender.com/api/v1';
const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';
const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';
const TRUST_ATTESTATION_PEM_LABEL = 'SPARK TRUST ATTESTATION';

interface EnrolledDevice {
  deviceId: string;
  accountId: string;
  publicKey: string;
  privateSeed: string;
  certPem: string;
}

async function postJson(path: string, body: unknown, sessionToken?: string): Promise<{ status: number; json: any }> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(sessionToken ? { authorization: `Bearer ${sessionToken}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let json: any;
  try {
    json = JSON.parse(text);
  } catch {
    json = { __unparseable_body__: text };
  }
  return { status: response.status, json };
}

async function getJson(path: string, sessionToken: string): Promise<{ status: number; json: any }> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { authorization: `Bearer ${sessionToken}` },
  });
  const text = await response.text();
  let json: any;
  try {
    json = JSON.parse(text);
  } catch {
    json = { __unparseable_body__: text };
  }
  return { status: response.status, json };
}

async function enrollDevice(label: string, accountId: string): Promise<EnrolledDevice> {
  const { publicKey, privateSeed } = generateEd25519KeyPair();
  const { status, json } = await postJson('/enroll', {
    account_id: accountId,
    public_key: publicKey,
    attestation_blob: `smoke-test-trust-${label}`,
  });
  if (status !== 200) {
    throw new Error(`enroll (${label}) failed: ${status} ${JSON.stringify(json)}`);
  }
  const certJson = JSON.parse(pemDecode(CERT_PEM_LABEL, json.cert).toString('utf8'));
  console.log(`Enrolled ${label}: device_id=${certJson.device_id} account_id=${accountId}`);
  return { deviceId: certJson.device_id, accountId, publicKey, privateSeed, certPem: json.cert };
}

async function authenticate(device: EnrolledDevice): Promise<string> {
  const challenge = await postJson('/auth/challenge', { device_id: device.deviceId });
  if (challenge.status !== 200) {
    throw new Error(`auth/challenge failed: ${challenge.status} ${JSON.stringify(challenge.json)}`);
  }
  const signedNonce = ed25519Sign(device.privateSeed, base64urlDecode(challenge.json.nonce));
  const verify = await postJson('/auth/verify', { device_id: device.deviceId, signed_nonce: signedNonce });
  if (verify.status !== 200) {
    throw new Error(`auth/verify failed: ${verify.status} ${JSON.stringify(verify.json)}`);
  }
  return verify.json.session_token;
}

interface LoadedPurseToken {
  tokenId: string;
  counterStart: number;
}

async function loadPurse(sessionToken: string, value: string): Promise<LoadedPurseToken> {
  const { status, json } = await postJson('/purse/load', { value }, sessionToken);
  if (status !== 200) {
    throw new Error(`purse/load failed: ${status} ${JSON.stringify(json)}`);
  }
  const tokenJson = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, json.purse_token).toString('utf8'));
  console.log(`Loaded purse token_id=${tokenJson.token_id} value=${tokenJson.value} cap=${tokenJson.cap}`);
  return { tokenId: tokenJson.token_id, counterStart: tokenJson.counter_start };
}

function buildSignedTransaction(params: {
  payer: EnrolledDevice;
  payee: EnrolledDevice;
  tokenId: string;
  amount: string;
  deviceCounter: number;
}): Transaction {
  const unsigned = {
    tx_id: randomUUID(),
    token_id: params.tokenId,
    amount: params.amount,
    payer: { device_id: params.payer.deviceId, account_id: params.payer.accountId, cert: params.payer.certPem },
    payee: { device_id: params.payee.deviceId, account_id: params.payee.accountId, cert: params.payee.certPem },
    device_counter: params.deviceCounter,
    prev_tx_hash: null,
    timestamp: Math.floor(Date.now() / 1000),
  };
  // Cast, not spread — Transaction has nested typed objects (payer/payee); see the note in
  // src/crypto/canonical.ts and src/settlement/engine.ts.
  const signingBytes = canonicalizeForSigning(unsigned as unknown as Record<string, JsonValue>);
  const signature = ed25519Sign(params.payer.privateSeed, signingBytes);
  return { ...unsigned, signature };
}

async function main(): Promise<void> {
  console.log(`Trust-flow smoke test against ${BASE_URL}\n`);

  // --- Step 1: enroll payer + payee ------------------------------------------------------------
  const payer = await enrollDevice('payer', DEMO_ACCOUNT_ID);
  const payee = await enrollDevice('payee', randomUUID());
  console.log();

  // --- Step 2: fund the payer's purse and settle a transaction ---------------------------------
  const payerSession = await authenticate(payer);
  const token = await loadPurse(payerSession, '20000'); // within limitStub's 200000-paise cap

  const tx = buildSignedTransaction({
    payer,
    payee,
    tokenId: token.tokenId,
    amount: '10000',
    deviceCounter: token.counterStart,
  });
  console.log(`\nBuilt signed transaction tx_id=${tx.tx_id} amount=${tx.amount}`);

  const syncResponse = await postJson('/sync/transactions', { transactions: [tx] }, payerSession);
  console.log(`\nPOST /sync/transactions status: ${syncResponse.status}`);
  console.log(JSON.stringify(syncResponse.json, null, 2));

  const result = syncResponse.json?.results?.find((r: { tx_id: string }) => r.tx_id === tx.tx_id);
  if (syncResponse.status !== 200 || !result || result.status !== 'accepted') {
    console.error('\n❌ Transaction did not settle (status "accepted") — stopping before trust check.');
    process.exitCode = 1;
    return;
  }
  console.log('\n✅ Transaction settled.');

  // --- Step 3: fetch trust attestations for the payer device ------------------------------------
  const attestationsResponse = await getJson(`/trust/attestations?subject=${payer.deviceId}`, payerSession);
  console.log(`\nGET /trust/attestations?subject=${payer.deviceId} status: ${attestationsResponse.status}`);

  if (attestationsResponse.status !== 200) {
    console.error('\n❌ /trust/attestations did not return 200.');
    console.error(JSON.stringify(attestationsResponse.json, null, 2));
    process.exitCode = 1;
    return;
  }

  const attestationPems: string[] = attestationsResponse.json.attestations ?? [];
  console.log(`Received ${attestationPems.length} attestation(s).`);

  // --- Step 4: verify a real, non-empty attestation with a valid signature ---------------------
  const operationalPublicKey = bankSigningPublicKey();
  let found = false;

  for (const pem of attestationPems) {
    const json = JSON.parse(pemDecode(TRUST_ATTESTATION_PEM_LABEL, pem).toString('utf8')) as TrustAttestation;
    const involvesBothDevices =
      (json.subject_a === payer.deviceId || json.subject_b === payer.deviceId) &&
      (json.subject_a === payee.deviceId || json.subject_b === payee.deviceId);
    if (!involvesBothDevices) {
      continue;
    }

    console.log('\nMatching attestation:');
    console.log(JSON.stringify(json, null, 2));

    const signingBytes = canonicalizeForSigning({ ...json });
    const valid = ed25519Verify(operationalPublicKey, signingBytes, json.signature);

    if (valid) {
      console.log('\n✅ Trust attestation is non-empty and its signature verifies against the known');
      console.log('   Bank operational public key (derived from local BANK_SIGNING_KEY_SEED).');
      found = true;
    } else {
      console.error('\n❌ Attestation found, but its signature does NOT verify against the known');
      console.error('   operational public key.');
      process.exitCode = 1;
    }
    break;
  }

  if (!found && process.exitCode !== 1) {
    console.error('\n❌ No attestation involving both devices was found in the response.');
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error('Smoke test failed with an unexpected error:', err);
  process.exitCode = 1;
});

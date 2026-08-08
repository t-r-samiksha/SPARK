import 'dotenv/config';
import { canonicalizeForSigning, ed25519Verify, generateEd25519KeyPair, pemDecode } from '../src/crypto';
import { bankRootCaPublicKey } from '../src/config';
import { DEMO_ACCOUNT_ID } from '../src/db/demoAccount';
import { Certificate } from '../src/types';

// Standalone smoke test — NOT part of the Jest suite. Hits the real deployed backend to confirm
// POST /api/v1/enroll works end-to-end against production and that the returned certificate is
// actually signed by the real production Bank Root CA key, not some other/misconfigured key.
//
// Run with: npx tsx scripts/smokeTestEnroll.ts
//
// Verification caveat: this script derives "the known Bank Root CA public key" from the LOCAL
// BANK_ROOT_CA_KEY_SEED (backend/.env), not from anything fetched from Render. That only proves
// what we want IF the same seed is configured in both places. If local and deployed keys have
// ever diverged, this will correctly report a verification failure — that's a real, useful signal
// pointing at a key mismatch, not a bug in this script.

const TARGET_URL = 'https://spark-m1pt.onrender.com/api/v1/enroll';
const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

async function main(): Promise<void> {
  console.log(`Enroll smoke test against ${TARGET_URL}\n`);

  const { publicKey } = generateEd25519KeyPair();
  console.log(`Generated device public key: ${publicKey}`);

  const requestBody = {
    account_id: DEMO_ACCOUNT_ID,
    public_key: publicKey,
    attestation_blob: 'smoke-test-placeholder-attestation',
  };
  console.log('Request body:');
  console.log(JSON.stringify(requestBody, null, 2));

  const response = await fetch(TARGET_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(requestBody),
  });
  const responseText = await response.text();

  console.log(`\nResponse status: ${response.status}`);
  console.log('Response body:');
  console.log(responseText);

  if (response.status !== 200) {
    console.error('\n❌ /enroll did not return 200 — stopping before signature verification.');
    process.exitCode = 1;
    return;
  }

  let certPem: string;
  try {
    ({ cert: certPem } = JSON.parse(responseText));
  } catch (err) {
    console.error('\n❌ Could not parse response body as JSON:', err);
    process.exitCode = 1;
    return;
  }

  const certJson = JSON.parse(pemDecode(CERT_PEM_LABEL, certPem).toString('utf8')) as Certificate;
  console.log('\nDecoded certificate:');
  console.log(JSON.stringify(certJson, null, 2));

  const rootCaPublicKey = bankRootCaPublicKey();
  console.log(`\nVerifying against Bank Root CA public key (derived from local BANK_ROOT_CA_KEY_SEED):`);
  console.log(rootCaPublicKey);

  const signingBytes = canonicalizeForSigning({ ...certJson });
  const valid = ed25519Verify(rootCaPublicKey, signingBytes, certJson.signature);

  if (valid) {
    console.log('\n✅ Certificate signature verifies against the known Bank Root CA public key.');
    console.log('   The deployed server is signing with the same key as local BANK_ROOT_CA_KEY_SEED.');
  } else {
    console.error('\n❌ Certificate signature does NOT verify against the known Bank Root CA public key.');
    console.error('   Either the deployed server is using a different root CA key than local .env,');
    console.error('   or the certificate/response was tampered with in transit.');
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error('Smoke test failed with an unexpected error:', err);
  process.exitCode = 1;
});

import { canonicalizeForSigning, ed25519Sign, ed25519Verify, publicKeyFromSeed } from '../../src/crypto';

// Ground-truth test against the real, verified test vector documented in
// docs/canonical-serialization.md#test-vector. That vector was generated and independently
// verified with Node's `crypto` module — if this test fails, the bug is in this implementation's
// canonicalization or signing, not in the vector.

const PRIVATE_SEED = 'rWUD47KRVvCyr9N4knN-ZyKP1z2o0UKQEJCoVuNrSRw';
const PUBLIC_KEY = 'luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE';
const EXPECTED_SIGNATURE =
  '6QQtyJFvKopqPH5o7EmhbpC-mU_OwGsy1QVKWV5ZpV79R1KHXqHoVqMwBOyEQ5MOxd36b0NF4H4GAVczrxuzDw';

// Already in canonical field order and form, per the doc.
const MESSAGE_OBJECT = { amount: '25000', device_id: 'x' };
const EXPECTED_CANONICAL_STRING = '{"amount":"25000","device_id":"x"}';

describe('canonical-serialization.md test vector', () => {
  it('derives the documented public key from the documented seed', () => {
    expect(publicKeyFromSeed(PRIVATE_SEED)).toBe(PUBLIC_KEY);
  });

  it('canonically serializes the documented message exactly', () => {
    const bytes = canonicalizeForSigning(MESSAGE_OBJECT);
    expect(bytes.toString('utf8')).toBe(EXPECTED_CANONICAL_STRING);
  });

  it('signs the canonical message and reproduces the documented signature exactly', () => {
    const bytes = canonicalizeForSigning(MESSAGE_OBJECT);
    const signature = ed25519Sign(PRIVATE_SEED, bytes);
    expect(signature).toBe(EXPECTED_SIGNATURE);
  });

  it('verifies the documented signature against the documented public key', () => {
    const bytes = canonicalizeForSigning(MESSAGE_OBJECT);
    expect(ed25519Verify(PUBLIC_KEY, bytes, EXPECTED_SIGNATURE)).toBe(true);
  });
});

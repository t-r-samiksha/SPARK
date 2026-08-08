import { generateX25519KeyPair, x25519SharedSecret } from '../../src/crypto';

describe('x25519', () => {
  it('two parties derive the same shared secret', () => {
    const alice = generateX25519KeyPair();
    const bob = generateX25519KeyPair();

    const secretFromAlice = x25519SharedSecret(alice.privateKey, bob.publicKey);
    const secretFromBob = x25519SharedSecret(bob.privateKey, alice.publicKey);

    expect(secretFromAlice.equals(secretFromBob)).toBe(true);
    expect(secretFromAlice.length).toBe(32);
  });

  it('produces a different shared secret for a different peer', () => {
    const alice = generateX25519KeyPair();
    const bob = generateX25519KeyPair();
    const carol = generateX25519KeyPair();

    const secretWithBob = x25519SharedSecret(alice.privateKey, bob.publicKey);
    const secretWithCarol = x25519SharedSecret(alice.privateKey, carol.publicKey);

    expect(secretWithBob.equals(secretWithCarol)).toBe(false);
  });
});

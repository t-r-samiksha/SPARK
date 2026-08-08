import { ed25519Sign, ed25519Verify, generateEd25519KeyPair, publicKeyFromSeed } from '../../src/crypto';

describe('ed25519', () => {
  it('sign/verify roundtrips for a freshly generated keypair', () => {
    const { privateSeed, publicKey } = generateEd25519KeyPair();
    const message = Buffer.from('hello spark', 'utf8');
    const signature = ed25519Sign(privateSeed, message);
    expect(ed25519Verify(publicKey, message, signature)).toBe(true);
  });

  it('rejects a tampered message', () => {
    const { privateSeed, publicKey } = generateEd25519KeyPair();
    const message = Buffer.from('original message', 'utf8');
    const signature = ed25519Sign(privateSeed, message);
    const tampered = Buffer.from('t4mpered message', 'utf8');
    expect(ed25519Verify(publicKey, tampered, signature)).toBe(false);
  });

  it('rejects a signature from a different keypair', () => {
    const pairA = generateEd25519KeyPair();
    const pairB = generateEd25519KeyPair();
    const message = Buffer.from('cross-key check', 'utf8');
    const signature = ed25519Sign(pairA.privateSeed, message);
    expect(ed25519Verify(pairB.publicKey, message, signature)).toBe(false);
  });

  it('generates a keypair with the expected raw lengths', () => {
    const { privateSeed, publicKey } = generateEd25519KeyPair();
    expect(Buffer.from(privateSeed.replace(/-/g, '+').replace(/_/g, '/'), 'base64').length).toBe(32);
    expect(Buffer.from(publicKey.replace(/-/g, '+').replace(/_/g, '/'), 'base64').length).toBe(32);
  });

  it('derives the same public key from the same seed deterministically', () => {
    const { privateSeed, publicKey } = generateEd25519KeyPair();
    expect(publicKeyFromSeed(privateSeed)).toBe(publicKey);
  });
});

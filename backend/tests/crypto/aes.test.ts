import { aesGcmDecrypt, aesGcmEncrypt, generateAesKey } from '../../src/crypto';

describe('aes-256-gcm', () => {
  it('roundtrips plaintext through encrypt/decrypt', () => {
    const key = generateAesKey();
    const plaintext = Buffer.from('a secret purse token value', 'utf8');
    const blob = aesGcmEncrypt(key, plaintext);
    expect(aesGcmDecrypt(key, blob).equals(plaintext)).toBe(true);
  });

  it('produces a different nonce (and ciphertext) on each call', () => {
    const key = generateAesKey();
    const plaintext = Buffer.from('same plaintext twice', 'utf8');
    const blobA = aesGcmEncrypt(key, plaintext);
    const blobB = aesGcmEncrypt(key, plaintext);
    expect(blobA.equals(blobB)).toBe(false);
  });

  it('fails to decrypt with the wrong key', () => {
    const key = generateAesKey();
    const wrongKey = generateAesKey();
    const plaintext = Buffer.from('sensitive payload', 'utf8');
    const blob = aesGcmEncrypt(key, plaintext);
    expect(() => aesGcmDecrypt(wrongKey, blob)).toThrow();
  });

  it('fails to decrypt tampered ciphertext (auth tag check)', () => {
    const key = generateAesKey();
    const plaintext = Buffer.from('integrity matters', 'utf8');
    const blob = aesGcmEncrypt(key, plaintext);
    const tampered = Buffer.from(blob);
    tampered[tampered.length - 1] ^= 0xff;
    expect(() => aesGcmDecrypt(key, tampered)).toThrow();
  });

  it('roundtrips with AAD and fails when AAD is wrong', () => {
    const key = generateAesKey();
    const plaintext = Buffer.from('bound to context', 'utf8');
    const aad = Buffer.from('device-123', 'utf8');
    const blob = aesGcmEncrypt(key, plaintext, aad);
    expect(aesGcmDecrypt(key, blob, aad).equals(plaintext)).toBe(true);
    expect(() => aesGcmDecrypt(key, blob, Buffer.from('device-456', 'utf8'))).toThrow();
  });

  it('rejects keys that are not 32 bytes', () => {
    const shortKey = Buffer.alloc(16);
    expect(() => aesGcmEncrypt(shortKey, Buffer.from('x'))).toThrow();
  });
});

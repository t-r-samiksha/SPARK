import { sha256, sha256Base64url } from '../../src/crypto';
import { createHash } from 'crypto';

describe('sha256', () => {
  it('matches Node crypto directly', () => {
    const data = Buffer.from('spark transaction bytes', 'utf8');
    const expected = createHash('sha256').update(data).digest();
    expect(sha256(data).equals(expected)).toBe(true);
  });

  it('is deterministic for the same input', () => {
    const data = Buffer.from('deterministic input', 'utf8');
    expect(sha256(data).equals(sha256(data))).toBe(true);
  });

  it('differs for different input', () => {
    const a = sha256(Buffer.from('a', 'utf8'));
    const b = sha256(Buffer.from('b', 'utf8'));
    expect(a.equals(b)).toBe(false);
  });

  it('base64url encoding is unpadded and URL-safe', () => {
    const encoded = sha256Base64url(Buffer.from('some content', 'utf8'));
    expect(encoded).not.toMatch(/[+/=]/);
    // 32 raw bytes -> 43 base64url chars, unpadded.
    expect(encoded.length).toBe(43);
  });
});

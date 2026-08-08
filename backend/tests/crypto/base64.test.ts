import { base64urlDecode, base64urlEncode } from '../../src/crypto';

describe('base64url', () => {
  it('roundtrips arbitrary bytes', () => {
    const original = Buffer.from('spark test payload with padding needs', 'utf8');
    expect(base64urlDecode(base64urlEncode(original)).equals(original)).toBe(true);
  });

  it('never emits padding or the standard-base64 alphabet characters', () => {
    const encoded = base64urlEncode(Buffer.from([0xfb, 0xff, 0xfe, 0x00, 0x01]));
    expect(encoded).not.toMatch(/[+/=]/);
  });

  it('decodes the documented test-vector public key to 32 raw bytes', () => {
    const decoded = base64urlDecode('luMvVjfGi9lT4_L0t1lrAeKzda6qfdamp4v_yQzpBVE');
    expect(decoded.length).toBe(32);
  });
});

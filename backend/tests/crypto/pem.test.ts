import { pemDecode, pemEncode } from '../../src/crypto';

describe('PEM envelope', () => {
  it('roundtrips arbitrary bytes through encode/decode', () => {
    const original = Buffer.from(JSON.stringify({ hello: 'world', n: 42 }), 'utf8');
    const pem = pemEncode('SPARK DEVICE CERTIFICATE', original);
    expect(pemDecode('SPARK DEVICE CERTIFICATE', pem).equals(original)).toBe(true);
  });

  it('wraps lines at 64 characters', () => {
    const original = Buffer.alloc(200, 1);
    const pem = pemEncode('SPARK PURSE TOKEN', original);
    const lines = pem.split('\n').slice(1, -1); // drop BEGIN/END lines
    for (const line of lines.slice(0, -1)) {
      expect(line.length).toBe(64);
    }
  });

  it('uses standard base64 alphabet (padded), not base64url', () => {
    // Choose bytes whose standard-base64 encoding is known to contain '+' or '/'.
    const original = Buffer.from([0xfb, 0xff, 0xfe]);
    const pem = pemEncode('SPARK TRUST ATTESTATION', original);
    const body = pem.split('\n').slice(1, -1).join('');
    expect(body).toBe('+//+'); // standard base64 of fb ff fe, padded
  });

  it('rejects a PEM string with a mismatched label', () => {
    const pem = pemEncode('SPARK DEVICE CERTIFICATE', Buffer.from('x'));
    expect(() => pemDecode('SPARK PURSE TOKEN', pem)).toThrow();
  });
});

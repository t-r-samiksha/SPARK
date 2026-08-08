import { canonicalizeForSigning, canonicalizeFull } from '../../src/crypto';

describe('canonical serialization', () => {
  it('sorts object keys lexicographically regardless of insertion order', () => {
    const inOrder = canonicalizeForSigning({ device_id: 'x', amount: '25000' });
    const outOfOrder = canonicalizeForSigning({ amount: '25000', device_id: 'x' });
    expect(inOrder.toString('utf8')).toBe('{"amount":"25000","device_id":"x"}');
    expect(outOfOrder.toString('utf8')).toBe(inOrder.toString('utf8'));
  });

  it('sorts nested object keys at every level', () => {
    const bytes = canonicalizeForSigning({
      payee: { device_id: 'b', account_id: 'a' },
      payer: { account_id: 'c', device_id: 'd' },
    });
    expect(bytes.toString('utf8')).toBe(
      '{"payee":{"account_id":"a","device_id":"b"},"payer":{"account_id":"c","device_id":"d"}}',
    );
  });

  it('produces compact output with no whitespace', () => {
    const bytes = canonicalizeForSigning({ b: 2, a: 1 });
    expect(bytes.toString('utf8')).toBe('{"a":1,"b":2}');
    expect(bytes.toString('utf8')).not.toMatch(/\s/);
  });

  it('is deterministic across repeated calls', () => {
    const obj = { z: 1, a: { y: 2, x: 3 }, m: [3, 1, 2] };
    expect(canonicalizeForSigning(obj).toString('utf8')).toBe(
      canonicalizeForSigning(obj).toString('utf8'),
    );
  });

  it('excludes the top-level signature field when signing', () => {
    const bytes = canonicalizeForSigning({ amount: '25000', device_id: 'x', signature: 'ABC' });
    expect(bytes.toString('utf8')).toBe('{"amount":"25000","device_id":"x"}');
  });

  it('includes signature when canonicalizing the full object (PEM-envelope layer)', () => {
    const bytes = canonicalizeFull({ amount: '25000', device_id: 'x', signature: 'ABC' });
    expect(bytes.toString('utf8')).toBe('{"amount":"25000","device_id":"x","signature":"ABC"}');
  });

  it('normalizes string values to NFC', () => {
    // "é" as combining sequence (e + U+0301) vs. precomposed (U+00E9) must canonicalize identically.
    const decomposed = 'é';
    const precomposed = 'é';
    expect(decomposed).not.toBe(precomposed);
    const a = canonicalizeForSigning({ name: decomposed });
    const b = canonicalizeForSigning({ name: precomposed });
    expect(a.toString('utf8')).toBe(b.toString('utf8'));
  });

  it('leaves already-base64url-encoded binary fields untouched as opaque strings', () => {
    const bytes = canonicalizeForSigning({ prev_tx_hash: 'kZ7X2mN4pQ8rS1tU6vW9xY0zA3bC5dE7fG9hJ1kL3mN' });
    expect(bytes.toString('utf8')).toBe('{"prev_tx_hash":"kZ7X2mN4pQ8rS1tU6vW9xY0zA3bC5dE7fG9hJ1kL3mN"}');
  });

  it('serializes arrays compactly preserving order', () => {
    const bytes = canonicalizeForSigning({ list: [3, 1, 2] });
    expect(bytes.toString('utf8')).toBe('{"list":[3,1,2]}');
  });

  it('serializes null values', () => {
    const bytes = canonicalizeForSigning({ prev_tx_hash: null });
    expect(bytes.toString('utf8')).toBe('{"prev_tx_hash":null}');
  });
});

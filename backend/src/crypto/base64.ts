// Two base64 variants are used deliberately, at two different layers — see
// docs/crypto.md#encoding-decided. Never conflate them:
//   - base64url unpadded: values *inside* JSON (keys, signatures, hashes).
//   - standard PEM base64, padded, 64-char lines: the outer envelope around a whole
//     cert/purse-token/trust-attestation JSON object (see pem.ts).

export function base64urlEncode(data: Buffer): string {
  return data.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function base64urlDecode(value: string): Buffer {
  let padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const remainder = padded.length % 4;
  if (remainder === 1) {
    throw new Error(`invalid base64url length: ${value.length}`);
  }
  if (remainder > 0) {
    padded += '='.repeat(4 - remainder);
  }
  return Buffer.from(padded, 'base64');
}

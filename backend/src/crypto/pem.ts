// Standard PEM envelope handling (RFC 7468) for certs/purse-tokens/trust-attestations. This is
// the *outer* container around a whole canonical-JSON object (including its `signature` field) —
// see docs/canonical-serialization.md#pem-envelopes-vs-field-encoding. Distinct from the
// base64url used for individual field values inside that JSON (see base64.ts).

const LINE_LENGTH = 64;

export function pemEncode(label: string, data: Buffer): string {
  const body = data.toString('base64');
  const lines: string[] = [];
  for (let i = 0; i < body.length; i += LINE_LENGTH) {
    lines.push(body.slice(i, i + LINE_LENGTH));
  }
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----`;
}

export function pemDecode(label: string, pem: string): Buffer {
  const begin = `-----BEGIN ${label}-----`;
  const end = `-----END ${label}-----`;
  const beginIdx = pem.indexOf(begin);
  const endIdx = pem.indexOf(end);
  if (beginIdx === -1 || endIdx === -1 || endIdx < beginIdx) {
    throw new Error(`invalid PEM envelope: expected ${begin} / ${end}`);
  }
  const body = pem
    .slice(beginIdx + begin.length, endIdx)
    .split('\n')
    .map((line) => line.trim())
    .join('');
  return Buffer.from(body, 'base64');
}

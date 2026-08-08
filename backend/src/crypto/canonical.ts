// Canonical JSON serialization per docs/canonical-serialization.md. This is the single most
// important cross-team contract in the repo: Android and backend must produce byte-identical
// output for the same logical object, or signatures won't verify across implementations.
//
// Rules implemented here (see the doc for the authoritative list):
//   1. Object keys sorted lexicographically, byte-wise on the UTF-8 key, at every nesting level.
//   2. Compact output — no spaces, no newlines.
//   3. String *values* normalized to Unicode NFC before serialization.
//   4/5. Amounts-as-decimal-strings and binary-fields-as-base64url are NOT this module's job —
//      by the time a caller passes an object here, it must already hold those conventions (e.g.
//      `amount: "25000"` not `amount: 25000`, `signature: "base64url..."` not a Buffer). This
//      module only serializes whatever JS values it's given, deterministically.
//   6. The top-level `signature` field is excluded from the bytes that get signed — see
//      `canonicalizeForSigning` vs. `canonicalizeFull` below.
//   7. Output is UTF-8 encoded before signing/hashing.
//
// Key *names* are not NFC-normalized — they're fixed, ASCII, schema-defined identifiers, not
// user-supplied data, so rule 3 ("string values") is read as applying to field values only.

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

function compareUtf8Bytes(a: string, b: string): number {
  return Buffer.compare(Buffer.from(a, 'utf8'), Buffer.from(b, 'utf8'));
}

function canonicalizeValue(value: JsonValue): string {
  if (value === null) {
    return 'null';
  }
  if (typeof value === 'boolean' || typeof value === 'number') {
    return JSON.stringify(value);
  }
  if (typeof value === 'string') {
    return JSON.stringify(value.normalize('NFC'));
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalizeValue).join(',')}]`;
  }
  if (typeof value === 'object') {
    const keys = Object.keys(value).sort(compareUtf8Bytes);
    const entries = keys.map(
      (key) => `${JSON.stringify(key.normalize('NFC'))}:${canonicalizeValue(value[key])}`,
    );
    return `{${entries.join(',')}}`;
  }
  throw new Error(`cannot canonicalize value of type ${typeof value}`);
}

// Generic over T rather than typed directly as Record<string, JsonValue>, because callers pass
// concrete interfaces (Certificate, Transaction, ...) that don't declare a `[key: string]` index
// signature. NOTE: this only gets you so far — TypeScript still rejects a *variable* of one of
// those interface types at the call site ("Index signature for type 'string' is missing"),
// because a named variable isn't a "fresh" object literal. Spread it into one at the call site,
// e.g. `canonicalizeFull({ ...certificate })`, not `canonicalizeFull(certificate)`.

/** Canonicalize the full object, including `signature` if present. Used for the PEM envelope
 * layer (applied once the signed object is finished), never for producing signing input. */
export function canonicalizeFull<T extends Record<string, JsonValue>>(obj: T): Buffer {
  return Buffer.from(canonicalizeValue(obj), 'utf8');
}

/** Canonicalize everything except the top-level `signature` field — this is what gets signed and
 * what a verifier must reconstruct before checking a signature. */
export function canonicalizeForSigning<T extends Record<string, JsonValue>>(obj: T): Buffer {
  const { signature: _signature, ...rest } = obj;
  return Buffer.from(canonicalizeValue(rest), 'utf8');
}

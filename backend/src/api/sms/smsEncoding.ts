import { base64urlDecode, base64urlEncode } from '../../crypto';
import { Transaction } from '../../types';

// Phase 9 — SMS rail (simulated). SMS ENCODING FORMAT, decided here:
//
// A real signed Transaction (tx_id, token_id, amount, payer/payee {device_id, account_id, cert},
// device_counter, prev_tx_hash, timestamp, signature) is dominated by the two embedded PEM
// certificate envelopes — each is itself a base64-wrapped JSON object with its own signature — so
// it is FAR too long for a single 160-character GSM-7 SMS segment (realistically several
// kilobytes once both certs are included). Two ways to handle that were considered:
//
//   1. Multi-part reassembly: split the base64url payload across N messages with a
//      `{msg_index}/{total}:{payload}` prefix, buffer parts server-side keyed by `from`, and
//      reassemble once all parts arrive.
//   2. Assume, for the hackathon demo, that a single "SMS" carries the full base64url-encoded
//      transaction — i.e. simulate the SMS rail's semantics (an opaque text payload arriving over
//      an untrusted transport) without simulating its 160-char segment limit or building
//      multi-part reassembly, retry/timeout, and out-of-order-arrival handling that would go with
//      it.
//
// DECIDED: option 2. The point of this feature is to demonstrate that a signed transaction is
// self-contained and verifiable over ANY transport — the transport's own framing limits are a
// separate, real-world SMS-gateway integration concern, not something this settlement logic cares
// about. Building real multi-part reassembly here would be exactly the kind of "SMS
// infrastructure" the task asks NOT to build, for a demo that doesn't need it to prove the thesis.
// KNOWN SIMPLIFICATION, flagged for whoever wires this to a real gateway: production would need
// either multi-part reassembly (option 1 above) or, more realistically, a compact
// reference-based encoding (e.g. the SMS carries a short pre-registered transaction reference/ID
// that the device separately uploaded over data when it had connectivity, not the full signed
// payload) — a real GSM-7 SMS cannot carry a raw cert-embedded transaction in one segment.
//
// Encoding itself: base64url(JSON.stringify(transaction)) — plain compact JSON, NOT this
// codebase's canonicalizeFull(). Canonical form (sorted keys, NFC-normalized strings) only matters
// for the SIGNING/verification computation, which engine.ts redoes from the DECODED object
// regardless of the key order or whitespace it arrived in — JSON.parse produces an equivalent
// object either way. Reusing canonicalizeFull for transport would add complexity (and its
// JsonValue-cast ceremony — see engine.ts's hashTransaction) for zero correctness benefit. Also
// deliberately NOT the pemEncode() PEM-envelope convention used for certs/purse-tokens/
// attestations: that's a verbose, multi-line, labeled format meant for storage/display, the
// opposite of what an SMS body needs.

/** Encodes a signed Transaction as a single SMS body — what a real device's SMS client would send
 * as the message text. Used by tests to construct request bodies; the inverse of
 * decodeTransactionSms below. */
export function encodeTransactionSms(transaction: Transaction): string {
  return base64urlEncode(Buffer.from(JSON.stringify(transaction), 'utf8'));
}

/**
 * Decodes an inbound SMS body back into a Transaction object. Deliberately does NOT validate the
 * result's shape beyond "valid base64url containing valid JSON" — POST /sms/inbound feeds the
 * result straight into settleTransactionBatch() (the exact same settlement path
 * POST /sync/transactions uses), which already fully verifies everything about a transaction that
 * matters (cert trust, signature, counter/hash chain, caps). Re-checking shape here would
 * duplicate that verification instead of reusing it. A structurally-wrong result (missing fields,
 * wrong types) surfaces as a thrown error from the settlement engine instead of from here — the
 * caller (the route handler) catches both this function's and settleTransactionBatch's errors the
 * same way.
 */
export function decodeTransactionSms(body: string): Transaction {
  const json = base64urlDecode(body.trim()).toString('utf8');
  return JSON.parse(json) as Transaction;
}

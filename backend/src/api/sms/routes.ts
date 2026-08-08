import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { settleTransactionBatch, TxSettlementResult } from '../../settlement/engine';
import { decodeTransactionSms } from './smsEncoding';

// Phase 9 — SMS rail (simulated, no Twilio account or SDK involved; see smsEncoding.ts for the
// chosen encoding format). Not in docs/api-contract.md — added later, like escrow and family
// wallet before it.
//
// SECURITY BOUNDARY, decided deliberately: unlike every other endpoint in this system, this one
// requires NO session (no Authorization header at all) — a real inbound-SMS webhook (Twilio or
// otherwise) has no device session bearer token to present; the "sender" is a phone number, not
// an authenticated API caller. That's fine, because this endpoint's HTTP-layer trust is a
// non-issue: nothing state-changing depends on WHO called this webhook, only on whether the
// transaction ENCODED INSIDE the SMS body carries a valid payer signature over a valid, unrevoked,
// Bank-issued certificate. That check happens inside settleTransactionBatch — the exact same
// verification POST /sync/transactions relies on, called directly here so this endpoint cannot
// drift from it. In other words: the SMS transport (and this webhook) is untrusted; the payload is
// not, and is what's actually verified. A real deployment would likely still want to verify the
// request came from the actual SMS gateway (e.g. Twilio's request-signature header) to prevent
// spam/DoS against this endpoint — out of scope here, same "not a production anti-abuse system"
// spirit as the global rate limiter already covers request volume generically.
interface SmsInboundBody {
  from: string;
  body: string;
}

const smsInboundSchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['from', 'body'],
    properties: {
      // Freeform per the task — a real gateway's sender field varies by carrier/format (E.164,
      // short code, etc.); nothing here parses or trusts it, so no pattern is enforced.
      from: { type: 'string', minLength: 1 },
      body: { type: 'string', minLength: 1 },
    },
  },
};

type SmsInboundResponse = { status: 'received'; detail: TxSettlementResult } | { status: 'error'; detail: string };

export default async function smsRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: SmsInboundBody }>(
    '/sms/inbound',
    { schema: smsInboundSchema },
    async (request: FastifyRequest<{ Body: SmsInboundBody }>, reply: FastifyReply) => {
      // Two independent failure surfaces, both handled the same way (200 + status: "error", never
      // a 500): (1) the SMS body isn't valid base64url, or decodes to something that isn't valid
      // JSON — decodeTransactionSms throws directly for both. (2) the decoded JSON IS valid but
      // isn't shaped like a real Transaction (missing/wrong-typed fields) — settleTransactionBatch
      // doesn't defensively shape-check its input (it's normally fed already-schema-validated
      // objects by POST /sync/transactions), so a malformed object surfaces as a thrown error from
      // deep inside settlement (e.g. reading `.device_id` off an absent `payer`) rather than a
      // clean rejection result. Catching broadly here — instead of re-validating the decoded
      // shape ourselves before calling settleTransactionBatch — is what keeps this endpoint from
      // duplicating verification logic that already lives in the engine.
      //
      // A 200 (not 400) is deliberate for both cases: Fastify's own schema above already returns
      // 400 if the WRAPPER shape ({from, body} as strings) is wrong. Once past that, an
      // undecodable or malformed payload is a normal, expected outcome for a webhook receiving
      // arbitrary carrier-relayed text — not a caller-request-format error — so it gets a 200 with
      // status: "error" in the body, mirroring how a real SMS gateway expects a 2xx acknowledgment
      // regardless of what the webhook did with the message internally.
      let transaction;
      try {
        transaction = decodeTransactionSms(request.body.body);
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err);
        return reply.code(200).send({ status: 'error', detail: `could not decode SMS body: ${detail}` } satisfies SmsInboundResponse);
      }

      try {
        // Single-transaction batch through the EXACT SAME settlement path POST /sync/transactions
        // uses — cert trust, signature, double-spend detection (within-batch and against history),
        // counter/hash-chain continuity, and persistence are all identical either way. SMS is just
        // another transport a transaction can arrive over, not a different trust path.
        const { results } = await settleTransactionBatch([transaction]);
        return reply.code(200).send({ status: 'received', detail: results[0] } satisfies SmsInboundResponse);
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err);
        return reply.code(200).send({ status: 'error', detail: `settlement failed: ${detail}` } satisfies SmsInboundResponse);
      }
    },
  );
}

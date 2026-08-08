import { randomUUID } from 'crypto';
import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { requireSession } from '../auth/requireSession';
import { canonicalizeForSigning, canonicalizeFull, ed25519Verify, pemEncode } from '../../crypto';
import { hashTransaction, reconstructTransaction } from '../../settlement/engine';
import { upsertTrustEdge } from '../../settlement/trustEdges';
import { getAvailablePurseBalance } from '../../settlement/escrowLedger';

// Phase 9 — escrow, COOPERATIVE CASE ONLY. Not in docs/api-contract.md at all (this feature
// predates any contract doc coverage) — true trustless escrow needs a neutral third-party holder
// this system doesn't have offline, so the model here is "buyer locks funds, buyer later signs
// their own confirmation that the condition was met, the Bank just settles it." The Bank is a
// bookkeeper, not an arbiter: there is no dispute-resolution logic (see POST /escrow/dispute).

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DECIMAL_PAISE_PATTERN = /^[0-9]+$/;
const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

function toIso8601(date: Date): string {
  return `${date.toISOString().split('.')[0]}Z`;
}

interface DeviceRow {
  deviceId: string;
  accountId: string;
  devicePublicKey: string;
  serialNumber: string;
  notBefore: Date;
  notAfter: Date;
  signature: string;
  revokedAt: Date | null;
}

/**
 * Rebuilds a device's certificate PEM from its Device row rather than looking one up — Device
 * stores every raw field a Certificate needs (see certificate-format.md) but not the PEM string
 * itself. The signature is already valid for these exact field values (it was computed once at
 * /enroll and never changes), so this just re-wraps it — no signing happens here.
 */
function reconstructCertificatePem(device: DeviceRow): string {
  const cert = {
    device_id: device.deviceId,
    account_id: device.accountId,
    device_public_key: device.devicePublicKey,
    serial_number: device.serialNumber,
    not_before: toIso8601(device.notBefore),
    not_after: toIso8601(device.notAfter),
    signature: device.signature,
  };
  return pemEncode(CERT_PEM_LABEL, canonicalizeFull(cert));
}

interface EscrowRow {
  id: string;
  buyerDeviceId: string;
  sellerDeviceId: string;
  tokenId: string;
  amount: string;
  condition: string;
  status: string;
  disputeReason: string | null;
  createdAt: Date;
  resolvedAt: Date | null;
}

function serializeEscrow(escrow: EscrowRow) {
  return {
    id: escrow.id,
    buyer_device_id: escrow.buyerDeviceId,
    seller_device_id: escrow.sellerDeviceId,
    token_id: escrow.tokenId,
    amount: escrow.amount,
    condition: escrow.condition,
    status: escrow.status,
    dispute_reason: escrow.disputeReason,
    created_at: Math.floor(escrow.createdAt.getTime() / 1000),
    resolved_at: escrow.resolvedAt ? Math.floor(escrow.resolvedAt.getTime() / 1000) : null,
  };
}

export default async function escrowRoutes(fastify: FastifyInstance): Promise<void> {
  // --- POST /escrow/create -----------------------------------------------------------------------
  interface EscrowCreateBody {
    buyer_device_id: string;
    seller_device_id: string;
    amount: string;
    condition: string;
  }

  const escrowCreateSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['buyer_device_id', 'seller_device_id', 'amount', 'condition'],
      properties: {
        buyer_device_id: { type: 'string' },
        seller_device_id: { type: 'string' },
        amount: { type: 'string' },
        condition: { type: 'string', minLength: 1 },
      },
    },
  };

  fastify.post<{ Body: EscrowCreateBody }>(
    '/escrow/create',
    { schema: escrowCreateSchema },
    async (request: FastifyRequest<{ Body: EscrowCreateBody }>, reply: FastifyReply) => {
      const sessionDeviceId = requireSession(request, reply);
      if (!sessionDeviceId) {
        return;
      }

      const { buyer_device_id, seller_device_id, amount, condition } = request.body;

      if (!UUID_V4_PATTERN.test(buyer_device_id) || !UUID_V4_PATTERN.test(seller_device_id)) {
        return reply
          .code(400)
          .send({ error: 'buyer_device_id and seller_device_id must be canonical (lowercase) UUID v4' });
      }
      if (buyer_device_id === seller_device_id) {
        return reply.code(400).send({ error: 'buyer_device_id and seller_device_id must be different devices' });
      }
      if (!DECIMAL_PAISE_PATTERN.test(amount)) {
        return reply.code(400).send({ error: 'amount must be a decimal-paise string, e.g. "25000"' });
      }
      // The buyer initiates escrow — the caller's own session must belong to the buyer device,
      // not just any authenticated device (unlike /escrow/release, where the caller and the party
      // being acted on can differ — see that handler).
      if (sessionDeviceId !== buyer_device_id) {
        return reply.code(403).send({ error: 'session does not belong to the buyer device' });
      }

      // "An active purse token" = the buyer's most recent, non-expired one — same lookup pattern
      // POST /purse/load uses for counter_start continuity.
      const token = await prisma.purseToken.findFirst({
        where: { deviceId: buyer_device_id },
        orderBy: { createdAt: 'desc' },
      });
      const nowEpoch = Math.floor(Date.now() / 1000);
      if (!token || token.expiry < nowEpoch) {
        return reply.code(400).send({ error: 'buyer has no active (non-expired) purse token' });
      }

      const requestedAmount = BigInt(amount);
      // Same per-spend ceiling a normal transaction is held to — see escrowLedger.ts for why
      // escrow amounts count against cap/value the same way a settled transaction would.
      if (requestedAmount > BigInt(token.cap)) {
        return reply.code(400).send({ error: `amount (${amount}) exceeds the purse token's cap (${token.cap})` });
      }

      const available = await getAvailablePurseBalance(token.value, token.tokenId);
      if (requestedAmount > available) {
        return reply.code(400).send({
          error: `insufficient available balance: requested ${amount} paise, ${available.toString()} paise available (token value ${token.value} minus settled + locked)`,
        });
      }

      const escrow = await prisma.escrowContract.create({
        data: {
          buyerDeviceId: buyer_device_id,
          sellerDeviceId: seller_device_id,
          tokenId: token.tokenId,
          amount,
          condition,
          status: 'locked',
        },
      });

      return reply.code(200).send(serializeEscrow(escrow));
    },
  );

  // --- POST /escrow/release ----------------------------------------------------------------------
  interface EscrowReleaseBody {
    escrow_id: string;
    buyer_signature: string;
  }

  const escrowReleaseSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['escrow_id', 'buyer_signature'],
      properties: {
        escrow_id: { type: 'string' },
        buyer_signature: { type: 'string' },
      },
    },
  };

  fastify.post<{ Body: EscrowReleaseBody }>(
    '/escrow/release',
    { schema: escrowReleaseSchema },
    async (request: FastifyRequest<{ Body: EscrowReleaseBody }>, reply: FastifyReply) => {
      // Any authenticated device may submit a release, not just the buyer — the buyer might sign
      // this confirmation offline and hand it to the (online) seller to submit, matching the
      // offline-first pattern the rest of this system uses. Authorization comes entirely from the
      // signature check below, not from who the caller's session belongs to.
      if (!requireSession(request, reply)) {
        return;
      }

      const { escrow_id, buyer_signature } = request.body;
      if (!UUID_V4_PATTERN.test(escrow_id)) {
        return reply.code(400).send({ error: 'escrow_id must be a canonical (lowercase) UUID v4' });
      }

      const escrow = await prisma.escrowContract.findUnique({ where: { id: escrow_id } });
      if (!escrow) {
        return reply.code(404).send({ error: 'escrow not found' });
      }
      if (escrow.status !== 'locked') {
        return reply.code(409).send({ error: `escrow is not locked (current status: ${escrow.status})` });
      }

      const buyerDevice = await prisma.device.findUnique({ where: { deviceId: escrow.buyerDeviceId } });
      if (!buyerDevice) {
        return reply.code(404).send({ error: 'buyer device not found' });
      }
      if (buyerDevice.revokedAt) {
        return reply.code(401).send({ error: 'buyer device certificate has been revoked' });
      }

      // Signed payload is {escrow_id, condition} — NOT the normal transaction-signing rule (see
      // the schema.prisma comment on Transaction.escrowContractId). This is the buyer's
      // out-of-band confirmation that the condition was met, not a device-signed transaction over
      // this escrow's own canonical fields.
      const signingBytes = canonicalizeForSigning({ escrow_id: escrow.id, condition: escrow.condition });
      if (!ed25519Verify(buyerDevice.devicePublicKey, signingBytes, buyer_signature)) {
        return reply.code(401).send({ error: 'invalid buyer signature' });
      }

      const sellerDevice = await prisma.device.findUnique({ where: { deviceId: escrow.sellerDeviceId } });
      if (!sellerDevice) {
        return reply.code(404).send({ error: 'seller device not found' });
      }

      const token = await prisma.purseToken.findUnique({ where: { tokenId: escrow.tokenId } });
      if (!token) {
        // Shouldn't happen — the token existed at /escrow/create time and purse tokens are never
        // deleted. Defensive only.
        return reply.code(404).send({ error: 'purse token backing this escrow was not found' });
      }

      const updated = await prisma.$transaction(async (tx) => {
        // Extends the SAME device_counter/prev_tx_hash chain normal synced transactions for this
        // token use (see engine.ts's Phase 4) — an escrow settlement is a real spend against the
        // token and must slot into its history the same way, so future /sync/transactions calls
        // and /escrow/create balance checks see one complete, gapless picture rather than two
        // disconnected ones.
        //
        // The buyer's own OFFLINE device has no idea this counter slot / hash link was consumed
        // server-side until its next sync — GET /sync/updates' `escrow_settlements` field closes
        // that gap by handing the device this exact row (reconstructed as a normal Transaction) so
        // it can append it to its local ledger and re-chain its next real transaction on top of
        // it. If the device's next signed transaction reuses this counter anyway (stale local
        // state, raced with this settlement), doubleSpendResolver.ts's isEscrowSettlement check
        // rejects it with a "stale counter, re-sync required" error instead of treating it as
        // fraud — see engine.ts's Phase 3.
        const lastSettled = await tx.transaction.findFirst({
          where: { tokenId: escrow.tokenId },
          orderBy: { deviceCounter: 'desc' },
        });
        const deviceCounter = lastSettled ? lastSettled.deviceCounter + 1 : token.counterStart;
        const prevTxHash = lastSettled ? hashTransaction(reconstructTransaction(lastSettled)) : null;

        await tx.transaction.create({
          data: {
            txId: randomUUID(),
            tokenId: escrow.tokenId,
            amount: escrow.amount,
            payerDeviceId: escrow.buyerDeviceId,
            payerAccountId: buyerDevice.accountId,
            payerCert: reconstructCertificatePem(buyerDevice),
            payeeDeviceId: escrow.sellerDeviceId,
            payeeAccountId: sellerDevice.accountId,
            payeeCert: reconstructCertificatePem(sellerDevice),
            deviceCounter,
            prevTxHash,
            timestamp: Math.floor(Date.now() / 1000),
            // The buyer's escrow-release signature, not a normal transaction signature — see the
            // schema.prisma comment on escrowContractId. Verifying it requires knowing this is an
            // escrow-settled row (escrowContractId is set) and checking it against
            // {escrow_id, condition}, not against this transaction's own canonical fields.
            signature: buyer_signature,
            escrowContractId: escrow.id,
          },
        });

        // Same trust-edge update a normal settled transaction triggers (src/settlement/trustEdges.ts)
        // — an escrow release IS a settlement between these two devices.
        await upsertTrustEdge(tx, escrow.buyerDeviceId, escrow.sellerDeviceId, escrow.amount);

        return tx.escrowContract.update({
          where: { id: escrow.id },
          data: { status: 'released', resolvedAt: new Date() },
        });
      });

      return reply.code(200).send(serializeEscrow(updated));
    },
  );

  // --- POST /escrow/dispute ----------------------------------------------------------------------
  interface EscrowDisputeBody {
    escrow_id: string;
    reason: string;
  }

  const escrowDisputeSchema = {
    body: {
      type: 'object',
      additionalProperties: false,
      required: ['escrow_id', 'reason'],
      properties: {
        escrow_id: { type: 'string' },
        reason: { type: 'string', minLength: 1 },
      },
    },
  };

  fastify.post<{ Body: EscrowDisputeBody }>(
    '/escrow/dispute',
    { schema: escrowDisputeSchema },
    async (request: FastifyRequest<{ Body: EscrowDisputeBody }>, reply: FastifyReply) => {
      const sessionDeviceId = requireSession(request, reply);
      if (!sessionDeviceId) {
        return;
      }

      const { escrow_id, reason } = request.body;
      if (!UUID_V4_PATTERN.test(escrow_id)) {
        return reply.code(400).send({ error: 'escrow_id must be a canonical (lowercase) UUID v4' });
      }

      const escrow = await prisma.escrowContract.findUnique({ where: { id: escrow_id } });
      if (!escrow) {
        return reply.code(404).send({ error: 'escrow not found' });
      }
      if (sessionDeviceId !== escrow.buyerDeviceId && sessionDeviceId !== escrow.sellerDeviceId) {
        return reply.code(403).send({ error: 'only the buyer or seller may dispute this escrow' });
      }
      if (escrow.status !== 'locked') {
        return reply
          .code(409)
          .send({ error: `escrow is not in a disputable state (current status: ${escrow.status})` });
      }

      // No auto-resolution logic — disputes just sit in this state. Matches the spec's explicit
      // "cooperative case" framing: real dispute resolution needs an arbiter/process this
      // hackathon doesn't have. resolvedAt is deliberately NOT set — see the schema.prisma
      // comment on EscrowContract.resolvedAt (disputed is ongoing, not terminal).
      const updated = await prisma.escrowContract.update({
        where: { id: escrow_id },
        data: { status: 'disputed', disputeReason: reason },
      });

      return reply.code(200).send(serializeEscrow(updated));
    },
  );
}

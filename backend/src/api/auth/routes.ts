import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { prisma } from '../../db/client';
import { base64urlDecode, ed25519Verify } from '../../crypto';
import { getNonceRecord, issueNonce, markNonceUsed } from './nonceStore';
import { issueSessionToken } from './sessionStore';

// UUID v4, canonical lowercase form — see docs/id-conventions.md#uuids.
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// POST /api/v1/auth/challenge and POST /api/v1/auth/verify — see docs/api-contract.md
// (Decision 3: /auth/verify takes {device_id, signed_nonce}).
//
// JUDGMENT CALL: docs/api-contract.md's /auth/challenge request schema is `{}` (no properties,
// additionalProperties: false) — i.e. a nonce with no device binding at issuance time. But
// /auth/verify never sends the plaintext nonce back, only {device_id, signed_nonce}, so without
// a device_id at challenge time the server would have to brute-force verify the signature
// against every currently-pending nonce to find a match. We deviate from the doc's literal
// empty-body schema and require device_id in the /auth/challenge request instead, so /auth/verify
// can look the nonce up directly (see nonceStore.ts). Flag this for the next docs sync — the
// OpenAPI schema needs updating to match.
interface ChallengeBody {
  device_id: string;
}

const challengeSchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['device_id'],
    properties: {
      device_id: { type: 'string' },
    },
  },
};

interface VerifyBody {
  device_id: string;
  signed_nonce: string;
}

const verifySchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['device_id', 'signed_nonce'],
    properties: {
      device_id: { type: 'string' },
      signed_nonce: { type: 'string' },
    },
  },
};

export default async function authRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: ChallengeBody }>(
    '/auth/challenge',
    { schema: challengeSchema },
    async (request: FastifyRequest<{ Body: ChallengeBody }>, reply: FastifyReply) => {
      const { device_id } = request.body;
      if (!UUID_V4_PATTERN.test(device_id)) {
        return reply.code(400).send({ error: 'device_id must be a canonical (lowercase) UUID v4' });
      }

      // Deliberately not checking enrollment here — a challenge for an unknown/not-yet-enrolled
      // device_id is harmless (it can never successfully complete /auth/verify) and checking
      // would just be extra work with no security benefit, since 404 already happens at verify.
      const nonce = issueNonce(device_id);
      return reply.code(200).send({ nonce });
    },
  );

  fastify.post<{ Body: VerifyBody }>(
    '/auth/verify',
    { schema: verifySchema },
    async (request: FastifyRequest<{ Body: VerifyBody }>, reply: FastifyReply) => {
      const { device_id, signed_nonce } = request.body;

      const device = await prisma.device.findUnique({ where: { deviceId: device_id } });
      if (!device) {
        return reply.code(404).send({ error: 'device not enrolled' });
      }
      // "current, non-revoked certificate" per docs/api-contract.md's /auth/verify description.
      if (device.revokedAt) {
        return reply.code(401).send({ error: 'device certificate revoked' });
      }

      const record = getNonceRecord(device_id);
      if (!record) {
        return reply.code(401).send({ error: 'no pending nonce for device' });
      }
      if (record.used) {
        return reply.code(401).send({ error: 'nonce already used' });
      }
      if (Date.now() > record.expiresAt) {
        return reply.code(401).send({ error: 'nonce expired' });
      }

      let signatureValid: boolean;
      try {
        const nonceBytes = base64urlDecode(record.nonce);
        signatureValid = ed25519Verify(device.devicePublicKey, nonceBytes, signed_nonce);
      } catch {
        // Malformed base64url in signed_nonce — same externally-visible outcome as a wrong
        // signature, so it gets the same 401 rather than a schema/500-level error.
        signatureValid = false;
      }
      if (!signatureValid) {
        return reply.code(401).send({ error: 'invalid signature' });
      }

      // Mark used only after every other check passes, so a request that fails for an unrelated
      // reason (e.g. hits the revoked-device check) never burns the caller's one legitimate nonce.
      markNonceUsed(device_id);

      const session_token = issueSessionToken(device_id);
      return reply.code(200).send({ session_token });
    },
  );
}

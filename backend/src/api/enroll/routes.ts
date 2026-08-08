import { randomUUID } from 'crypto';
import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { Prisma } from '@prisma/client';
import { prisma } from '../../db/client';
import { base64urlDecode } from '../../crypto';
import { issueCertificate } from './issueCertificate';

// UUID v4, canonical lowercase form — see docs/id-conventions.md#uuids.
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
// base64url, unpadded, 32 raw bytes -> exactly 43 characters — see
// docs/id-conventions.md#public-key-encoding.
const PUBLIC_KEY_LENGTH_BYTES = 32;

interface EnrollBody {
  account_id: string;
  public_key: string;
  attestation_blob: string;
}

const enrollSchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['account_id', 'public_key', 'attestation_blob'],
    properties: {
      account_id: { type: 'string' },
      public_key: { type: 'string' },
      attestation_blob: { type: 'string', minLength: 1 },
    },
  },
};

// POST /api/v1/enroll — see docs/api-contract.md and docs/certificate-format.md#issuance.
export default async function enrollRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.post<{ Body: EnrollBody }>(
    '/enroll',
    { schema: enrollSchema },
    async (request: FastifyRequest<{ Body: EnrollBody }>, reply: FastifyReply) => {
      const { account_id, public_key, attestation_blob } = request.body;

      if (!UUID_V4_PATTERN.test(account_id)) {
        return reply.code(400).send({ error: 'account_id must be a canonical (lowercase) UUID v4' });
      }

      let publicKeyBytes: Buffer;
      try {
        publicKeyBytes = base64urlDecode(public_key);
      } catch {
        return reply.code(400).send({ error: 'public_key must be valid base64url' });
      }
      if (publicKeyBytes.length !== PUBLIC_KEY_LENGTH_BYTES) {
        return reply.code(400).send({
          error: `public_key must decode to ${PUBLIC_KEY_LENGTH_BYTES} raw bytes, got ${publicKeyBytes.length}`,
        });
      }

      // SIMPLIFICATION (hackathon scope): attestation_blob is expected to be an opaque
      // platform-attestation payload (e.g. Play Integrity) — see the open question in
      // docs/certificate-format.md#issuance and docs/api-contract.md's /enroll schema. We do not
      // verify it against Google's hardware attestation root or any other issuer chain here; we
      // only check it's present and non-empty (enforced by the JSON schema above) and log it.
      // TODO before this goes anywhere near production: real attestation verification.
      request.log.info({ attestation_blob_length: attestation_blob.length }, 'accepting attestation_blob without chain verification (hackathon simplification)');

      const existingDevice = await prisma.device.findUnique({ where: { devicePublicKey: public_key } });
      if (existingDevice) {
        return reply.code(409).send({ error: 'device already enrolled' });
      }

      await prisma.account.upsert({
        where: { id: account_id },
        create: { id: account_id },
        update: {},
      });

      const deviceId = randomUUID();
      const { certificate, pem } = issueCertificate({
        device_id: deviceId,
        account_id,
        device_public_key: public_key,
      });

      try {
        await prisma.device.create({
          data: {
            deviceId: certificate.device_id,
            accountId: certificate.account_id,
            devicePublicKey: certificate.device_public_key,
            serialNumber: certificate.serial_number,
            notBefore: new Date(certificate.not_before),
            notAfter: new Date(certificate.not_after),
            signature: certificate.signature,
          },
        });
      } catch (err) {
        if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2002') {
          // Race: another request enrolled the same public key between our check and this
          // insert. Rare in a hackathon-scale deployment, but cheap to handle correctly.
          return reply.code(409).send({ error: 'device already enrolled' });
        }
        throw err;
      }

      return reply.code(200).send({ cert: pem });
    },
  );
}

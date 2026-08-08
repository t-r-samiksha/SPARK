import { randomUUID } from 'crypto';

// In-memory fake for the two Prisma models POST /enroll touches, so this test doesn't need a
// real Postgres instance. Declared entirely inside the factory (not referencing outer-scope
// variables) per Jest's mock-hoisting rules.
jest.mock('../../../src/db/client', () => {
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  return {
    __esModule: true,
    prisma: {
      account: {
        upsert: async ({ where }: { where: { id: string } }) => ({ id: where.id }),
      },
      device: {
        findUnique: async ({ where }: { where: { devicePublicKey: string } }) =>
          devicesByPublicKey.get(where.devicePublicKey) ?? null,
        create: async ({ data }: { data: { devicePublicKey: string } & Record<string, unknown> }) => {
          if (devicesByPublicKey.has(data.devicePublicKey)) {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const { Prisma } = require('@prisma/client');
            throw new Prisma.PrismaClientKnownRequestError('Unique constraint failed on devicePublicKey', {
              code: 'P2002',
              clientVersion: 'test',
            });
          }
          devicesByPublicKey.set(data.devicePublicKey, data);
          return data;
        },
      },
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import {
  canonicalizeForSigning,
  ed25519Verify,
  generateEd25519KeyPair,
  pemDecode,
} from '../../../src/crypto';
import { Certificate } from '../../../src/types';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';
const ISO8601_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

let app: FastifyInstance;
let caPublicKey: string;

beforeAll(async () => {
  const caKeyPair = generateEd25519KeyPair();
  process.env.BANK_ROOT_CA_KEY_SEED = caKeyPair.privateSeed;
  process.env.BANK_SIGNING_KEY_SEED = generateEd25519KeyPair().privateSeed;
  caPublicKey = caKeyPair.publicKey;

  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

async function enroll(body: Record<string, unknown>) {
  const response = await app.inject({ method: 'POST', url: '/api/v1/enroll', payload: body });
  return response;
}

describe('POST /api/v1/enroll', () => {
  it('enrolls a new device and returns a certificate that verifies against the Root CA key', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const accountId = randomUUID();

    const response = await enroll({
      account_id: accountId,
      public_key: publicKey,
      attestation_blob: 'opaque-platform-attestation-payload',
    });

    expect(response.statusCode).toBe(200);
    const { cert } = response.json();
    expect(typeof cert).toBe('string');

    const json = JSON.parse(pemDecode(CERT_PEM_LABEL, cert).toString('utf8')) as Certificate;

    expect(UUID_V4_PATTERN.test(json.device_id)).toBe(true);
    expect(json.account_id).toBe(accountId);
    expect(json.device_public_key).toBe(publicKey);
    expect(json.serial_number).toMatch(/^SPARK-CERT-/);
    expect(ISO8601_PATTERN.test(json.not_before)).toBe(true);
    expect(ISO8601_PATTERN.test(json.not_after)).toBe(true);

    const signingBytes = canonicalizeForSigning({ ...json });
    expect(ed25519Verify(caPublicKey, signingBytes, json.signature)).toBe(true);
  });

  it('rejects a second enrollment of the same device public key with 409', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const body = {
      account_id: randomUUID(),
      public_key: publicKey,
      attestation_blob: 'first-enrollment',
    };

    const first = await enroll(body);
    expect(first.statusCode).toBe(200);

    const second = await enroll({ ...body, attestation_blob: 'second-attempt-same-device' });
    expect(second.statusCode).toBe(409);
    expect(second.json()).toEqual({ error: 'device already enrolled' });
  });

  it('rejects a request missing a required field with 400', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const response = await enroll({ account_id: randomUUID(), public_key: publicKey });
    expect(response.statusCode).toBe(400);
  });

  it('rejects a request with an extra, undeclared field with 400', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const response = await enroll({
      account_id: randomUUID(),
      public_key: publicKey,
      attestation_blob: 'x',
      extra_field: 'not allowed',
    });
    expect(response.statusCode).toBe(400);
  });

  it('rejects a malformed account_id (not a canonical UUID v4) with 400 and a clear message', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const response = await enroll({
      account_id: 'not-a-uuid',
      public_key: publicKey,
      attestation_blob: 'x',
    });
    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatch(/account_id/);
  });

  it('rejects a malformed public_key (wrong decoded length) with 400 and a clear message', async () => {
    const response = await enroll({
      account_id: randomUUID(),
      public_key: 'not-a-valid-32-byte-key',
      attestation_blob: 'x',
    });
    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatch(/public_key/);
  });

  it('rejects an empty attestation_blob with 400', async () => {
    const { publicKey } = generateEd25519KeyPair();
    const response = await enroll({
      account_id: randomUUID(),
      public_key: publicKey,
      attestation_blob: '',
    });
    expect(response.statusCode).toBe(400);
  });
});

import { randomUUID } from 'crypto';

// In-memory fake for the Prisma Device/Account models — supports both lookup shapes used across
// enroll (findUnique by devicePublicKey) and auth (findUnique by deviceId), since these tests
// enroll real devices through the actual /enroll endpoint rather than re-deriving cert-issuance
// logic. Declared entirely inside the factory per Jest's mock-hoisting rules.
jest.mock('../../../src/db/client', () => {
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  return {
    __esModule: true,
    prisma: {
      account: {
        upsert: async ({ where }: { where: { id: string } }) => ({ id: where.id }),
      },
      device: {
        findUnique: async ({ where }: { where: { devicePublicKey?: string; deviceId?: string } }) => {
          if (where.devicePublicKey !== undefined) {
            return devicesByPublicKey.get(where.devicePublicKey) ?? null;
          }
          if (where.deviceId !== undefined) {
            return devicesById.get(where.deviceId) ?? null;
          }
          return null;
        },
        create: async ({ data }: { data: { deviceId: string; devicePublicKey: string } & Record<string, unknown> }) => {
          if (devicesByPublicKey.has(data.devicePublicKey)) {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const { Prisma } = require('@prisma/client');
            throw new Prisma.PrismaClientKnownRequestError('Unique constraint failed on devicePublicKey', {
              code: 'P2002',
              clientVersion: 'test',
            });
          }
          devicesById.set(data.deviceId, data);
          devicesByPublicKey.set(data.devicePublicKey, data);
          return data;
        },
      },
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import { base64urlDecode, ed25519Sign, generateEd25519KeyPair, pemDecode } from '../../../src/crypto';
import { NONCE_TTL_MS } from '../../../src/api/auth/nonceStore';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';

let app: FastifyInstance;

beforeAll(async () => {
  process.env.BANK_ROOT_CA_KEY_SEED = generateEd25519KeyPair().privateSeed;
  process.env.BANK_SIGNING_KEY_SEED = generateEd25519KeyPair().privateSeed;

  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

interface EnrolledDevice {
  deviceId: string;
  publicKey: string;
  privateSeed: string;
}

async function enrollDevice(): Promise<EnrolledDevice> {
  const { publicKey, privateSeed } = generateEd25519KeyPair();
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/enroll',
    payload: {
      account_id: randomUUID(),
      public_key: publicKey,
      attestation_blob: 'test-attestation-blob',
    },
  });
  if (response.statusCode !== 200) {
    throw new Error(`enrollment failed in test setup: ${response.statusCode} ${response.body}`);
  }
  const { cert } = response.json();
  const json = JSON.parse(pemDecode(CERT_PEM_LABEL, cert).toString('utf8'));
  return { deviceId: json.device_id, publicKey, privateSeed };
}

function challenge(deviceId: string) {
  return app.inject({ method: 'POST', url: '/api/v1/auth/challenge', payload: { device_id: deviceId } });
}

function verify(deviceId: string, signedNonce: string) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/auth/verify',
    payload: { device_id: deviceId, signed_nonce: signedNonce },
  });
}

function signNonce(privateSeed: string, nonce: string): string {
  return ed25519Sign(privateSeed, base64urlDecode(nonce));
}

describe('POST /api/v1/auth/challenge + POST /api/v1/auth/verify', () => {
  it('valid challenge -> verify roundtrip succeeds', async () => {
    const device = await enrollDevice();

    const challengeRes = await challenge(device.deviceId);
    expect(challengeRes.statusCode).toBe(200);
    const { nonce } = challengeRes.json();
    expect(typeof nonce).toBe('string');

    const signedNonce = signNonce(device.privateSeed, nonce);
    const verifyRes = await verify(device.deviceId, signedNonce);

    expect(verifyRes.statusCode).toBe(200);
    const { session_token } = verifyRes.json();
    expect(typeof session_token).toBe('string');
    expect(session_token.length).toBeGreaterThan(0);
  });

  it('verify with the wrong device_id fails', async () => {
    const deviceA = await enrollDevice();
    const deviceB = await enrollDevice();

    const challengeRes = await challenge(deviceA.deviceId);
    const { nonce } = challengeRes.json();
    const signedNonce = signNonce(deviceA.privateSeed, nonce);

    // deviceB never requested its own challenge, so there's no pending nonce to match against —
    // this fails regardless of whose signature is attached.
    const verifyRes = await verify(deviceB.deviceId, signedNonce);
    expect(verifyRes.statusCode).toBe(401);
  });

  it('verify with a tampered signature fails', async () => {
    const device = await enrollDevice();

    const challengeRes = await challenge(device.deviceId);
    const { nonce } = challengeRes.json();
    const signedNonce = signNonce(device.privateSeed, nonce);
    const tampered = signedNonce.slice(0, -1) + (signedNonce.endsWith('A') ? 'B' : 'A');

    const verifyRes = await verify(device.deviceId, tampered);
    expect(verifyRes.statusCode).toBe(401);
  });

  it('verify with an expired nonce fails', async () => {
    const device = await enrollDevice();

    const dateSpy = jest.spyOn(Date, 'now');
    const t0 = Date.now();
    dateSpy.mockReturnValue(t0);

    const challengeRes = await challenge(device.deviceId);
    const { nonce } = challengeRes.json();
    const signedNonce = signNonce(device.privateSeed, nonce);

    dateSpy.mockReturnValue(t0 + NONCE_TTL_MS + 1);

    const verifyRes = await verify(device.deviceId, signedNonce);
    expect(verifyRes.statusCode).toBe(401);

    dateSpy.mockRestore();
  });

  it('verify with an already-used nonce fails (replay protection)', async () => {
    const device = await enrollDevice();

    const challengeRes = await challenge(device.deviceId);
    const { nonce } = challengeRes.json();
    const signedNonce = signNonce(device.privateSeed, nonce);

    const first = await verify(device.deviceId, signedNonce);
    expect(first.statusCode).toBe(200);

    const second = await verify(device.deviceId, signedNonce);
    expect(second.statusCode).toBe(401);
  });

  it('verify against a device that was never enrolled fails with 404', async () => {
    const neverEnrolledDeviceId = randomUUID();
    const impostor = generateEd25519KeyPair();

    const challengeRes = await challenge(neverEnrolledDeviceId);
    expect(challengeRes.statusCode).toBe(200);
    const { nonce } = challengeRes.json();
    const signedNonce = signNonce(impostor.privateSeed, nonce);

    const verifyRes = await verify(neverEnrolledDeviceId, signedNonce);
    expect(verifyRes.statusCode).toBe(404);
  });

  it('rejects a challenge request with a malformed device_id with 400', async () => {
    const response = await challenge('not-a-uuid');
    expect(response.statusCode).toBe(400);
  });

  it('rejects a verify request missing signed_nonce with 400', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/verify',
      payload: { device_id: randomUUID() },
    });
    expect(response.statusCode).toBe(400);
  });
});

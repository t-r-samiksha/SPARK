import { randomUUID } from 'crypto';

// In-memory fake covering the models GET /trust/attestations and GET /merchant/:id/trust touch
// (plus /enroll + /auth, needed to set up authenticated test devices). Declared entirely inside
// the factory per Jest's mock-hoisting rules. Trust edges are seeded directly via
// prisma.trustAttestation.create (see seedTrustEdge below) rather than via a full settlement —
// the settlement engine's own trust-edge-writing behavior is covered by
// tests/api/sync/transactions.test.ts; this file is testing the read-side routes.
jest.mock('../../../src/db/client', () => {
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const trustEdgesById = new Map<string, Record<string, unknown> & { id: string; subjectA: string; subjectB: string }>();

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
        findMany: async ({ where }: { where: { accountId: string } }) =>
          [...devicesById.values()].filter((d) => d.accountId === where.accountId),
      },
      trustAttestation: {
        findMany: async ({
          where,
        }: {
          where?: { OR?: Array<{ subjectA?: string; subjectB?: string | { in: string[] } }> };
        } = {}) => {
          const all = [...trustEdgesById.values()];
          if (!where?.OR) {
            return all;
          }
          return all.filter((e) =>
            where.OR!.some((clause) => {
              const matches = (value: string | { in: string[] } | undefined, actual: string) => {
                if (value === undefined) return false;
                if (typeof value === 'string') return value === actual;
                return value.in.includes(actual);
              };
              return matches(clause.subjectA, e.subjectA) || matches(clause.subjectB, e.subjectB);
            }),
          );
        },
        create: async ({ data }: { data: Record<string, unknown> & { subjectA: string; subjectB: string } }) => {
          const record = { id: randomUUID(), ...data };
          trustEdgesById.set(record.id, record);
          return record;
        },
      },
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import {
  base64urlDecode,
  canonicalizeForSigning,
  ed25519Sign,
  ed25519Verify,
  generateEd25519KeyPair,
  pemDecode,
} from '../../../src/crypto';
import { enrollTestDevice, TestDevice } from '../../helpers/makeSignedTx';

const TRUST_ATTESTATION_PEM_LABEL = 'SPARK TRUST ATTESTATION';

let app: FastifyInstance;
let operationalPublicKey: string;
let operationalPrivateSeed: string;

beforeAll(async () => {
  process.env.BANK_ROOT_CA_KEY_SEED = generateEd25519KeyPair().privateSeed;
  const operationalKeyPair = generateEd25519KeyPair();
  operationalPrivateSeed = operationalKeyPair.privateSeed;
  operationalPublicKey = operationalKeyPair.publicKey;
  process.env.BANK_SIGNING_KEY_SEED = operationalPrivateSeed;

  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

async function getSessionToken(device: TestDevice): Promise<string> {
  const challengeRes = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/challenge',
    payload: { device_id: device.deviceId },
  });
  if (challengeRes.statusCode !== 200) {
    throw new Error(`challenge failed in test setup: ${challengeRes.statusCode} ${challengeRes.body}`);
  }
  const { nonce } = challengeRes.json();
  const signedNonce = ed25519Sign(device.privateSeed, base64urlDecode(nonce));

  const verifyRes = await app.inject({
    method: 'POST',
    url: '/api/v1/auth/verify',
    payload: { device_id: device.deviceId, signed_nonce: signedNonce },
  });
  if (verifyRes.statusCode !== 200) {
    throw new Error(`verify failed in test setup: ${verifyRes.statusCode} ${verifyRes.body}`);
  }
  return verifyRes.json().session_token;
}

function toIso8601(date: Date): string {
  return `${date.toISOString().split('.')[0]}Z`;
}

/** Seeds a trust edge directly (bypassing the settlement engine) with a REAL signature computed
 * the same way trustEdges.ts does, so "verifies against the operational public key" assertions
 * below are meaningful rather than trivially true. */
async function seedTrustEdge(params: {
  subjectA: string;
  subjectB: string;
  settledAmount: string;
  settlementCount: number;
}) {
  const { prisma } = jest.requireMock('../../../src/db/client') as {
    prisma: { trustAttestation: { create: (args: { data: Record<string, unknown> }) => Promise<unknown> } };
  };
  const now = new Date();
  const unsigned = {
    subject_a: params.subjectA,
    subject_b: params.subjectB,
    settled_amount: params.settledAmount,
    settlement_count: params.settlementCount,
    timestamp: toIso8601(now),
  };
  const signature = ed25519Sign(operationalPrivateSeed, canonicalizeForSigning(unsigned));
  return prisma.trustAttestation.create({
    data: {
      subjectA: params.subjectA,
      subjectB: params.subjectB,
      settledAmount: params.settledAmount,
      settlementCount: params.settlementCount,
      timestamp: now,
      lastSettledAt: now,
      signature,
    },
  });
}

describe('GET /api/v1/trust/attestations', () => {
  it('returns correctly signed attestations that verify against the operational public key', async () => {
    const deviceA = await enrollTestDevice(app);
    const deviceB = await enrollTestDevice(app);
    const caller = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(caller);

    const [subjectA, subjectB] = [deviceA.deviceId, deviceB.deviceId].sort();
    await seedTrustEdge({ subjectA, subjectB, settledAmount: '25000', settlementCount: 3 });

    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/trust/attestations?subject=${deviceA.deviceId}`,
      headers: { authorization: `Bearer ${sessionToken}` },
    });
    expect(response.statusCode).toBe(200);
    const { attestations } = response.json();
    expect(attestations).toHaveLength(1);

    const json = JSON.parse(pemDecode(TRUST_ATTESTATION_PEM_LABEL, attestations[0]).toString('utf8'));
    expect(json.subject_a).toBe(subjectA);
    expect(json.subject_b).toBe(subjectB);
    expect(json.settled_amount).toBe('25000');
    expect(json.settlement_count).toBe(3);

    const signingBytes = canonicalizeForSigning({ ...json });
    expect(ed25519Verify(operationalPublicKey, signingBytes, json.signature)).toBe(true);
  });

  it('returns an empty array for a subject with no trust edges', async () => {
    const device = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(device);

    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/trust/attestations?subject=${device.deviceId}`,
      headers: { authorization: `Bearer ${sessionToken}` },
    });
    expect(response.statusCode).toBe(200);
    expect(response.json().attestations).toEqual([]);
  });

  it('rejects a request with no Authorization header with 401', async () => {
    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/trust/attestations?subject=${randomUUID()}`,
    });
    expect(response.statusCode).toBe(401);
  });
});

describe('GET /api/v1/merchant/:id/trust', () => {
  it('returns the aggregate reputation bundle for an account', async () => {
    const merchantDevice = await enrollTestDevice(app);
    const counterparty = await enrollTestDevice(app);
    const caller = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(caller);

    const [subjectA, subjectB] = [merchantDevice.deviceId, counterparty.deviceId].sort();
    await seedTrustEdge({ subjectA, subjectB, settledAmount: '40000', settlementCount: 5 });

    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/merchant/${merchantDevice.accountId}/trust`,
      headers: { authorization: `Bearer ${sessionToken}` },
    });
    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.merchant_id).toBe(merchantDevice.accountId);
    expect(body.attestations).toHaveLength(1);
    expect(body.summary).toEqual(
      expect.objectContaining({
        settled_count: 5,
        settled_value: '40000',
        dispute_rate: null,
        delivery_score: null,
        offline_reliability: null,
      }),
    );
    expect(typeof body.summary.last_settled_at).toBe('number');
  });

  it('returns a zeroed bundle for an account with no settlement history', async () => {
    const device = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(device);

    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/merchant/${randomUUID()}/trust`,
      headers: { authorization: `Bearer ${sessionToken}` },
    });
    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.attestations).toEqual([]);
    expect(body.summary.settled_count).toBe(0);
    expect(body.summary.settled_value).toBe('0');
    expect(body.summary.last_settled_at).toBeNull();
  });

  it('rejects a request with no Authorization header with 401', async () => {
    const response = await app.inject({ method: 'GET', url: `/api/v1/merchant/${randomUUID()}/trust` });
    expect(response.statusCode).toBe(401);
  });
});

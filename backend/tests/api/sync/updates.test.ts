import { randomUUID } from 'crypto';

// In-memory fake covering the models GET /sync/updates (and the /enroll + /auth setup these
// tests need) touches. Declared entirely inside the factory per Jest's mock-hoisting rules.
// Exposes `__revokeAt` (test-only, via `jest.requireMock`) so tests can insert a
// RevokedCertificate row at an exact, deterministic epoch-seconds timestamp — real revocation
// timing (via revokeCertificate()'s `new Date()`) is real-wall-clock-based and second-precision,
// which would make delta-cursor tests flaky/timing-dependent if driven off actual elapsed time.
jest.mock('../../../src/db/client', () => {
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const revokedCerts = new Map<string, { certSerial: string; revokedAt: Date; reason: string }>();
  const disasterEvents: Array<Record<string, unknown> & { active: boolean }> = [];
  const trustEdgesById = new Map<string, Record<string, unknown> & { id: string; subjectA: string; subjectB: string }>();
  const transactionsById = new Map<
    string,
    Record<string, unknown> & { txId: string; payerDeviceId: string; escrowContractId: string | null; syncedAt: Date }
  >();

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
        updateMany: async ({
          where,
          data,
        }: {
          where: { deviceId: string };
          data: Record<string, unknown>;
        }) => {
          const device = devicesById.get(where.deviceId);
          if (!device) {
            return { count: 0 };
          }
          Object.assign(device, data);
          return { count: 1 };
        },
      },
      revokedCertificate: {
        findUnique: async ({ where }: { where: { certSerial: string } }) => revokedCerts.get(where.certSerial) ?? null,
        findMany: async ({ where }: { where?: { revokedAt?: { gte: Date } } }) => {
          const all = [...revokedCerts.values()];
          const filtered = where?.revokedAt ? all.filter((r) => r.revokedAt >= where.revokedAt!.gte) : all;
          return filtered.sort((a, b) => a.revokedAt.getTime() - b.revokedAt.getTime());
        },
        upsert: async ({
          where,
          create,
          update,
        }: {
          where: { certSerial: string };
          create: { certSerial: string; reason: string };
          update: { reason?: string };
        }) => {
          const existing = revokedCerts.get(where.certSerial);
          if (!existing) {
            revokedCerts.set(where.certSerial, { certSerial: where.certSerial, revokedAt: new Date(), reason: create.reason });
          } else if (update.reason !== undefined) {
            existing.reason = update.reason;
          }
          return revokedCerts.get(where.certSerial);
        },
      },
      disasterEvent: {
        create: async ({ data }: { data: Record<string, unknown> }) => {
          const record = {
            id: randomUUID(),
            active: true,
            essentialOnly: false,
            higherCap: null,
            startedAt: new Date(),
            endedAt: null,
            ...data,
          };
          disasterEvents.push(record);
          return record;
        },
        findMany: async ({ where }: { where?: { active?: boolean } }) => {
          if (where?.active === undefined) {
            return disasterEvents;
          }
          return disasterEvents.filter((e) => e.active === where.active);
        },
      },
      trustAttestation: {
        findMany: async ({
          where,
        }: {
          where?: { OR?: Array<{ subjectA?: string; subjectB?: string }> };
        } = {}) => {
          const all = [...trustEdgesById.values()];
          if (!where?.OR) {
            return all;
          }
          return all.filter((e) => where.OR!.some((clause) => e.subjectA === clause.subjectA || e.subjectB === clause.subjectB));
        },
        create: async ({ data }: { data: Record<string, unknown> & { subjectA: string; subjectB: string } }) => {
          const record = { id: randomUUID(), ...data };
          trustEdgesById.set(record.id, record);
          return record;
        },
      },
      transaction: {
        findMany: async ({
          where,
        }: {
          where: { payerDeviceId: string; escrowContractId?: { not: null }; syncedAt?: { gte: Date } };
        }) => {
          return [...transactionsById.values()]
            .filter(
              (t) =>
                t.payerDeviceId === where.payerDeviceId &&
                (where.escrowContractId === undefined || t.escrowContractId !== null) &&
                (where.syncedAt === undefined || t.syncedAt >= where.syncedAt.gte),
            )
            .sort((a, b) => a.syncedAt.getTime() - b.syncedAt.getTime());
        },
      },
    },
    __revokeAt: (certSerial: string, epochSeconds: number, reason = 'test revocation') => {
      revokedCerts.set(certSerial, { certSerial, revokedAt: new Date(epochSeconds * 1000), reason });
    },
    __createEscrowSettledTransaction: (data: Record<string, unknown> & { txId: string; payerDeviceId: string; escrowContractId: string }) => {
      transactionsById.set(data.txId, { syncedAt: new Date(), ...data });
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import { base64urlDecode, ed25519Sign, generateEd25519KeyPair, pemDecode } from '../../../src/crypto';
import { enrollTestDevice, TestDevice } from '../../helpers/makeSignedTx';

const { __revokeAt } = jest.requireMock('../../../src/db/client') as {
  __revokeAt: (certSerial: string, epochSeconds: number, reason?: string) => void;
};

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

async function fetchSyncUpdates(sessionToken: string, since?: number) {
  const query = since !== undefined ? `?since=${since}` : '';
  return app.inject({
    method: 'GET',
    url: `/api/v1/sync/updates${query}`,
    headers: { authorization: `Bearer ${sessionToken}` },
  });
}

describe('GET /api/v1/sync/updates', () => {
  it('returns the full current CRL when `since` is omitted', async () => {
    const [deviceA, deviceB, caller] = await Promise.all([
      enrollTestDevice(app),
      enrollTestDevice(app),
      enrollTestDevice(app),
    ]);
    const baseEpoch = Math.floor(Date.now() / 1000);
    __revokeAt(deviceA.serialNumber, baseEpoch);
    __revokeAt(deviceB.serialNumber, baseEpoch + 1);

    const sessionToken = await getSessionToken(caller);
    const response = await fetchSyncUpdates(sessionToken);

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.crl).toEqual(expect.arrayContaining([deviceA.serialNumber, deviceB.serialNumber]));
    expect(body.crl).toHaveLength(2);
    expect(typeof body.crl_cursor).toBe('number');
  });

  it('with `since` set after a revocation, returns only the newer revocation', async () => {
    const [deviceA, deviceB, caller] = await Promise.all([
      enrollTestDevice(app),
      enrollTestDevice(app),
      enrollTestDevice(app),
    ]);
    const sessionToken = await getSessionToken(caller);

    // Deliberately far in the past/future (not just "now" / "now"), so this can't collide with
    // `cursor`'s floored epoch-second value, or with leftover revocations from other tests
    // sharing this file's mock state — see the note on crl_cursor's `>=` filter in routes.ts for
    // why same-second collisions are possible at all.
    const pastEpoch = Math.floor(Date.now() / 1000) - 1000;
    __revokeAt(deviceA.serialNumber, pastEpoch);

    const first = await fetchSyncUpdates(sessionToken);
    expect(first.json().crl).toContain(deviceA.serialNumber);
    const cursor = first.json().crl_cursor as number;

    const futureEpoch = cursor + 1000;
    __revokeAt(deviceB.serialNumber, futureEpoch);

    const second = await fetchSyncUpdates(sessionToken, cursor);
    expect(second.statusCode).toBe(200);
    const body = second.json();
    expect(body.crl).toContain(deviceB.serialNumber);
    expect(body.crl).not.toContain(deviceA.serialNumber);
  });

  it('returns an empty flags array when there are no active disasters', async () => {
    const caller = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(caller);

    const response = await fetchSyncUpdates(sessionToken);
    expect(response.statusCode).toBe(200);
    expect(response.json().flags).toEqual([]);
  });

  it('includes an active disaster event in the flags array', async () => {
    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: { disasterEvent: { create: (args: { data: Record<string, unknown> }) => Promise<unknown> } };
    };
    await prisma.disasterEvent.create({
      data: {
        regionGeo: 'IN-MH',
        type: 'flood',
        active: true,
        higherCap: '500000',
        essentialOnly: true,
      },
    });

    const caller = await enrollTestDevice(app);
    const sessionToken = await getSessionToken(caller);

    const response = await fetchSyncUpdates(sessionToken);
    expect(response.statusCode).toBe(200);
    const { flags } = response.json();
    expect(flags).toHaveLength(1);
    expect(flags[0]).toEqual(
      expect.objectContaining({
        kind: 'disaster',
        type: 'flood',
        region_geo: 'IN-MH',
        active: true,
        higher_cap: '500000',
        essential_only: true,
      }),
    );
    expect(typeof flags[0].started_at).toBe('number');
  });

  it('includes real (non-empty) trust_attestations when the device has settlement history', async () => {
    const deviceA = await enrollTestDevice(app);
    const deviceB = await enrollTestDevice(app);
    const [subjectA, subjectB] = [deviceA.deviceId, deviceB.deviceId].sort();

    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: { trustAttestation: { create: (args: { data: Record<string, unknown> }) => Promise<unknown> } };
    };
    // Signature isn't verified by this test (that's covered in tests/api/trust/routes.test.ts) —
    // this is testing that /sync/updates wires the field to real query results, not that the
    // settlement engine signs correctly.
    await prisma.trustAttestation.create({
      data: {
        subjectA,
        subjectB,
        settledAmount: '12000',
        settlementCount: 2,
        timestamp: new Date(),
        lastSettledAt: new Date(),
        signature: 'placeholder-signature-not-verified-by-this-test',
      },
    });

    const sessionToken = await getSessionToken(deviceA);
    const response = await fetchSyncUpdates(sessionToken);
    expect(response.statusCode).toBe(200);
    const { trust_attestations } = response.json();
    expect(trust_attestations).toHaveLength(1);

    const json = JSON.parse(pemDecode('SPARK TRUST ATTESTATION', trust_attestations[0]).toString('utf8'));
    expect(json.subject_a).toBe(subjectA);
    expect(json.subject_b).toBe(subjectB);
    expect(json.settled_amount).toBe('12000');
    expect(json.settlement_count).toBe(2);
  });

  it('includes escrow-settled transactions for the calling (payer/buyer) device', async () => {
    const buyer = await enrollTestDevice(app);
    const seller = await enrollTestDevice(app);

    const { __createEscrowSettledTransaction } = jest.requireMock('../../../src/db/client') as {
      __createEscrowSettledTransaction: (data: Record<string, unknown>) => void;
    };
    const txId = randomUUID();
    const tokenId = randomUUID();
    const escrowId = randomUUID();
    __createEscrowSettledTransaction({
      txId,
      tokenId,
      amount: '20000',
      payerDeviceId: buyer.deviceId,
      payerAccountId: randomUUID(),
      payerCert: buyer.certPem,
      payeeDeviceId: seller.deviceId,
      payeeAccountId: randomUUID(),
      payeeCert: seller.certPem,
      deviceCounter: 0,
      prevTxHash: null,
      timestamp: Math.floor(Date.now() / 1000),
      signature: 'escrow-release-signature-placeholder',
      escrowContractId: escrowId,
    });

    const sessionToken = await getSessionToken(buyer);
    const response = await fetchSyncUpdates(sessionToken);

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.escrow_settlements).toHaveLength(1);
    expect(body.escrow_settlements[0]).toEqual(
      expect.objectContaining({
        tx_id: txId,
        token_id: tokenId,
        amount: '20000',
        device_counter: 0,
        prev_tx_hash: null,
      }),
    );
    expect(body.escrow_settlements[0].payer.device_id).toBe(buyer.deviceId);
    expect(body.escrow_settlements[0].payee.device_id).toBe(seller.deviceId);
    expect(typeof body.escrow_settlements_cursor).toBe('number');

    // Not returned for the seller — only the payer's local counter chain can go stale this way.
    const sellerSession = await getSessionToken(seller);
    const sellerResponse = await fetchSyncUpdates(sellerSession);
    expect(sellerResponse.json().escrow_settlements).toEqual([]);
  });

  it('rejects a request with no Authorization header with 401', async () => {
    const response = await app.inject({ method: 'GET', url: '/api/v1/sync/updates' });
    expect(response.statusCode).toBe(401);
  });
});

import { randomUUID } from 'crypto';

// Full in-memory fake covering every Prisma model touched across the request chain these tests
// exercise: /enroll, /auth/challenge, /auth/verify, /purse/load, and /sync/transactions.
// Declared entirely inside the factory per Jest's mock-hoisting rules. Exposes
// `__setAccountBalance` (test-only, via `jest.requireMock`) since real /enroll always creates
// accounts at real_balance = 0.
jest.mock('../../../src/db/client', () => {
  const accounts = new Map<string, { id: string; realBalance: bigint }>();
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const purseTokensById = new Map<string, Record<string, unknown> & { deviceId: string; createdAt: Date }>();
  const revokedCerts = new Map<string, { certSerial: string; revokedAt: Date; reason: string }>();
  const incidents: Array<Record<string, unknown>> = [];
  const transactionsById = new Map<string, Record<string, unknown> & { tokenId: string; deviceCounter: number }>();
  const trustEdgesById = new Map<string, Record<string, unknown> & { id: string; subjectA: string; subjectB: string }>();
  const disasterEvents: Array<Record<string, unknown> & { id: string; regionGeo: string; active: boolean; startedAt: Date }> = [];

  const client = {
    account: {
      upsert: async ({ where, create }: { where: { id: string }; create?: { realBalance?: bigint } }) => {
        if (!accounts.has(where.id)) {
          accounts.set(where.id, { id: where.id, realBalance: create?.realBalance ?? 0n });
        }
        return accounts.get(where.id);
      },
      findUnique: async ({ where }: { where: { id: string } }) => accounts.get(where.id) ?? null,
      update: async ({
        where,
        data,
      }: {
        where: { id: string };
        data: { realBalance?: { decrement?: bigint } };
      }) => {
        const account = accounts.get(where.id);
        if (!account) {
          throw new Error(`account not found in fake store: ${where.id}`);
        }
        if (data.realBalance?.decrement !== undefined) {
          account.realBalance -= data.realBalance.decrement;
        }
        return account;
      },
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
        // Match real Prisma's guarantee that unset nullable columns read back as null, not
        // an omitted key.
        const row = { revokedAt: null, revokedReason: null, ...data };
        devicesById.set(data.deviceId, row);
        devicesByPublicKey.set(data.devicePublicKey, row);
        return row;
      },
      updateMany: async ({
        where,
        data,
      }: {
        where: { deviceId: string };
        data: { revokedAt?: Date; revokedReason?: string };
      }) => {
        const device = devicesById.get(where.deviceId);
        if (!device) {
          return { count: 0 };
        }
        Object.assign(device, data);
        return { count: 1 };
      },
    },
    purseToken: {
      findFirst: async ({ where }: { where: { deviceId: string } }) => {
        const matches = [...purseTokensById.values()]
          .filter((t) => t.deviceId === where.deviceId)
          .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
        return matches[0] ?? null;
      },
      findUnique: async ({ where }: { where: { tokenId: string } }) => purseTokensById.get(where.tokenId) ?? null,
      create: async ({ data }: { data: Record<string, unknown> & { tokenId: string; deviceId: string } }) => {
        const record = { ...data, createdAt: new Date() };
        purseTokensById.set(data.tokenId, record);
        return record;
      },
    },
    transaction: {
      findUnique: async ({
        where,
      }: {
        where: { txId?: string; tokenId_deviceCounter?: { tokenId: string; deviceCounter: number } };
      }) => {
        if (where.txId !== undefined) {
          return transactionsById.get(where.txId) ?? null;
        }
        if (where.tokenId_deviceCounter !== undefined) {
          const { tokenId, deviceCounter } = where.tokenId_deviceCounter;
          return (
            [...transactionsById.values()].find((t) => t.tokenId === tokenId && t.deviceCounter === deviceCounter) ??
            null
          );
        }
        return null;
      },
      findMany: async ({ where }: { where: { tokenId: string } }) => {
        return [...transactionsById.values()]
          .filter((t) => t.tokenId === where.tokenId)
          .sort((a, b) => a.deviceCounter - b.deviceCounter);
      },
      create: async ({
        data,
      }: {
        data: Record<string, unknown> & { txId: string; tokenId: string; deviceCounter: number };
      }) => {
        // Real Prisma always returns null (never an omitted key) for a nullable column that
        // wasn't included in `data` — engine.ts's normal (non-escrow) persist path never sets
        // escrowContractId, relying on that DB default. Default it here so this fake matches
        // that guarantee instead of leaving the key undefined.
        const row = { escrowContractId: null, ...data };
        transactionsById.set(data.txId, row);
        return row;
      },
    },
    revokedCertificate: {
      findUnique: async ({ where }: { where: { certSerial: string } }) => revokedCerts.get(where.certSerial) ?? null,
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
    doubleSpendIncident: {
      create: async ({ data }: { data: Record<string, unknown> }) => {
        const record = { id: randomUUID(), detectedAt: new Date(), ...data };
        incidents.push(record);
        return record;
      },
    },
    // Needed since Phase 9: POST /purse/load (used by this file's loadPurseToken() helper) now
    // unconditionally calls getActiveDisasterEvent(), which queries this model — without it every
    // /purse/load call in this file would 500. This file doesn't test disaster-mode behavior
    // itself (see tests/api/purse/load.test.ts for that); it just needs the mock not to crash.
    disasterEvent: {
      findFirst: async ({ where }: { where: { regionGeo?: string; active?: boolean } }) => {
        const matches = disasterEvents
          .filter(
            (e) =>
              (where.regionGeo === undefined || e.regionGeo === where.regionGeo) &&
              (where.active === undefined || e.active === where.active),
          )
          .sort((a, b) => b.startedAt.getTime() - a.startedAt.getTime());
        return matches[0] ?? null;
      },
      create: async ({ data }: { data: Record<string, unknown> & { regionGeo: string; active: boolean } }) => {
        const record = {
          id: randomUUID(),
          essentialOnly: false,
          higherCap: null,
          startedAt: new Date(),
          endedAt: null,
          ...data,
        };
        disasterEvents.push(record);
        return record;
      },
      update: async ({ where, data }: { where: { id: string }; data: Record<string, unknown> }) => {
        const event = disasterEvents.find((e) => e.id === where.id);
        if (!event) {
          throw new Error(`disaster event not found in fake store: ${where.id}`);
        }
        Object.assign(event, data);
        return event;
      },
    },
    trustAttestation: {
      findUnique: async ({
        where,
      }: {
        where: { subjectA_subjectB: { subjectA: string; subjectB: string } };
      }) => {
        const { subjectA, subjectB } = where.subjectA_subjectB;
        return (
          [...trustEdgesById.values()].find((e) => e.subjectA === subjectA && e.subjectB === subjectB) ?? null
        );
      },
      findMany: async ({ where }: { where?: { OR?: Array<{ subjectA?: string; subjectB?: string }> } } = {}) => {
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
      update: async ({ where, data }: { where: { id: string }; data: Record<string, unknown> }) => {
        const edge = trustEdgesById.get(where.id);
        if (!edge) {
          throw new Error(`trust edge not found in fake store: ${where.id}`);
        }
        Object.assign(edge, data);
        return edge;
      },
    },
    // Handles both Prisma $transaction forms used across the codebase: an array of already-
    // invoked operation promises (src/settlement/engine.ts's persist phase) and an interactive
    // callback (src/api/purse/routes.ts). `tx` is typed `any` to avoid a circular type reference
    // (client referencing its own type within its own initializer) — test-only mock code.
    $transaction: async (arg: unknown[] | ((tx: any) => Promise<unknown>)) => {
      if (Array.isArray(arg)) {
        return Promise.all(arg);
      }
      return arg(client);
    },
  };

  return {
    __esModule: true,
    prisma: client,
    __setAccountBalance: (accountId: string, balance: bigint) => {
      accounts.set(accountId, { id: accountId, realBalance: balance });
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import { base64urlDecode, ed25519Sign, generateEd25519KeyPair, pemDecode } from '../../../src/crypto';
import { enrollTestDevice, makeSignedTx, TestDevice } from '../../helpers/makeSignedTx';
import { revokeCertificate } from '../../../src/settlement/doubleSpendResolver';

const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';

const { __setAccountBalance } = jest.requireMock('../../../src/db/client') as {
  __setAccountBalance: (accountId: string, balance: bigint) => void;
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

interface LoadedPurseToken {
  tokenId: string;
  value: string;
  cap: string;
  counterStart: number;
}

async function loadPurseToken(device: TestDevice, value: string, initialBalance = 10_000_000n): Promise<LoadedPurseToken> {
  __setAccountBalance(device.accountId, initialBalance);
  const sessionToken = await getSessionToken(device);

  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/purse/load',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: { value },
  });
  if (response.statusCode !== 200) {
    throw new Error(`purse/load failed in test setup: ${response.statusCode} ${response.body}`);
  }
  const { purse_token } = response.json();
  const json = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, purse_token).toString('utf8'));
  return { tokenId: json.token_id, value: json.value, cap: json.cap, counterStart: json.counter_start };
}

async function syncBatch(sessionToken: string, transactions: unknown[]) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/sync/transactions',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: { transactions },
  });
}

describe('POST /api/v1/sync/transactions', () => {
  it('settles a clean batch of valid (chained) transactions correctly', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const tx1 = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    const tx2 = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '20000',
      deviceCounter: token.counterStart + 1,
      prevTx: tx1,
    });

    const response = await syncBatch(callerSession, [tx1, tx2]);
    expect(response.statusCode).toBe(200);

    const { results, incidents } = response.json();
    expect(results).toEqual([
      { tx_id: tx1.tx_id, status: 'accepted' },
      { tx_id: tx2.tx_id, status: 'accepted' },
    ]);
    expect(incidents).toEqual([]);
  });

  it('settles a relay duplicate (identical tx_id + signature sent twice) once, with no incident', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const tx = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });

    const first = await syncBatch(callerSession, [tx]);
    expect(first.json().results).toEqual([{ tx_id: tx.tx_id, status: 'accepted' }]);

    const second = await syncBatch(callerSession, [tx]);
    expect(second.statusCode).toBe(200);
    const { results, incidents } = second.json();
    expect(results).toEqual([{ tx_id: tx.tx_id, status: 'duplicate', reason: 'already settled' }]);
    expect(incidents).toEqual([]);
  });

  it('detects a genuine double-spend within one batch, records the incident, and revokes the device', async () => {
    const payer = await enrollTestDevice(app);
    const payeeA = await enrollTestDevice(app);
    const payeeB = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payeeA);

    // Same payer, same token, same device_counter — but two genuinely different transactions
    // (different payee, different tx_id, therefore different signature).
    const txA = makeSignedTx({
      payer,
      payee: payeeA,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    const txB = makeSignedTx({
      payer,
      payee: payeeB,
      tokenId: token.tokenId,
      amount: '15000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    expect(txA.signature).not.toBe(txB.signature);

    const response = await syncBatch(callerSession, [txA, txB]);
    expect(response.statusCode).toBe(200);

    const { results, incidents } = response.json();
    expect(results).toEqual([
      expect.objectContaining({ tx_id: txA.tx_id, status: 'rejected' }),
      expect.objectContaining({ tx_id: txB.tx_id, status: 'rejected' }),
    ]);
    expect(results[0].reason).toMatch(/double-spend/);
    expect(results[1].reason).toMatch(/double-spend/);

    expect(incidents).toHaveLength(1);
    expect(incidents[0]).toEqual(
      expect.objectContaining({
        token_id: token.tokenId,
        device_id: payer.deviceId,
        tx_id_a: txA.tx_id,
        tx_id_b: txB.tx_id,
      }),
    );

    // The device should now be revoked: it can no longer obtain a new session.
    const challengeRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/challenge',
      payload: { device_id: payer.deviceId },
    });
    const { nonce } = challengeRes.json();
    const signedNonce = ed25519Sign(payer.privateSeed, base64urlDecode(nonce));
    const verifyRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/verify',
      payload: { device_id: payer.deviceId, signed_nonce: signedNonce },
    });
    expect(verifyRes.statusCode).toBe(401);
  });

  it('detects a double-spend against previously-settled history (across two separate syncs)', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const txFirst = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    const settleFirst = await syncBatch(callerSession, [txFirst]);
    expect(settleFirst.json().results).toEqual([{ tx_id: txFirst.tx_id, status: 'accepted' }]);

    const txConflicting = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '25000',
      deviceCounter: token.counterStart, // same slot as txFirst, different tx_id/signature
      prevTx: null,
    });
    const settleSecond = await syncBatch(callerSession, [txConflicting]);
    expect(settleSecond.statusCode).toBe(200);
    const { results, incidents } = settleSecond.json();
    expect(results[0].status).toBe('rejected');
    expect(results[0].reason).toMatch(/double-spend/);
    expect(incidents).toHaveLength(1);
    expect(incidents[0]).toEqual(
      expect.objectContaining({ tx_id_a: txFirst.tx_id, tx_id_b: txConflicting.tx_id }),
    );
  });

  it('rejects a real transaction colliding with an escrow-settled slot with a clean "stale counter" error, not a double-spend incident', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: {
        transaction: { create: (args: { data: Record<string, unknown> }) => Promise<unknown> };
        device: { findUnique: (args: { where: { deviceId: string } }) => Promise<{ revokedAt: Date | null }> };
      };
    };

    // Simulates what POST /escrow/release would have created: a settled transaction at this
    // exact (token_id, device_counter) slot, with escrowContractId set — the device's local
    // ledger doesn't know about this yet.
    const escrowSettledTxId = randomUUID();
    await prisma.transaction.create({
      data: {
        txId: escrowSettledTxId,
        tokenId: token.tokenId,
        amount: '15000',
        payerDeviceId: payer.deviceId,
        payerAccountId: randomUUID(),
        payerCert: payer.certPem,
        payeeDeviceId: payee.deviceId,
        payeeAccountId: randomUUID(),
        payeeCert: payee.certPem,
        deviceCounter: token.counterStart,
        prevTxHash: null,
        timestamp: Math.floor(Date.now() / 1000),
        signature: 'escrow-release-signature-placeholder',
        escrowContractId: randomUUID(),
      },
    });

    // The device, unaware of the escrow settlement, signs its own real transaction for the same
    // slot — genuinely signed by the real device, not fraud, just stale local state.
    const staleTx = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });

    const response = await syncBatch(callerSession, [staleTx]);
    expect(response.statusCode).toBe(200);
    const { results, incidents } = response.json();

    expect(results[0].status).toBe('rejected');
    expect(results[0].reason).toMatch(/stale counter/i);
    expect(results[0].reason).not.toMatch(/double-spend/i);
    expect(incidents).toEqual([]); // no fraud incident recorded

    const deviceRow = await prisma.device.findUnique({ where: { deviceId: payer.deviceId } });
    expect(deviceRow?.revokedAt).toBeNull(); // device NOT revoked
  });

  it('rejects a transaction whose amount exceeds the token\'s cap', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const tx = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '999999999', // > token.cap ('200000', from limitStub.ts's placeholder)
      deviceCounter: token.counterStart,
      prevTx: null,
    });

    const response = await syncBatch(callerSession, [tx]);
    expect(response.statusCode).toBe(200);
    const { results } = response.json();
    expect(results).toEqual([expect.objectContaining({ tx_id: tx.tx_id, status: 'rejected' })]);
    expect(results[0].reason).toMatch(/cap/);
  });

  it('rejects a transaction signed by a revoked device', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    await revokeCertificate({
      certSerial: payer.serialNumber,
      deviceId: payer.deviceId,
      reason: 'test: manually revoked',
    });

    const tx = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });

    const response = await syncBatch(callerSession, [tx]);
    expect(response.statusCode).toBe(200);
    const { results } = response.json();
    expect(results).toEqual([expect.objectContaining({ tx_id: tx.tx_id, status: 'rejected' })]);
    expect(results[0].reason).toMatch(/revoked/);
  });

  it('rejects a transaction referencing a non-existent token_id', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const callerSession = await getSessionToken(payee);

    const tx = makeSignedTx({
      payer,
      payee,
      tokenId: randomUUID(), // never issued via /purse/load
      amount: '10000',
      deviceCounter: 0,
      prevTx: null,
    });

    const response = await syncBatch(callerSession, [tx]);
    expect(response.statusCode).toBe(200);
    const { results } = response.json();
    expect(results).toEqual([expect.objectContaining({ tx_id: tx.tx_id, status: 'rejected' })]);
    expect(results[0].reason).toMatch(/token_id/);
  });

  it('rejects a request with no Authorization header with 401', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/sync/transactions',
      payload: { transactions: [] },
    });
    expect(response.statusCode).toBe(401);
  });

  describe('trust edges (Phase 8)', () => {
    async function findTrustEdges(deviceId: string) {
      const { prisma } = jest.requireMock('../../../src/db/client') as {
        prisma: {
          trustAttestation: {
            findMany: (args: {
              where: { OR: Array<{ subjectA?: string; subjectB?: string }> };
            }) => Promise<Array<Record<string, unknown>>>;
          };
        };
      };
      return prisma.trustAttestation.findMany({
        where: { OR: [{ subjectA: deviceId }, { subjectB: deviceId }] },
      });
    }

    it('creates a trust edge between payer and payee when a transaction settles, and updates it (not a duplicate) on a second settlement', async () => {
      const payer = await enrollTestDevice(app);
      const payee = await enrollTestDevice(app);
      const token = await loadPurseToken(payer, '100000');
      const callerSession = await getSessionToken(payee);

      const tx1 = makeSignedTx({
        payer,
        payee,
        tokenId: token.tokenId,
        amount: '10000',
        deviceCounter: token.counterStart,
        prevTx: null,
      });
      const settle1 = await syncBatch(callerSession, [tx1]);
      expect(settle1.json().results).toEqual([{ tx_id: tx1.tx_id, status: 'accepted' }]);

      const edgesAfterFirst = await findTrustEdges(payer.deviceId);
      expect(edgesAfterFirst).toHaveLength(1);
      expect(edgesAfterFirst[0]).toEqual(
        expect.objectContaining({ settledAmount: '10000', settlementCount: 1 }),
      );
      expect([edgesAfterFirst[0].subjectA, edgesAfterFirst[0].subjectB].sort()).toEqual(
        [payer.deviceId, payee.deviceId].sort(),
      );

      const tx2 = makeSignedTx({
        payer,
        payee,
        tokenId: token.tokenId,
        amount: '5000',
        deviceCounter: token.counterStart + 1,
        prevTx: tx1,
      });
      const settle2 = await syncBatch(callerSession, [tx2]);
      expect(settle2.json().results).toEqual([{ tx_id: tx2.tx_id, status: 'accepted' }]);

      const edgesAfterSecond = await findTrustEdges(payer.deviceId);
      expect(edgesAfterSecond).toHaveLength(1); // same edge updated, not a second one
      expect(edgesAfterSecond[0].settledAmount).toBe('15000');
      expect(edgesAfterSecond[0].settlementCount).toBe(2);
    });

    it('does NOT create or update a trust edge for a transaction that fails to settle', async () => {
      const payer = await enrollTestDevice(app);
      const payee = await enrollTestDevice(app);
      const token = await loadPurseToken(payer, '100000');
      const callerSession = await getSessionToken(payee);

      const tx = makeSignedTx({
        payer,
        payee,
        tokenId: token.tokenId,
        amount: '999999999', // exceeds the token's cap -> rejected, never settles
        deviceCounter: token.counterStart,
        prevTx: null,
      });

      const response = await syncBatch(callerSession, [tx]);
      expect(response.json().results[0].status).toBe('rejected');

      const edges = await findTrustEdges(payer.deviceId);
      expect(edges).toEqual([]);
    });
  });
});

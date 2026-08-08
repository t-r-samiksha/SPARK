import { randomUUID } from 'crypto';

// Full in-memory fake covering every Prisma model touched across the request chain these tests
// exercise: /enroll, /auth/challenge, /auth/verify, /purse/load, /sync/transactions, and
// /sms/inbound. Modeled directly on tests/api/sync/transactions.test.ts's mock (same models, same
// fake-fidelity fixes — escrowContractId/revokedAt defaulting to null, not left undefined, to
// match real Prisma's guarantee that unset nullable columns always read back as null). Declared
// entirely inside the factory per Jest's mock-hoisting rules. Exposes `__getIncidentCount`
// (test-only, via `jest.requireMock`) so the double-spend-via-SMS test can confirm an incident was
// actually recorded, not just infer it from the rejection reason text.
jest.mock('../../../src/db/client', () => {
  const accounts = new Map<string, { id: string; realBalance: bigint }>();
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const purseTokensById = new Map<string, Record<string, unknown> & { deviceId: string; createdAt: Date }>();
  const transactionsById = new Map<string, Record<string, unknown> & { tokenId: string; deviceCounter: number }>();
  const revokedCerts = new Map<string, { certSerial: string; revokedAt: Date; reason: string }>();
  const incidents: Array<Record<string, unknown>> = [];
  const trustEdgesById = new Map<string, Record<string, unknown> & { id: string; subjectA: string; subjectB: string }>();
  const disasterEvents: Array<Record<string, unknown> & { active: boolean }> = [];

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
    // Needed since Phase 9: POST /purse/load unconditionally calls getActiveDisasterEvent(),
    // which queries this model — without it every /purse/load call in this file would 500.
    disasterEvent: {
      findFirst: async ({ where }: { where: { active?: boolean } }) => {
        const matches = disasterEvents.filter((e) => where.active === undefined || e.active === where.active);
        return matches[0] ?? null;
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
    // callback (src/api/purse/routes.ts). `tx` is typed `any` to avoid a circular type reference —
    // test-only mock code.
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
    __getIncidentCount: () => incidents.length,
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import { base64urlDecode, base64urlEncode, ed25519Sign, generateEd25519KeyPair, pemDecode } from '../../../src/crypto';
import { enrollTestDevice, makeSignedTx, TestDevice } from '../../helpers/makeSignedTx';
import { encodeTransactionSms } from '../../../src/api/sms/smsEncoding';

const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';

const { __setAccountBalance, __getIncidentCount } = jest.requireMock('../../../src/db/client') as {
  __setAccountBalance: (accountId: string, balance: bigint) => void;
  __getIncidentCount: () => number;
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

function syncBatch(sessionToken: string, transactions: unknown[]) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/sync/transactions',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: { transactions },
  });
}

function smsInbound(from: string, body: string) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/sms/inbound',
    payload: { from, body },
  });
}

describe('POST /api/v1/sms/inbound', () => {
  it('settles a validly-encoded transaction identically to POST /sync/transactions, chaining on top of a normally-synced one', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    // tx1 settles normally, over /sync/transactions.
    const tx1 = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    const syncResponse = await syncBatch(callerSession, [tx1]);
    expect(syncResponse.statusCode).toBe(200);
    expect(syncResponse.json().results).toEqual([{ tx_id: tx1.tx_id, status: 'accepted' }]);

    // tx2 chains directly on top of tx1 (device_counter + 1, prev_tx_hash = hash(tx1)) but arrives
    // over SMS instead — same payer, same token, a real continuation of the same chain.
    const tx2 = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '15000',
      deviceCounter: token.counterStart + 1,
      prevTx: tx1,
    });
    const smsBody = encodeTransactionSms(tx2);
    const smsResponse = await smsInbound('+15551234567', smsBody);

    expect(smsResponse.statusCode).toBe(200);
    // Identical shape/outcome to what /sync/transactions would have returned for this same
    // transaction: {tx_id, status: 'accepted'}, no `reason` key — this IS engine.ts's
    // TxSettlementResult, unmodified, not a re-derived summary.
    expect(smsResponse.json()).toEqual({
      status: 'received',
      detail: { tx_id: tx2.tx_id, status: 'accepted' },
    });

    // Confirms it was actually persisted (not just reported as accepted) — a third, independent
    // transaction chained on top of tx2 via /sync/transactions should settle cleanly, which is
    // only possible if the engine's Phase 4 continuity check sees tx2 as real settled history.
    const tx3 = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '5000',
      deviceCounter: token.counterStart + 2,
      prevTx: tx2,
    });
    const finalResponse = await syncBatch(callerSession, [tx3]);
    expect(finalResponse.json().results).toEqual([{ tx_id: tx3.tx_id, status: 'accepted' }]);
  });

  it('rejects an SMS body that is not valid base64url/JSON cleanly, without a 500', async () => {
    const response = await smsInbound('+15551234567', 'not-valid-base64url!!!');

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('error');
    expect(typeof body.detail).toBe('string');
  });

  it('rejects a decoded SMS body that is valid JSON but not shaped like a transaction, without a 500', async () => {
    const notATransaction = base64urlEncode(Buffer.from(JSON.stringify({ hello: 'world' }), 'utf8'));
    const response = await smsInbound('+15551234567', notATransaction);

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('error');
    expect(typeof body.detail).toBe('string');
  });

  it('detects a double-spend when one side arrives via SMS and the other via normal sync — same trust path either way', async () => {
    const payer = await enrollTestDevice(app);
    const payee = await enrollTestDevice(app);
    const token = await loadPurseToken(payer, '100000');
    const callerSession = await getSessionToken(payee);

    const incidentsBefore = __getIncidentCount();

    // Genuinely settled first, over the normal path.
    const txFirst = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '10000',
      deviceCounter: token.counterStart,
      prevTx: null,
    });
    const syncResponse = await syncBatch(callerSession, [txFirst]);
    expect(syncResponse.json().results).toEqual([{ tx_id: txFirst.tx_id, status: 'accepted' }]);

    // A second, DIFFERENT, still validly-signed transaction claiming the SAME (token_id,
    // device_counter) slot — a genuine double-spend attempt, arriving over SMS instead.
    const txConflicting = makeSignedTx({
      payer,
      payee,
      tokenId: token.tokenId,
      amount: '20000',
      deviceCounter: token.counterStart, // same slot as txFirst
      prevTx: null,
    });
    const smsResponse = await smsInbound('+15551234567', encodeTransactionSms(txConflicting));

    expect(smsResponse.statusCode).toBe(200);
    const body = smsResponse.json();
    expect(body.status).toBe('received');
    expect(body.detail.tx_id).toBe(txConflicting.tx_id);
    expect(body.detail.status).toBe('rejected');
    expect(body.detail.reason).toMatch(/double-spend/i);
    // Not the escrow-settlement stale-counter path — this is a REAL double-spend between two
    // genuinely device-signed transactions.
    expect(body.detail.reason).not.toMatch(/stale counter/i);

    // The same recordDoubleSpendIncident() path that fires for an all-sync double-spend fired
    // here too — SMS didn't bypass or duplicate that logic.
    expect(__getIncidentCount()).toBe(incidentsBefore + 1);
  });
});

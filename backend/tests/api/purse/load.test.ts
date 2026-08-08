import { randomUUID } from 'crypto';

// In-memory fake for the Prisma models POST /purse/load touches (plus the Device/Account models
// needed to enroll + authenticate a device first, since these are integration tests over the
// real HTTP surface, not unit tests of the route in isolation). Declared entirely inside the
// factory per Jest's mock-hoisting rules. Exposes two test-only helpers (`__setAccountBalance`,
// `__getAccountBalance`) alongside `prisma`, since the real /enroll flow always creates accounts
// at real_balance = 0 (the schema default) and there's no route that sets a balance — tests
// reach into the fake store directly via `jest.requireMock` to fund accounts.
jest.mock('../../../src/db/client', () => {
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const accounts = new Map<string, { id: string; realBalance: bigint }>();
  const purseTokens: Array<Record<string, unknown> & { deviceId: string; createdAt: Date }> = [];

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
        devicesById.set(data.deviceId, data);
        devicesByPublicKey.set(data.devicePublicKey, data);
        return data;
      },
    },
    purseToken: {
      findFirst: async ({ where }: { where: { deviceId: string } }) => {
        const matches = purseTokens
          .filter((t) => t.deviceId === where.deviceId)
          .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
        return matches[0] ?? null;
      },
      create: async ({ data }: { data: Record<string, unknown> & { deviceId: string } }) => {
        const record = { ...data, deviceId: data.deviceId, createdAt: new Date() };
        purseTokens.push(record);
        return record;
      },
    },
    // `tx` is typed `any` rather than `typeof client` to avoid a circular type reference (client
    // referencing its own type within its own initializer) — acceptable in test-only mock code.
    $transaction: async <T>(fn: (tx: any) => Promise<T>): Promise<T> => fn(client),
  };

  return {
    __esModule: true,
    prisma: client,
    __setAccountBalance: (accountId: string, balance: bigint) => {
      accounts.set(accountId, { id: accountId, realBalance: balance });
    },
    __getAccountBalance: (accountId: string): bigint | undefined => accounts.get(accountId)?.realBalance,
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import {
  canonicalizeForSigning,
  ed25519Sign,
  ed25519Verify,
  generateEd25519KeyPair,
  base64urlDecode,
  pemDecode,
} from '../../../src/crypto';
import { PurseToken } from '../../../src/types';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';
const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';
const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

// Generous default so tests unrelated to balance limits (session checks, cap checks, schema
// validation) don't spuriously fail on insufficient balance — real /enroll always creates
// accounts at real_balance = 0 (the schema default).
const DEFAULT_TEST_BALANCE = 10_000_000n; // ₹1,00,000

const { __setAccountBalance, __getAccountBalance } = jest.requireMock('../../../src/db/client') as {
  __setAccountBalance: (accountId: string, balance: bigint) => void;
  __getAccountBalance: (accountId: string) => bigint | undefined;
};

let app: FastifyInstance;
let operationalPublicKey: string;

beforeAll(async () => {
  process.env.BANK_ROOT_CA_KEY_SEED = generateEd25519KeyPair().privateSeed;
  const operationalKeyPair = generateEd25519KeyPair();
  process.env.BANK_SIGNING_KEY_SEED = operationalKeyPair.privateSeed;
  operationalPublicKey = operationalKeyPair.publicKey;

  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

interface EnrolledDevice {
  deviceId: string;
  accountId: string;
  publicKey: string;
  privateSeed: string;
}

async function enrollDevice(initialBalance: bigint = DEFAULT_TEST_BALANCE): Promise<EnrolledDevice> {
  const { publicKey, privateSeed } = generateEd25519KeyPair();
  const accountId = randomUUID();
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/enroll',
    payload: {
      account_id: accountId,
      public_key: publicKey,
      attestation_blob: 'test-attestation-blob',
    },
  });
  if (response.statusCode !== 200) {
    throw new Error(`enrollment failed in test setup: ${response.statusCode} ${response.body}`);
  }
  __setAccountBalance(accountId, initialBalance);

  const { cert } = response.json();
  const json = JSON.parse(pemDecode(CERT_PEM_LABEL, cert).toString('utf8'));
  return { deviceId: json.device_id, accountId, publicKey, privateSeed };
}

async function getSessionToken(device: EnrolledDevice): Promise<string> {
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

async function loadPurse(sessionToken: string | undefined, body: Record<string, unknown>) {
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/purse/load',
    headers: sessionToken ? { authorization: `Bearer ${sessionToken}` } : {},
    payload: body,
  });
  return response;
}

describe('POST /api/v1/purse/load', () => {
  it('issues a purse token that verifies against the operational public key', async () => {
    const device = await enrollDevice();
    const sessionToken = await getSessionToken(device);

    const response = await loadPurse(sessionToken, { value: '50000' });
    expect(response.statusCode).toBe(200);

    const { purse_token: pem } = response.json();
    expect(typeof pem).toBe('string');

    const json = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, pem).toString('utf8')) as PurseToken;

    expect(json.device_id).toBe(device.deviceId);
    expect(json.value).toBe('50000');
    expect(json.cap).toBe('200000');
    expect(json.counter_start).toBe(0);
    expect(UUID_V4_PATTERN.test(json.token_id)).toBe(true);
    expect(typeof json.expiry).toBe('number');
    expect(json.expiry).toBeGreaterThan(Math.floor(Date.now() / 1000));

    const signingBytes = canonicalizeForSigning({ ...json });
    expect(ed25519Verify(operationalPublicKey, signingBytes, json.signature)).toBe(true);
  });

  it('carries counter_start forward from the device\'s previous purse token', async () => {
    const device = await enrollDevice();
    const sessionToken = await getSessionToken(device);

    const first = await loadPurse(sessionToken, { value: '10000' });
    const firstToken = JSON.parse(
      pemDecode(PURSE_TOKEN_PEM_LABEL, first.json().purse_token).toString('utf8'),
    ) as PurseToken;
    expect(firstToken.counter_start).toBe(0);

    const second = await loadPurse(sessionToken, { value: '20000' });
    const secondToken = JSON.parse(
      pemDecode(PURSE_TOKEN_PEM_LABEL, second.json().purse_token).toString('utf8'),
    ) as PurseToken;
    expect(secondToken.counter_start).toBe(firstToken.counter_start);
  });

  it('rejects a request with no Authorization header with 401', async () => {
    const response = await loadPurse(undefined, { value: '50000' });
    expect(response.statusCode).toBe(401);
  });

  it('rejects a request with an invalid session token with 401', async () => {
    const response = await loadPurse('not-a-real-session-token', { value: '50000' });
    expect(response.statusCode).toBe(401);
  });

  it('rejects a request missing the required value field with 400', async () => {
    const device = await enrollDevice();
    const sessionToken = await getSessionToken(device);

    const response = await loadPurse(sessionToken, {});
    expect(response.statusCode).toBe(400);
  });

  it('rejects a non-numeric value with 400', async () => {
    const device = await enrollDevice();
    const sessionToken = await getSessionToken(device);

    const response = await loadPurse(sessionToken, { value: 'not-a-number' });
    expect(response.statusCode).toBe(400);
  });

  it('rejects a value that exceeds the recommended cap with 400', async () => {
    const device = await enrollDevice();
    const sessionToken = await getSessionToken(device);

    const response = await loadPurse(sessionToken, { value: '999999999' });
    expect(response.statusCode).toBe(400);
  });

  describe('real_balance semantics', () => {
    it('decrements the account real_balance by exactly value on a successful load', async () => {
      const device = await enrollDevice(100_000n);
      const sessionToken = await getSessionToken(device);

      const response = await loadPurse(sessionToken, { value: '30000' });
      expect(response.statusCode).toBe(200);

      expect(__getAccountBalance(device.accountId)).toBe(70_000n);
    });

    it('fails cleanly with 400 when value exceeds real_balance, leaving the balance unchanged', async () => {
      const device = await enrollDevice(1_000n);
      const sessionToken = await getSessionToken(device);

      const response = await loadPurse(sessionToken, { value: '5000' });
      expect(response.statusCode).toBe(400);
      expect(response.json().error).toMatch(/balance/i);

      // A rejected load must not touch the balance at all.
      expect(__getAccountBalance(device.accountId)).toBe(1_000n);
    });

    it('decrements correctly across two sequential loads (not double-counted)', async () => {
      const device = await enrollDevice(100_000n);
      const sessionToken = await getSessionToken(device);

      const first = await loadPurse(sessionToken, { value: '30000' });
      expect(first.statusCode).toBe(200);
      expect(__getAccountBalance(device.accountId)).toBe(70_000n);

      const second = await loadPurse(sessionToken, { value: '20000' });
      expect(second.statusCode).toBe(200);
      expect(__getAccountBalance(device.accountId)).toBe(50_000n);
    });
  });
});

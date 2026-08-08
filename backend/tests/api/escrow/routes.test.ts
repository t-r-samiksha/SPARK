import { randomUUID } from 'crypto';

// Full in-memory fake covering every Prisma model touched across the request chain these tests
// exercise: /enroll, /auth/challenge, /auth/verify, /purse/load, and /escrow/*. Declared entirely
// inside the factory per Jest's mock-hoisting rules. Exposes `__setAccountBalance` (test-only, via
// `jest.requireMock`) since real /enroll always creates accounts at real_balance = 0.
jest.mock('../../../src/db/client', () => {
  const accounts = new Map<string, { id: string; realBalance: bigint }>();
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const purseTokensById = new Map<string, Record<string, unknown> & { deviceId: string; createdAt: Date }>();
  const transactionsById = new Map<string, Record<string, unknown> & { tokenId: string; deviceCounter: number }>();
  const escrowsById = new Map<string, Record<string, unknown> & { id: string; tokenId: string; status: string }>();
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
        devicesById.set(data.deviceId, data);
        devicesByPublicKey.set(data.devicePublicKey, data);
        return data;
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
      findFirst: async ({ where }: { where: { tokenId: string } }) => {
        const matches = [...transactionsById.values()]
          .filter((t) => t.tokenId === where.tokenId)
          .sort((a, b) => b.deviceCounter - a.deviceCounter);
        return matches[0] ?? null;
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
        transactionsById.set(data.txId, data);
        return data;
      },
    },
    escrowContract: {
      create: async ({ data }: { data: Record<string, unknown> & { tokenId: string } }) => {
        const record = {
          id: randomUUID(),
          status: 'locked',
          disputeReason: null,
          createdAt: new Date(),
          resolvedAt: null,
          ...data,
        };
        escrowsById.set(record.id, record);
        return record;
      },
      findUnique: async ({ where }: { where: { id: string } }) => escrowsById.get(where.id) ?? null,
      findMany: async ({ where }: { where: { tokenId: string; status?: string } }) => {
        return [...escrowsById.values()].filter(
          (e) => e.tokenId === where.tokenId && (where.status === undefined || e.status === where.status),
        );
      },
      update: async ({ where, data }: { where: { id: string }; data: Record<string, unknown> }) => {
        const escrow = escrowsById.get(where.id);
        if (!escrow) {
          throw new Error(`escrow not found in fake store: ${where.id}`);
        }
        Object.assign(escrow, data);
        return escrow;
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
    disasterEvent: {
      findFirst: async ({ where }: { where: { active?: boolean } }) => {
        const matches = disasterEvents.filter((e) => where.active === undefined || e.active === where.active);
        return matches[0] ?? null;
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
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import {
  base64urlDecode,
  canonicalizeForSigning,
  ed25519Sign,
  generateEd25519KeyPair,
  pemDecode,
} from '../../../src/crypto';

const CERT_PEM_LABEL = 'SPARK DEVICE CERTIFICATE';
const PURSE_TOKEN_PEM_LABEL = 'SPARK PURSE TOKEN';
const DEFAULT_TEST_BALANCE = 10_000_000n; // ₹1,00,000

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
    payload: { account_id: accountId, public_key: publicKey, attestation_blob: 'test-attestation-blob' },
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

async function loadPurse(sessionToken: string, value: string) {
  const response = await app.inject({
    method: 'POST',
    url: '/api/v1/purse/load',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: { value },
  });
  if (response.statusCode !== 200) {
    throw new Error(`purse/load failed in test setup: ${response.statusCode} ${response.body}`);
  }
  const json = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, response.json().purse_token).toString('utf8'));
  return { tokenId: json.token_id as string, value: json.value as string, cap: json.cap as string };
}

function createEscrow(sessionToken: string, body: Record<string, unknown>) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/escrow/create',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: body,
  });
}

function releaseEscrow(sessionToken: string, body: Record<string, unknown>) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/escrow/release',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: body,
  });
}

function disputeEscrow(sessionToken: string, body: Record<string, unknown>) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/escrow/dispute',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: body,
  });
}

function signRelease(privateSeed: string, escrowId: string, condition: string): string {
  const signingBytes = canonicalizeForSigning({ escrow_id: escrowId, condition });
  return ed25519Sign(privateSeed, signingBytes);
}

/** Sets up a buyer with a loaded purse token and a seller, both authenticated, ready to create an
 * escrow between them. */
async function setupBuyerAndSeller(purseValue = '50000') {
  const buyer = await enrollDevice();
  const seller = await enrollDevice();
  const buyerSession = await getSessionToken(buyer);
  const sellerSession = await getSessionToken(seller);
  await loadPurse(buyerSession, purseValue);
  return { buyer, seller, buyerSession, sellerSession };
}

describe('POST /api/v1/escrow/create', () => {
  it('succeeds with sufficient balance', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller('50000');

    const response = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '20000',
      condition: 'seller ships the widget',
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('locked');
    expect(body.amount).toBe('20000');
    expect(body.buyer_device_id).toBe(buyer.deviceId);
    expect(body.seller_device_id).toBe(seller.deviceId);
    expect(body.condition).toBe('seller ships the widget');
    expect(typeof body.id).toBe('string');
    expect(typeof body.created_at).toBe('number');
    expect(body.resolved_at).toBeNull();
  });

  it('fails with insufficient balance', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller('10000');

    const response = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '20000', // exceeds the 10000 loaded into the purse
      condition: 'seller ships the widget',
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatch(/available balance/);
  });

  it('accounts for already-locked escrows when checking available balance', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller('50000');

    const first = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '30000',
      condition: 'first deal',
    });
    expect(first.statusCode).toBe(200);

    // Only 20000 should remain available (50000 - 30000 locked).
    const second = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '25000',
      condition: 'second deal',
    });
    expect(second.statusCode).toBe(400);
    expect(second.json().error).toMatch(/available balance/);
  });

  it('requires a valid session', async () => {
    const { buyer, seller } = await setupBuyerAndSeller();
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/escrow/create',
      payload: {
        buyer_device_id: buyer.deviceId,
        seller_device_id: seller.deviceId,
        amount: '10000',
        condition: 'x',
      },
    });
    expect(response.statusCode).toBe(401);
  });

  it('rejects a session that does not belong to the buyer device', async () => {
    const { buyer, seller, sellerSession } = await setupBuyerAndSeller();

    // sellerSession is a valid session, just not the buyer's.
    const response = await createEscrow(sellerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'x',
    });
    expect(response.statusCode).toBe(403);
  });
});

describe('POST /api/v1/escrow/release', () => {
  it("valid buyer signature moves funds and updates status", async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller('50000');
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '20000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    const buyerSignature = signRelease(buyer.privateSeed, escrow.id, escrow.condition);
    const response = await releaseEscrow(buyerSession, { escrow_id: escrow.id, buyer_signature: buyerSignature });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('released');
    expect(typeof body.resolved_at).toBe('number');

    // A fresh escrow for the now-freed-up remainder plus the already-spent 20000 should reflect
    // the settlement counting as SPENT, not still locked: only 30000 (50000 - 20000) available.
    const withinRemainder = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '30000',
      condition: 'second deal',
    });
    expect(withinRemainder.statusCode).toBe(200);

    const overRemainder = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '1',
      condition: 'third deal',
    });
    expect(overRemainder.statusCode).toBe(400);
  });

  it('rejects a wrong signature', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    const wrongSignature = signRelease(buyer.privateSeed, escrow.id, 'a different condition entirely');
    const response = await releaseEscrow(buyerSession, { escrow_id: escrow.id, buyer_signature: wrongSignature });
    expect(response.statusCode).toBe(401);
  });

  it('rejects releasing an already-released escrow', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();
    const buyerSignature = signRelease(buyer.privateSeed, escrow.id, escrow.condition);

    const first = await releaseEscrow(buyerSession, { escrow_id: escrow.id, buyer_signature: buyerSignature });
    expect(first.statusCode).toBe(200);

    const second = await releaseEscrow(buyerSession, { escrow_id: escrow.id, buyer_signature: buyerSignature });
    expect(second.statusCode).toBe(409);
  });

  it("rejects a genuine signature from a different device (someone else's escrow)", async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    // An unrelated, genuinely-enrolled third device signs the SAME payload — a valid signature,
    // just not from this escrow's actual buyer.
    const impostor = await enrollDevice();
    const impostorSignature = signRelease(impostor.privateSeed, escrow.id, escrow.condition);

    const response = await releaseEscrow(buyerSession, {
      escrow_id: escrow.id,
      buyer_signature: impostorSignature,
    });
    expect(response.statusCode).toBe(401);
  });
});

describe('POST /api/v1/escrow/dispute', () => {
  it('lets the buyer dispute a locked escrow', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    const response = await disputeEscrow(buyerSession, { escrow_id: escrow.id, reason: 'never arrived' });
    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('disputed');
    expect(body.dispute_reason).toBe('never arrived');
    expect(body.resolved_at).toBeNull(); // disputed is ongoing, not terminal
  });

  it('lets the seller dispute a locked escrow', async () => {
    const { buyer, seller, buyerSession, sellerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    const response = await disputeEscrow(sellerSession, { escrow_id: escrow.id, reason: 'buyer is unreachable' });
    expect(response.statusCode).toBe(200);
    expect(response.json().status).toBe('disputed');
  });

  it('rejects disputing an already-resolved escrow', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();
    const buyerSignature = signRelease(buyer.privateSeed, escrow.id, escrow.condition);
    const released = await releaseEscrow(buyerSession, { escrow_id: escrow.id, buyer_signature: buyerSignature });
    expect(released.statusCode).toBe(200);

    const response = await disputeEscrow(buyerSession, { escrow_id: escrow.id, reason: 'too late' });
    expect(response.statusCode).toBe(409);
  });

  it('rejects a dispute from a third party', async () => {
    const { buyer, seller, buyerSession } = await setupBuyerAndSeller();
    const created = await createEscrow(buyerSession, {
      buyer_device_id: buyer.deviceId,
      seller_device_id: seller.deviceId,
      amount: '10000',
      condition: 'seller ships the widget',
    });
    const escrow = created.json();

    const thirdParty = await enrollDevice();
    const thirdPartySession = await getSessionToken(thirdParty);

    const response = await disputeEscrow(thirdPartySession, { escrow_id: escrow.id, reason: 'not my business' });
    expect(response.statusCode).toBe(403);
  });
});

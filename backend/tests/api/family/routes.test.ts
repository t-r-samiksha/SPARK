import { randomUUID } from 'crypto';

// Full in-memory fake covering every Prisma model touched across the request chain these tests
// exercise: /enroll, /auth/challenge, /auth/verify, /family/allocate, /family/activity, and (for
// the "spent_amount reflects real settled spending" test) /sync/transactions. Modeled directly on
// tests/api/sync/transactions.test.ts's mock (same models, same fake-fidelity fixes — e.g.
// escrowContractId/revokedAt defaulting to null, not left undefined, to match real Prisma's
// guarantee that unset nullable columns always read back as null). Declared entirely inside the
// factory per Jest's mock-hoisting rules.
jest.mock('../../../src/db/client', () => {
  const accounts = new Map<string, { id: string; realBalance: bigint }>();
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const purseTokensById = new Map<string, Record<string, unknown> & { deviceId: string; createdAt: Date }>();
  const transactionsById = new Map<string, Record<string, unknown> & { tokenId: string; deviceCounter: number }>();
  const revokedCerts = new Map<string, { certSerial: string; revokedAt: Date; reason: string }>();
  const incidents: Array<Record<string, unknown>> = [];
  const trustEdgesById = new Map<string, Record<string, unknown> & { id: string; subjectA: string; subjectB: string }>();
  const familyAllocationsById = new Map<
    string,
    Record<string, unknown> & { id: string; parentAccountId: string; createdAt: Date }
  >();

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
    familyAllocation: {
      create: async ({ data }: { data: Record<string, unknown> & { parentAccountId: string } }) => {
        const record = { id: randomUUID(), active: true, createdAt: new Date(), ...data };
        familyAllocationsById.set(record.id, record);
        return record;
      },
      findMany: async ({ where }: { where: { parentAccountId: string } }) => {
        return [...familyAllocationsById.values()]
          .filter((a) => a.parentAccountId === where.parentAccountId)
          .sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
      },
    },
    // Handles both Prisma $transaction forms used across the codebase: an array of already-
    // invoked operation promises (src/settlement/engine.ts's persist phase) and an interactive
    // callback (src/api/purse/routes.ts, src/api/family/routes.ts). `tx` is typed `any` to avoid
    // a circular type reference — test-only mock code.
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

function allocate(sessionToken: string, body: Record<string, unknown>) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/family/allocate',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: body,
  });
}

function fetchActivity(sessionToken: string, parentAccountId: string) {
  return app.inject({
    method: 'GET',
    url: `/api/v1/family/activity?parent_account_id=${parentAccountId}`,
    headers: { authorization: `Bearer ${sessionToken}` },
  });
}

function syncBatch(sessionToken: string, transactions: unknown[]) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/sync/transactions',
    headers: { authorization: `Bearer ${sessionToken}` },
    payload: { transactions },
  });
}

/** Enrolls a parent (with a funded account) and a child device, both ready to call
 * POST /family/allocate. */
async function setupParentAndChild(parentBalance = 10_000_000n) {
  const parent = await enrollTestDevice(app);
  const child = await enrollTestDevice(app);
  __setAccountBalance(parent.accountId, parentBalance);
  const parentSession = await getSessionToken(parent);
  return { parent, child, parentSession };
}

describe('POST /api/v1/family/allocate', () => {
  it('succeeds with sufficient parent balance, debits it, and mints a child purse token', async () => {
    const { parent, child, parentSession } = await setupParentAndChild(10_000_000n);

    const response = await allocate(parentSession, {
      parent_account_id: parent.accountId,
      child_device_id: child.deviceId,
      allocated_amount: '50000',
      cap: '20000',
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();

    expect(body.allocation.parent_account_id).toBe(parent.accountId);
    expect(body.allocation.child_device_id).toBe(child.deviceId);
    expect(body.allocation.allocated_amount).toBe('50000');
    expect(body.allocation.cap).toBe('20000');
    expect(body.allocation.spent_amount).toBe('0');
    expect(body.allocation.active).toBe(true);
    expect(typeof body.allocation.id).toBe('string');
    expect(typeof body.allocation.created_at).toBe('number');

    // Purse token PEM was minted for the CHILD device, value = allocated_amount, cap as given
    // (both below allocated_amount, so no clamping kicks in here).
    const tokenJson = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, body.purse_token).toString('utf8'));
    expect(tokenJson.device_id).toBe(child.deviceId);
    expect(tokenJson.value).toBe('50000');
    expect(tokenJson.cap).toBe('20000');

    // Parent's real_balance was debited by allocated_amount.
    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: { account: { findUnique: (args: { where: { id: string } }) => Promise<{ realBalance: bigint }> } };
    };
    const account = await prisma.account.findUnique({ where: { id: parent.accountId } });
    expect(account?.realBalance).toBe(10_000_000n - 50_000n);
  });

  it('clamps the child purse token cap to allocated_amount when the requested cap is larger', async () => {
    const { parent, child, parentSession } = await setupParentAndChild();

    const response = await allocate(parentSession, {
      parent_account_id: parent.accountId,
      child_device_id: child.deviceId,
      allocated_amount: '10000',
      cap: '50000', // exceeds allocated_amount
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.allocation.cap).toBe('10000'); // clamped down to allocated_amount
    const tokenJson = JSON.parse(pemDecode(PURSE_TOKEN_PEM_LABEL, body.purse_token).toString('utf8'));
    expect(tokenJson.cap).toBe('10000');
  });

  it('fails with insufficient parent balance', async () => {
    const { parent, child, parentSession } = await setupParentAndChild(10_000n);

    const response = await allocate(parentSession, {
      parent_account_id: parent.accountId,
      child_device_id: child.deviceId,
      allocated_amount: '50000', // exceeds the 10000 balance
      cap: '20000',
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatch(/insufficient balance/);
  });

  it('requires a valid session', async () => {
    const { parent, child } = await setupParentAndChild();
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/family/allocate',
      payload: {
        parent_account_id: parent.accountId,
        child_device_id: child.deviceId,
        allocated_amount: '10000',
        cap: '5000',
      },
    });
    expect(response.statusCode).toBe(401);
  });

  it('rejects a session that does not belong to the parent account', async () => {
    const { child } = await setupParentAndChild();
    // A second, unrelated account/device — its session is valid but isn't the parent's.
    const outsider = await enrollTestDevice(app);
    __setAccountBalance(outsider.accountId, 10_000_000n);
    const outsiderSession = await getSessionToken(outsider);

    const response = await allocate(outsiderSession, {
      parent_account_id: randomUUID(), // some account outsider's session doesn't belong to
      child_device_id: child.deviceId,
      allocated_amount: '10000',
      cap: '5000',
    });
    expect(response.statusCode).toBe(403);
  });

  it("fails if child_device_id doesn't belong to a real enrolled device", async () => {
    const { parent, parentSession } = await setupParentAndChild();

    const response = await allocate(parentSession, {
      parent_account_id: parent.accountId,
      child_device_id: randomUUID(), // never enrolled
      allocated_amount: '10000',
      cap: '5000',
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error).toMatch(/child_device_id/);
  });
});

describe('GET /api/v1/family/activity', () => {
  it("returns spent_amount reflecting the child's actual settled spending", async () => {
    const { parent, child, parentSession } = await setupParentAndChild();
    const merchant = await enrollTestDevice(app);

    const allocateResponse = await allocate(parentSession, {
      parent_account_id: parent.accountId,
      child_device_id: child.deviceId,
      allocated_amount: '50000',
      cap: '50000',
    });
    expect(allocateResponse.statusCode).toBe(200);
    const tokenJson = JSON.parse(
      pemDecode(PURSE_TOKEN_PEM_LABEL, allocateResponse.json().purse_token).toString('utf8'),
    );

    // Before any spending, activity should show spent_amount = "0".
    const beforeResponse = await fetchActivity(parentSession, parent.accountId);
    expect(beforeResponse.statusCode).toBe(200);
    expect(beforeResponse.json().allocations).toHaveLength(1);
    expect(beforeResponse.json().allocations[0].spent_amount).toBe('0');

    // Child settles two real transactions against its allocated purse token.
    const tx1 = makeSignedTx({
      payer: child,
      payee: merchant,
      tokenId: tokenJson.token_id,
      amount: '12000',
      deviceCounter: tokenJson.counter_start,
      prevTx: null,
    });
    const tx2 = makeSignedTx({
      payer: child,
      payee: merchant,
      tokenId: tokenJson.token_id,
      amount: '8000',
      deviceCounter: tokenJson.counter_start + 1,
      prevTx: tx1,
    });
    const merchantSession = await getSessionToken(merchant);
    const syncResponse = await syncBatch(merchantSession, [tx1, tx2]);
    expect(syncResponse.statusCode).toBe(200);
    expect(syncResponse.json().results.every((r: { status: string }) => r.status === 'accepted')).toBe(true);

    // spent_amount is computed fresh from that settled history, not from a stored counter — see
    // the schema.prisma comment on FamilyAllocation.spentAmount.
    const afterResponse = await fetchActivity(parentSession, parent.accountId);
    expect(afterResponse.statusCode).toBe(200);
    const allocations = afterResponse.json().allocations;
    expect(allocations).toHaveLength(1);
    expect(allocations[0].spent_amount).toBe('20000'); // 12000 + 8000
    expect(allocations[0].allocated_amount).toBe('50000');
  });

  it("a parent can't see another parent's allocations", async () => {
    const { parent: parentA, child: childA, parentSession: sessionA } = await setupParentAndChild();
    const { parent: parentB, parentSession: sessionB } = await setupParentAndChild();

    const allocateResponse = await allocate(sessionA, {
      parent_account_id: parentA.accountId,
      child_device_id: childA.deviceId,
      allocated_amount: '10000',
      cap: '5000',
    });
    expect(allocateResponse.statusCode).toBe(200);

    // Parent B querying parent A's account_id is rejected — session-scoped, not just filtered.
    const crossResponse = await fetchActivity(sessionB, parentA.accountId);
    expect(crossResponse.statusCode).toBe(403);

    // Parent B's own (empty) activity is unaffected.
    const ownResponse = await fetchActivity(sessionB, parentB.accountId);
    expect(ownResponse.statusCode).toBe(200);
    expect(ownResponse.json().allocations).toEqual([]);
  });

  it('requires a valid session', async () => {
    const response = await app.inject({
      method: 'GET',
      url: `/api/v1/family/activity?parent_account_id=${randomUUID()}`,
    });
    expect(response.statusCode).toBe(401);
  });
});

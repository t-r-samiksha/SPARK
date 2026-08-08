import { randomUUID } from 'crypto';

// In-memory fake covering the models the admin routes (plus /enroll, needed to set up test
// devices) touch. Declared entirely inside the factory per Jest's mock-hoisting rules.
jest.mock('../../../src/db/client', () => {
  const devicesById = new Map<string, Record<string, unknown>>();
  const devicesByPublicKey = new Map<string, Record<string, unknown>>();
  const revokedCerts = new Map<string, { certSerial: string; revokedAt: Date; reason: string }>();
  const disasterEvents: Array<Record<string, unknown> & { id: string; regionGeo: string; active: boolean }> = [];
  const incidents: Array<Record<string, unknown> & { id: string; detectedAt: Date }> = [];

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
        findFirst: async ({ where }: { where: { regionGeo: string; active: boolean } }) =>
          disasterEvents.find((e) => e.regionGeo === where.regionGeo && e.active === where.active) ?? null,
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
      doubleSpendIncident: {
        create: async ({ data }: { data: Record<string, unknown> }) => {
          const record = { id: randomUUID(), detectedAt: new Date(), ...data } as Record<string, unknown> & {
            id: string;
            detectedAt: Date;
          };
          incidents.push(record);
          return record;
        },
        findMany: async () => [...incidents].sort((a, b) => b.detectedAt.getTime() - a.detectedAt.getTime()),
      },
    },
  };
});

import { FastifyInstance } from 'fastify';
import { buildServer } from '../../../src/server';
import { generateEd25519KeyPair } from '../../../src/crypto';
import { enrollTestDevice } from '../../helpers/makeSignedTx';

const ADMIN_KEY = 'test-admin-key';

let app: FastifyInstance;

beforeAll(async () => {
  process.env.BANK_ROOT_CA_KEY_SEED = generateEd25519KeyPair().privateSeed;
  process.env.BANK_SIGNING_KEY_SEED = generateEd25519KeyPair().privateSeed;
  process.env.ADMIN_API_KEY = ADMIN_KEY;

  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

function adminHeaders() {
  return { 'x-admin-key': ADMIN_KEY };
}

async function toggleDisaster(body: Record<string, unknown>, headers: Record<string, string> = adminHeaders()) {
  return app.inject({ method: 'POST', url: '/api/v1/admin/disaster/toggle', headers, payload: body });
}

async function getIncidents(query = '', headers: Record<string, string> = adminHeaders()) {
  return app.inject({ method: 'GET', url: `/api/v1/admin/incidents${query}`, headers });
}

async function revokeDevice(body: Record<string, unknown>, headers: Record<string, string> = adminHeaders()) {
  return app.inject({ method: 'POST', url: '/api/v1/admin/revoke', headers, payload: body });
}

describe('POST /api/v1/admin/disaster/toggle', () => {
  it('enabling a fresh region creates an event with started_at set', async () => {
    const region = `region-${randomUUID()}`;
    const response = await toggleDisaster({ region_geo: region, type: 'flood', enabled: true });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.region_geo).toBe(region);
    expect(body.type).toBe('flood');
    expect(body.enabled).toBe(true);
    expect(typeof body.id).toBe('string');
    expect(typeof body.updated_at).toBe('number');
  });

  it('enabling an already-active region updates fields without creating a duplicate', async () => {
    const region = `region-${randomUUID()}`;
    const first = await toggleDisaster({ region_geo: region, type: 'flood', enabled: true, essential_only: false });
    const firstId = first.json().id;

    const second = await toggleDisaster({
      region_geo: region,
      type: 'earthquake',
      enabled: true,
      higher_cap: '500000',
      essential_only: true,
    });

    expect(second.statusCode).toBe(200);
    const body = second.json();
    expect(body.id).toBe(firstId); // same event updated, not a new one
    expect(body.type).toBe('earthquake');
    expect(body.higher_cap).toBe('500000');
    expect(body.essential_only).toBe(true);
  });

  it('disabling an active region sets ended_at (via the resulting enabled=false state)', async () => {
    const region = `region-${randomUUID()}`;
    await toggleDisaster({ region_geo: region, type: 'flood', enabled: true });

    const response = await toggleDisaster({ region_geo: region, type: 'flood', enabled: false });
    expect(response.statusCode).toBe(200);
    expect(response.json().enabled).toBe(false);

    // Re-disabling should now 404 (no active event left) — indirect proof the event was actually
    // ended (active: false, ended_at: now) rather than left active.
    const again = await toggleDisaster({ region_geo: region, type: 'flood', enabled: false });
    expect(again.statusCode).toBe(404);
  });

  it('disabling a region with no active event returns 404', async () => {
    const region = `region-${randomUUID()}`;
    const response = await toggleDisaster({ region_geo: region, type: 'flood', enabled: false });
    expect(response.statusCode).toBe(404);
  });

  it('rejects a request with no X-Admin-Key header with 401', async () => {
    const response = await toggleDisaster({ region_geo: 'x', type: 'flood', enabled: true }, {});
    expect(response.statusCode).toBe(401);
  });
});

describe('GET /api/v1/admin/incidents', () => {
  it('returns recorded double-spend incidents', async () => {
    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: { doubleSpendIncident: { create: (args: { data: Record<string, unknown> }) => Promise<unknown> } };
    };
    const tokenId = randomUUID();
    const deviceId = randomUUID();
    const txIdA = randomUUID();
    const txIdB = randomUUID();
    await prisma.doubleSpendIncident.create({
      data: { tokenId, deviceId, txIdA, txIdB },
    });

    const response = await getIncidents();
    expect(response.statusCode).toBe(200);
    const { incidents } = response.json();
    expect(incidents).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: 'double_spend',
          token_id: tokenId,
          device_id: deviceId,
          tx_id_a: txIdA,
          tx_id_b: txIdB,
        }),
      ]),
    );
  });

  it('filters by type=double_spend and type=fraud_flag', async () => {
    const doubleSpendOnly = await getIncidents('?type=double_spend');
    expect(doubleSpendOnly.statusCode).toBe(200);
    expect(doubleSpendOnly.json().incidents.length).toBeGreaterThan(0);
    for (const incident of doubleSpendOnly.json().incidents) {
      expect(incident.type).toBe('double_spend');
    }

    const fraudOnly = await getIncidents('?type=fraud_flag');
    expect(fraudOnly.statusCode).toBe(200);
    expect(fraudOnly.json().incidents).toEqual([]);
  });

  it('rejects a request with no X-Admin-Key header with 401', async () => {
    const response = await getIncidents('', {});
    expect(response.statusCode).toBe(401);
  });
});

describe('POST /api/v1/admin/revoke', () => {
  it('revokes a device, reflected in both RevokedCertificate and Device.revokedAt', async () => {
    const device = await enrollTestDevice(app);

    const response = await revokeDevice({ device_id: device.deviceId, reason: 'lost phone' });
    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body).toEqual(
      expect.objectContaining({
        device_id: device.deviceId,
        serial_number: device.serialNumber,
        reason: 'lost phone',
      }),
    );
    expect(typeof body.revoked_at).toBe('number');

    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: {
        revokedCertificate: { findUnique: (args: { where: { certSerial: string } }) => Promise<unknown> };
        device: { findUnique: (args: { where: { deviceId: string } }) => Promise<{ revokedAt: Date | null }> };
      };
    };
    const revokedCert = await prisma.revokedCertificate.findUnique({ where: { certSerial: device.serialNumber } });
    expect(revokedCert).not.toBeNull();

    const deviceRow = await prisma.device.findUnique({ where: { deviceId: device.deviceId } });
    expect(deviceRow?.revokedAt).not.toBeNull();
  });

  it('revoking a device twice with different reasons leaves both records holding the latest reason', async () => {
    const device = await enrollTestDevice(app);

    const first = await revokeDevice({ device_id: device.deviceId, reason: 'double-spend auto-revoke' });
    expect(first.statusCode).toBe(200);

    const second = await revokeDevice({ device_id: device.deviceId, reason: 'admin: confirmed stolen phone' });
    expect(second.statusCode).toBe(200);
    expect(second.json().reason).toBe('admin: confirmed stolen phone');

    const { prisma } = jest.requireMock('../../../src/db/client') as {
      prisma: {
        revokedCertificate: {
          findUnique: (args: { where: { certSerial: string } }) => Promise<{ reason: string } | null>;
        };
        device: { findUnique: (args: { where: { deviceId: string } }) => Promise<{ revokedReason: string | null }> };
      };
    };
    const revokedCert = await prisma.revokedCertificate.findUnique({ where: { certSerial: device.serialNumber } });
    const deviceRow = await prisma.device.findUnique({ where: { deviceId: device.deviceId } });

    expect(revokedCert?.reason).toBe('admin: confirmed stolen phone');
    expect(deviceRow?.revokedReason).toBe('admin: confirmed stolen phone');
    expect(revokedCert?.reason).toBe(deviceRow?.revokedReason);
  });

  it('returns 404 for an unknown device_id', async () => {
    const response = await revokeDevice({ device_id: randomUUID(), reason: 'test' });
    expect(response.statusCode).toBe(404);
  });

  it('rejects a request with no X-Admin-Key header with 401', async () => {
    const device = await enrollTestDevice(app);
    const response = await revokeDevice({ device_id: device.deviceId, reason: 'x' }, {});
    expect(response.statusCode).toBe(401);
  });
});

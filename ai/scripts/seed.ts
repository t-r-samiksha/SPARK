/**
 * Development fixture for the intelligence models.
 *
 * NOT production data and NOT cryptographically valid: certificate/signature columns carry
 * placeholder strings, because the models score behaviour (amounts, timing, counterparties) and
 * never verify signatures. Anything that does verify signatures must use a real enrolment flow,
 * not this.
 *
 * Creates a small fleet with deliberately different behavioural profiles so each signal can be
 * seen to move:
 *   - veteran     long settled history, prompt syncs, many counterparties  → high cap
 *   - newcomer    no history at all                                        → low cap
 *   - offender    a confirmed double-spend incident on record              → penalised cap
 *   - hoarder     spends offline and syncs days later                      → fraud: offline duration
 *   - burst       many transactions in a few hours + one huge outlier      → fraud: velocity/anomaly
 *   - ring_a/b    almost every transaction with each other                 → fraud: concentration
 *
 *   npm run db:seed
 */
import { randomUUID } from 'node:crypto';
import { prisma } from '../src/db.ts';

const HOUR = 3600;
const DAY = 86_400;
const now = Math.floor(Date.now() / 1000);

const placeholder = (label: string) => `dev-fixture-${label}-not-a-real-signature`;

async function device(accountId: string, label: string): Promise<string> {
  const deviceId = randomUUID();
  await prisma.device.create({
    data: {
      deviceId,
      accountId,
      devicePublicKey: `dev-fixture-pk-${label}-${deviceId}`,
      serialNumber: `FIXTURE-${label.toUpperCase()}-${deviceId.slice(0, 8)}`,
      notBefore: new Date((now - 90 * DAY) * 1000),
      notAfter: new Date((now + 275 * DAY) * 1000),
      signature: placeholder(label),
    },
  });
  return deviceId;
}

async function transaction(
  payer: string,
  payerAccount: string,
  payee: string,
  payeeAccount: string,
  amountPaise: number,
  agoSeconds: number,
  offlineSeconds: number,
  counter: number,
): Promise<void> {
  const timestamp = now - agoSeconds;
  await prisma.transaction.create({
    data: {
      txId: randomUUID(),
      tokenId: randomUUID(),
      amount: String(amountPaise),
      payerDeviceId: payer,
      payerAccountId: payerAccount,
      payerCert: placeholder('payer-cert'),
      payeeDeviceId: payee,
      payeeAccountId: payeeAccount,
      payeeCert: placeholder('payee-cert'),
      deviceCounter: counter,
      timestamp,
      signature: placeholder('tx'),
      syncedAt: new Date((timestamp + offlineSeconds) * 1000),
    },
  });
}

async function trustEdge(a: string, b: string, settledPaise: number, count: number): Promise<void> {
  const [subjectA, subjectB] = a < b ? [a, b] : [b, a];
  await prisma.trustAttestation.create({
    data: {
      subjectA,
      subjectB,
      settledAmount: String(settledPaise),
      settlementCount: count,
      timestamp: new Date(now * 1000),
      lastSettledAt: new Date(now * 1000),
      signature: placeholder('attestation'),
    },
  });
}

async function main(): Promise<void> {
  console.log('Clearing previous fixture data…');
  await prisma.trustAttestation.deleteMany({});
  await prisma.doubleSpendIncident.deleteMany({});
  await prisma.transaction.deleteMany({});
  await prisma.purseToken.deleteMany({});
  await prisma.device.deleteMany({});
  await prisma.account.deleteMany({});

  const account = await prisma.account.create({
    data: { id: randomUUID(), realBalance: 5_000_000n },
  });
  const thinAccount = await prisma.account.create({
    data: { id: randomUUID(), realBalance: 120_000n },
  });

  const veteran = await device(account.id, 'veteran');
  const newcomer = await device(account.id, 'newcomer');
  const offender = await device(account.id, 'offender');
  const hoarder = await device(account.id, 'hoarder');
  const burst = await device(account.id, 'burst');
  const ringA = await device(account.id, 'ring-a');
  const ringB = await device(account.id, 'ring-b');
  const thin = await device(thinAccount.id, 'thin-balance');
  const merchants = await Promise.all([
    device(account.id, 'merchant-1'),
    device(account.id, 'merchant-2'),
    device(account.id, 'merchant-3'),
    device(account.id, 'merchant-4'),
  ]);

  // veteran — 24 settled spends over 60 days, always synced within ~20 minutes.
  for (let i = 0; i < 24; i++) {
    await transaction(
      veteran,
      account.id,
      merchants[i % merchants.length]!,
      account.id,
      20_000 + (i % 5) * 4_000,
      (60 - i * 2) * DAY,
      15 * 60 + (i % 4) * 120,
      i + 1,
    );
  }
  for (const [i, m] of merchants.entries()) {
    await trustEdge(veteran, m!, 400_000 + i * 50_000, 6 + i);
  }
  await trustEdge(merchants[0]!, merchants[1]!, 200_000, 4);
  await trustEdge(merchants[1]!, merchants[2]!, 150_000, 3);

  // offender — modest history, then a confirmed double-spend.
  for (let i = 0; i < 6; i++) {
    await transaction(offender, account.id, merchants[0]!, account.id, 30_000, (20 - i) * DAY, 45 * 60, i + 1);
  }
  await trustEdge(offender, merchants[0]!, 180_000, 6);
  await prisma.doubleSpendIncident.create({
    data: {
      tokenId: randomUUID(),
      deviceId: offender,
      txIdA: randomUUID(),
      txIdB: randomUUID(),
      detectedAt: new Date((now - 2 * DAY) * 1000),
    },
  });

  // hoarder — spends offline, settles up to 6 days later.
  for (let i = 0; i < 5; i++) {
    await transaction(hoarder, account.id, merchants[1]!, account.id, 25_000, (10 - i) * DAY, 6 * DAY, i + 1);
  }
  await trustEdge(hoarder, merchants[1]!, 125_000, 5);

  // burst — 14 spends inside one day, with a final outlier ~12x the median.
  for (let i = 0; i < 13; i++) {
    await transaction(burst, account.id, merchants[i % 3]!, account.id, 8_000, 20 * HOUR - i * HOUR, 30 * 60, i + 1);
  }
  await transaction(burst, account.id, merchants[2]!, account.id, 96_000, 2 * HOUR, 30 * 60, 14);
  await trustEdge(burst, merchants[0]!, 60_000, 5);

  // ring — value cycles BOTH ways between the same two devices, which is what distinguishes a
  // settlement ring from a customer who simply always pays the same merchant.
  for (let i = 0; i < 9; i++) {
    await transaction(ringA, account.id, ringB, account.id, 15_000, (9 - i) * DAY, 40 * 60, i + 1);
    await transaction(ringB, account.id, ringA, account.id, 14_000, (9 - i) * DAY - HOUR, 40 * 60, i + 1);
  }
  await transaction(ringA, account.id, merchants[3]!, account.id, 15_000, 1 * DAY, 40 * 60, 10);
  await trustEdge(ringA, ringB, 135_000, 9);

  // thin — good behaviour, but the account cannot back a large cap.
  for (let i = 0; i < 8; i++) {
    await transaction(thin, thinAccount.id, merchants[0]!, account.id, 10_000, (16 - i * 2) * DAY, 20 * 60, i + 1);
  }
  await trustEdge(thin, merchants[0]!, 80_000, 8);

  console.log('Fixture ready:');
  console.log(`  veteran   ${veteran}`);
  console.log(`  newcomer  ${newcomer}`);
  console.log(`  offender  ${offender}`);
  console.log(`  hoarder   ${hoarder}`);
  console.log(`  burst     ${burst}`);
  console.log(`  ring-a    ${ringA}`);
  console.log(`  thin      ${thin}`);
}

await main();
await prisma.$disconnect();

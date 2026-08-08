import { prisma } from './client';

// Demo/dev seed data — NOT for production. Creates a funded demo account so the team can
// exercise POST /api/v1/purse/load without manually editing the database.
//
// Run with `npx prisma db seed` (wired up via the `prisma.seed` key in package.json) or directly
// via `npx tsx src/db/seed.ts`.
//
// This only creates the Account row — POST /api/v1/purse/load needs an enrolled *device* too.
// After seeding, enroll a device against DEMO_ACCOUNT_ID via POST /api/v1/enroll to get a working
// device + session for testing the full purse/load flow.

const DEMO_ACCOUNT_ID = '00000000-0000-4000-8000-000000000001';
const DEMO_REAL_BALANCE_PAISE = 5_000_000n; // ₹50,000

async function main(): Promise<void> {
  const account = await prisma.account.upsert({
    where: { id: DEMO_ACCOUNT_ID },
    create: { id: DEMO_ACCOUNT_ID, realBalance: DEMO_REAL_BALANCE_PAISE },
    update: { realBalance: DEMO_REAL_BALANCE_PAISE },
  });

  console.log(`Seeded demo account ${account.id} with real_balance = ${account.realBalance} paise.`);
  console.log(`Enroll a device against account_id=${account.id} via POST /api/v1/enroll,`);
  console.log('then POST /api/v1/purse/load with that device\'s session to test against this balance.');
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });

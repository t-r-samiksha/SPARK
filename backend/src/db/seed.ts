import { prisma } from './client';
import { DEMO_ACCOUNT_ID, DEMO_REAL_BALANCE_PAISE } from './demoAccount';

// Demo/dev seed data — NOT for production. Creates a funded demo account so the team can
// exercise POST /api/v1/purse/load without manually editing the database.
//
// Run with `npx prisma db seed` (wired up via the `prisma.seed` key in package.json) or directly
// via `npx tsx src/db/seed.ts`.
//
// This only creates the Account row — POST /api/v1/purse/load needs an enrolled *device* too.
// After seeding, enroll a device against DEMO_ACCOUNT_ID via POST /api/v1/enroll to get a working
// device + session for testing the full purse/load flow.
//
// This file runs main() unconditionally on load — it's a standalone script, not a pure module.
// Other code that needs DEMO_ACCOUNT_ID must import it from ./demoAccount, not from here, or
// importing it will trigger a real (and likely failing, if no DB is configured) seed run.

async function main(): Promise<void> {
  const before = await prisma.account.findUnique({ where: { id: DEMO_ACCOUNT_ID } });

  const account = await prisma.account.upsert({
    where: { id: DEMO_ACCOUNT_ID },
    create: { id: DEMO_ACCOUNT_ID, realBalance: DEMO_REAL_BALANCE_PAISE },
    // Unconditional (not merged with the existing value): re-running this script is meant to
    // RESET the demo account back to a known balance for repeatable local/smoke testing — e.g.
    // after scripts/smokeTestTrust.ts debits it via /purse/load — not just "create if missing."
    // Testing convenience only; nothing else in this codebase resets a real account's balance
    // like this (real accounts are only ever debited, via /purse/load).
    update: { realBalance: DEMO_REAL_BALANCE_PAISE },
  });

  if (before) {
    console.log(
      `Reset demo account ${account.id}'s real_balance: ${before.realBalance} -> ${account.realBalance} paise.`,
    );
  } else {
    console.log(`Created demo account ${account.id} with real_balance = ${account.realBalance} paise.`);
  }
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

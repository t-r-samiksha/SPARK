# SPARK Backend

The bank-side backend for SPARK, an offline-capable digital wallet system. Node.js + TypeScript
(Fastify + Prisma/Postgres).

## Setup

```
npm install
```

Copy `.env.example` to `.env` and fill in the values (see that file for what each one is —
secrets aren't repeated here). You'll need two Ed25519 key seeds; generate each with:

```
npx tsx -e "import { generateEd25519KeyPair } from './src/crypto'; console.log(generateEd25519KeyPair().privateSeed)"
```

Run once for `BANK_ROOT_CA_KEY_SEED` and once more for `BANK_SIGNING_KEY_SEED` — they're
deliberately separate keys (cold Root CA vs. operational signing key), so don't reuse one seed for
both.

Then apply migrations against your `DATABASE_URL`:

```
npx prisma migrate deploy
```

## Running locally

```
npm run dev
```

## Running tests

```
npm test
```

## Deployed instance

Production: **https://spark-m1pt.onrender.com**

## What's implemented

- **Enrollment** — device enrollment issues a Bank-signed device certificate.
- **Auth** — challenge/response session auth (device signs a nonce with its enrolled key).
- **Purse** — loading an offline-spendable purse token, debited from account real_balance.
- **Settlement** — batch settlement of signed offline transactions (`/sync/transactions`), with counter/hash-chain continuity and per-token value/cap enforcement.
- **Double-spend detection** — conflicting transactions for the same token/counter slot are caught, recorded as incidents, and the offending device is revoked.
- **Trust storage** — settled transactions accumulate signed trust attestations between devices.
- **Escrow** — cooperative-case buyer-locks/buyer-releases fund holds, settled as a real chained transaction.
- **Family wallet** — a parent account can allocate a scoped, capped purse token to a child device.
- **Disaster mode** — admin-toggled regional events can raise the offline spend cap and flag essential-only mode to devices.
- **Admin** — incident listing, device revocation, and disaster-mode toggling behind a shared admin key.
- **SMS rail (simulated)** — a webhook that decodes a signed transaction from an SMS body and settles it through the same path as `/sync/transactions`.

Rate limiting (100 req/min per IP, hackathon-scope) applies globally except `/health`.

See [docs/api-contract.md](../docs/api-contract.md) for the full endpoint reference.

## Smoke tests against production

`scripts/smokeTestEnroll.ts` and `scripts/smokeTestTrust.ts` are runnable references showing real
request shapes against the deployed instance above (not part of the Jest suite):

```
npx tsx scripts/smokeTestEnroll.ts
npx tsx scripts/smokeTestTrust.ts
```

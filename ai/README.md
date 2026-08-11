# SPARK Intelligence Service (Member C)

Adaptive offline spend-cap recommendations, trust-graph traversal, and fraud intelligence for the
SPARK offline-payment network.

Runs as its own process. The bank server calls in; this service never calls out, and **never
writes** to the bank's database — a scoring model must not be able to corrupt the ledger it
scores. Every model here derives from settled history the settlement engine has already persisted.

## Why a separate service

`backend/src/api/purse/limitStub.ts` was written expecting exactly this:

> TODO: replace this with a real call to **Member C's `/limit/recommendation`** once available.

So the bank's own code changes very little — it gained a client, not a model.

## Running it

```bash
npm install
npm run db:up     # embedded Postgres — no Docker, no system install (leave running)
npm run db:seed   # development fixture with distinct behavioural profiles
npm run dev       # service on :3100
```

`db:up` downloads a real PostgreSQL binary into `ai/.pgdata` (gitignored) and serves it on 5432,
matching the `DATABASE_URL` in `backend/.env.example`. This exists because the repo README
documents the backend as "Node 20+ and `npm install`" while `prisma/schema.prisma` requires a
Postgres server, and nothing in the repo provided one.

To enable the models in the bank server, set in `backend/.env`:

```
AI_SERVICE_URL=http://localhost:3100
```

**Unset means off.** With no `AI_SERVICE_URL`, the backend behaves exactly as it did before this
service existed: a flat ₹2,000 cap and an empty fraud list. Backend tests and anyone running the
bank alone must never be made to depend on a second process being up.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | liveness |
| GET | `/limit/recommendation[?device_id=]` | adaptive cap; omit `device_id` for the fleet-median network default |
| GET | `/fraud/flags` | devices whose behaviour warrants operator review |
| GET | `/trust/:deviceId` | bounded ≤3-hop decayed trust traversal |

## The cap model

The cap is the value a device may spend while it **cannot be checked**, so this is an exposure
question, not a credit one. Four signals, each normalised to `[0,1]`, combined by the weights in
`src/config.ts`:

| Signal | Weight | Reads |
| --- | --- | --- |
| Settlement history | 0.35 | count and value of settled outgoing transactions |
| Trust graph | 0.30 | decayed ≤3-hop walk over settled-only trust edges |
| Sync discipline | 0.20 | median gap between offline spend and settlement |
| Incident record | 0.15 | confirmed double-spend incidents |

The composite maps onto a multiplier band applied to the ₹2,000 baseline, then:

- each confirmed double-spend **halves** the cap (see below),
- the account's real balance is a hard ceiling — the bank cannot authorise more offline value
  than the account holds,
- an active disaster event may raise it, which is an operator-authorised exception and is
  reported separately from the model's own output,
- absolute bounds of ₹500 – ₹10,000 apply throughout,
- a revoked device gets `0`, with no scoring at all.

Every response carries its own signal decomposition, so the console can show *why* a device got
the cap it got. Nothing displayed is illustrative.

### Why incidents apply a multiplicative penalty

The weighted score alone produced a perverse result: a device with long history and strong trust
out-scored its own incident record, so a device with a **confirmed double-spend** was offered a
higher offline cap than a device with no history at all. Proven abuse of the exact failure this
cap exists to bound has to dominate the evidence that preceded it.

## Fraud intelligence

**This is an original design decision, not an implementation of a written spec.**
`docs/api-contract.md` defines only `type: "fraud_flag"` and says the shape is "TBD once
fraud-detection logic exists". Nothing in the repo states what fraud means for SPARK, so the
signals below are a proposal for the team to revise.

| Signal | Reads |
| --- | --- |
| Spend velocity | transactions in the last 24h |
| Offline duration | longest gap between offline spend and settlement |
| Amount anomaly | largest spend against the device's *own* median |
| Circular flow | value cycling back and forth with the same counterparty |

Three rules keep the fraud tab something an operator can trust:

1. **Corroboration.** Most signals have an innocent reading — a market trader has high velocity, a
   rural user syncs late, a family pays each other. Two independent signals must agree.
2. **Materiality.** A signal that technically fired but scored near zero is dropped rather than
   averaged in, so one strong signal is never hidden by noise.
3. **One exception.** Circular flow at high balance is structurally conclusive on its own: a shop
   does not pay its customers back nine times. Requiring a second signal there would mean seeing
   a settlement ring and staying silent.

Deliberately **not** used: anything derived from the account holder, location, or counterparty
identity. A flag must be answerable by "this device's own behaviour changed", so an operator is
never asked to act on a proxy for who someone is.

**A flag is a prompt to look, never an action.** This service cannot revoke, block, or write.

## Trust traversal

`backend/src/settlement/trustEdges.ts` writes one symmetric edge per device pair that has
**settled** at least one transaction — the Sybil-resistance property, since trust cannot be
manufactured by spinning up devices. `src/trustGraph.ts` walks that graph breadth-first to 3 hops,
halving the weight per hop, counting each device once at its shortest distance so a dense cluster
cannot inflate its own endorsement.

Edge weight is driven by settlement *count* and only modulated by amount: repeat settlement is
stronger evidence than one large transfer, so trust cannot be bought in a single payment.

> That module cites a "Phase 8 spec" for this design. **That spec is not in this repository.** The
> traversal implements the two properties the code comment actually states — bounded hops,
> decaying weight — and declares its own constants in `src/config.ts` rather than inventing a
> citation. Worth confirming with the team whether the spec exists elsewhere.

## Tests

```bash
npm test
```

Pure unit tests over the fraud scorer, no database required. Each case pins a failure the first
working version of the model actually had.

## Caveats

- The models are explainable rules-and-weights scorers, **not** learned models. There is no
  training data yet, and every constant in `src/config.ts` is a policy decision open to argument
  rather than a tuned parameter.
- `scripts/seed.ts` writes fixture rows whose certificate and signature columns are placeholder
  strings. The models score behaviour and never verify signatures; anything that *does* verify
  signatures must use a real enrolment flow.
- `prisma/schema.prisma` is a copy of the backend's. It is read-only here; if the backend's schema
  changes, re-copy it and re-run `npm run prisma:generate`.

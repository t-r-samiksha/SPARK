# SPARK

SPARK is a mobile digital-wallet system: an Android wallet app talks to a bank backend over a signed, canonically-serialized transaction protocol, with an admin dashboard for operations and oversight.

## Repository layout

```
android/     Wallet app (Kotlin, Gradle)
backend/     Bank server (Node/TypeScript)
dashboard/   Admin web UI (React/TypeScript, Vite)
docs/        Shared contracts: wire formats, crypto, API, conventions
```

All cross-component contracts (transaction format, certificate format, crypto choices, API shape, etc.) live in [docs/](docs/) and are binding for every workstream — see [docs/README.md](docs/README.md) for the index.

## Branching model

- `main` — the source of truth. All work branches from `main` and PRs back into `main`; there's no separate integration branch.
- Feature/fix branches — any name, branched from `main` (e.g. `feat/purse-topup`, `fix/123`, or a personal branch).
- Review requirement depends on what the branch touches:
  - Changes to shared contracts in `docs/` — 1 approving review required before merging.
  - Changes scoped to a single workstream (`android/`, `backend/`, `dashboard/`) — self-approved, the author merges once they're happy with it.
- No CI yet — for now, review (where required above) is manual. We'll revisit once integration begins.

## Dev environment setup

### Android (wallet)

Requires: JDK 17, Android Studio (or command-line Gradle + Android SDK).

```
cd android
./gradlew build
```

### Backend (bank server)

Requires: Node.js 20+ **and a PostgreSQL server** (`prisma/schema.prisma` uses the `postgresql`
provider — the server will not start without one).

If you do not have Postgres installed, `ai/` ships an embedded one that needs no Docker and no
system-wide install:

```
cd ai && npm install && npm run db:up     # leave running
```

Then:

```
cd backend
npm install
cp .env.example .env                       # fill in the key seeds and ADMIN_API_KEY
npx prisma migrate deploy
npm run dev
```

Optionally set `AI_SERVICE_URL=http://localhost:3100` in `backend/.env` to enable Member C's
cap-intelligence and fraud models. Leave it unset and the backend behaves exactly as before.

### Dashboard (admin web UI)

Requires: Node.js 20+.

```
cd dashboard
npm install
```

## Running each component

| Component | Command | Notes |
|---|---|---|
| Wallet (android/) | `./gradlew installDebug` | Deploys to a connected device/emulator |
| Backend (backend/) | `npm run dev` | Starts the bank server locally |
| Intelligence (ai/) | `npm run dev` | Starts Member C's cap/fraud models (optional) |
| Dashboard (dashboard/) | `npm run dev` | Starts the admin UI dev server, expects backend running |

## Shared contracts

Anything that crosses a process boundary (wallet → backend, backend → dashboard) is specified in [docs/](docs/), not in any one component's code. Changes to those contracts must be PR'd to `docs/` and agreed by all affected workstreams before implementation.

## Maintainers

| Member | Area | Name | Email |
|---|---|---|---|
| Member A | Android (wallet) | Akshayathiru | akshayat.it2024@citchennai.net |
| Member B | Backend | Samiksha | trsamiksha.it2024@citchennai.net |
| Member C | Dashboard / AI | Faleesha-Zaeen | faleeshazaeenzarshad.it2024@citchennai.net |

Shared contracts (`docs/`) are jointly owned by all three.

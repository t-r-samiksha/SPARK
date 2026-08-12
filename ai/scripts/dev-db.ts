/**
 * Local Postgres for development, with no Docker and no system-wide install.
 *
 * `embedded-postgres` downloads a real PostgreSQL binary into ai/.pgdata and runs it as a child
 * process, so the backend's Prisma datasource (provider = "postgresql") works unchanged. This
 * exists because the repo README documents the backend as "Node 20+ and npm install" but
 * prisma/schema.prisma requires a Postgres server — nothing in the repo provided one.
 *
 *   npm run db:up     start (and initialise on first run)
 *   npm run db:down   stop
 *
 * The data directory is gitignored. Credentials below are local-only development defaults and
 * match the DATABASE_URL in backend/.env.example.
 */
import EmbeddedPostgres from 'embedded-postgres';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dataDir = resolve(here, '..', '.pgdata');
const pidFile = resolve(here, '..', '.pgdata.pid');

const PORT = 5432;
const USER = 'spark';
const PASSWORD = 'spark';
const DATABASE = 'spark';

export const DATABASE_URL = `postgresql://${USER}:${PASSWORD}@localhost:${PORT}/${DATABASE}?schema=public`;

function makeServer(): EmbeddedPostgres {
  return new EmbeddedPostgres({
    databaseDir: dataDir,
    user: USER,
    password: PASSWORD,
    port: PORT,
    persistent: true,
  });
}

async function up(): Promise<void> {
  const firstRun = !existsSync(dataDir);
  if (firstRun) {
    mkdirSync(dataDir, { recursive: true });
  }

  const pg = makeServer();

  if (firstRun) {
    console.log('Initialising Postgres cluster (first run, downloads a binary)…');
    await pg.initialise();
  }

  await pg.start();

  // createDatabase throws if it already exists; a re-run of db:up must stay idempotent.
  try {
    await pg.createDatabase(DATABASE);
    console.log(`Created database "${DATABASE}".`);
  } catch {
    console.log(`Database "${DATABASE}" already present.`);
  }

  writeFileSync(pidFile, String(process.pid));
  console.log(`Postgres listening on port ${PORT}.`);
  console.log(`DATABASE_URL=${DATABASE_URL}`);
  console.log('Leave this process running; Ctrl+C or `npm run db:down` stops it.');

  const stop = async () => {
    await pg.stop();
    process.exit(0);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);

  // Hold the process open — embedded-postgres dies with its parent.
  await new Promise(() => {});
}

async function down(): Promise<void> {
  const pg = makeServer();
  await pg.stop();
  console.log('Postgres stopped.');
}

const command = process.argv[2];
if (command === 'up') {
  await up();
} else if (command === 'down') {
  await down();
} else {
  console.error('Usage: tsx scripts/dev-db.ts <up|down>');
  process.exit(1);
}

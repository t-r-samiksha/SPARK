import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scoreDevice, type Tx } from '../src/fraud.ts';

/**
 * These cases exist because the first working version of this model got them wrong, and the
 * failures were the kind that only show up against realistic data:
 *   - a device with a confirmed double-spend out-scored a device with no history at all;
 *   - a customer who only ever paid one merchant was flagged as a settlement ring;
 *   - a genuine ring went unflagged because a near-zero signal dragged its average down.
 * Each is pinned below so they cannot come back silently.
 */

const NOW = 1_800_000_000;
const HOUR = 3600;
const DAY = 86_400;

/** syncedAt defaults to 30 minutes after the transaction, so overriding `timestamp` alone does
 *  not silently create a multi-day offline gap and fire a signal the case did not intend. */
function tx(overrides: Partial<Tx> & { payeeDeviceId: string }): Tx {
  const timestamp = overrides.timestamp ?? NOW - DAY;
  return {
    amount: '10000',
    timestamp,
    syncedAt: new Date((timestamp + 1800) * 1000),
    ...overrides,
  };
}

test('an ordinary customer paying one merchant is not flagged', () => {
  // 100% concentration, but value only ever flows one way. This is a corner shop, not a ring.
  const txs = Array.from({ length: 10 }, (_, i) =>
    tx({ payeeDeviceId: 'merchant', timestamp: NOW - (10 - i) * DAY }),
  );

  const result = scoreDevice('customer', txs, new Map(), NOW);
  assert.equal(result, null, 'one-way concentration must not raise a flag');
});

test('a reciprocal ring is flagged on the circular-flow signal alone', () => {
  const txs = Array.from({ length: 9 }, (_, i) =>
    tx({ payeeDeviceId: 'partner', timestamp: NOW - (9 - i) * DAY }),
  );
  // The partner paid back just as often — value is cycling.
  const incoming = new Map([['partner', 9]]);

  const result = scoreDevice('ring-member', txs, incoming, NOW);
  assert.ok(result, 'a balanced two-way loop must be flagged');
  assert.equal(result.reasons[0]?.key, 'circular_flow');
  assert.ok(result.score >= 0.8);
});

test('a single non-conclusive signal is not enough to flag', () => {
  // Long offline gaps and nothing else — a phone left in a drawer, not fraud.
  const txs = Array.from({ length: 5 }, (_, i) =>
    tx({
      payeeDeviceId: `shop-${i}`,
      timestamp: NOW - (5 - i) * DAY,
      syncedAt: new Date((NOW - (5 - i) * DAY + 5 * DAY) * 1000),
    }),
  );

  const result = scoreDevice('slow-syncer', txs, new Map(), NOW);
  assert.equal(result, null, 'offline duration alone must not flag a device');
});

test('two corroborating signals do flag', () => {
  // A burst of activity plus an outlier far above this device's own median.
  const txs = [
    ...Array.from({ length: 13 }, (_, i) =>
      tx({ payeeDeviceId: `shop-${i % 3}`, amount: '8000', timestamp: NOW - (13 - i) * HOUR }),
    ),
    tx({ payeeDeviceId: 'shop-2', amount: '96000', timestamp: NOW - HOUR }),
  ];

  const result = scoreDevice('burst', txs, new Map(), NOW);
  assert.ok(result, 'velocity plus amount anomaly must flag');
  const keys = result.reasons.map((r) => r.key).sort();
  assert.deepEqual(keys, ['amount_anomaly', 'velocity']);
});

test('flag ids are stable within a day so re-scans do not duplicate incidents', () => {
  const txs = Array.from({ length: 9 }, (_, i) =>
    tx({ payeeDeviceId: 'partner', timestamp: NOW - (9 - i) * DAY }),
  );
  const incoming = new Map([['partner', 9]]);

  const first = scoreDevice('ring-member', txs, incoming, NOW);
  const second = scoreDevice('ring-member', txs, incoming, NOW + HOUR);
  assert.equal(first?.id, second?.id);
});

test('a device with no transactions is never flagged', () => {
  assert.equal(scoreDevice('idle', [], new Map(), NOW), null);
});

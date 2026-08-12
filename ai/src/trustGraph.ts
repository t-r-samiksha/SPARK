import { prisma } from './db.ts';
import { MAX_TRUST_HOPS, TRUST_HOP_DECAY } from './config.ts';

/**
 * Trust-graph traversal.
 *
 * backend/src/settlement/trustEdges.ts writes one symmetric TrustAttestation per device pair that
 * has SETTLED at least one transaction, and is explicit that this is the Sybil-resistance
 * property: an edge cannot exist without settled history, so trust cannot be manufactured by
 * spinning up devices. That module cites a "Phase 8 spec" for a bounded, decaying-weight chain,
 * but that spec is not present in this repository — so the traversal below implements the two
 * properties the code comment does state (bounded hops, decaying weight) and states its own
 * constants in config.ts rather than inventing a citation.
 *
 * Edges are symmetric: an attestation between A and B is stored once under a canonical ordering,
 * so a walk must look at both subject columns.
 */

export interface TrustResult {
  /** Total decayed trust weight reachable within MAX_TRUST_HOPS. */
  weight: number;
  /** Distinct devices reached, by hop distance. */
  reachedByHop: number[];
  /** Direct (1-hop) counterparties. */
  directCounterparties: number;
  /** Total settled paise across direct edges. */
  directSettledPaise: bigint;
}

interface Edge {
  other: string;
  settledAmount: bigint;
  settlementCount: number;
}

/** All edges incident to any device in `ids`, keyed by device. One query per hop, not per node. */
async function edgesFor(ids: string[]): Promise<Map<string, Edge[]>> {
  const rows = await prisma.trustAttestation.findMany({
    where: {
      OR: [{ subjectA: { in: ids } }, { subjectB: { in: ids } }],
    },
    select: {
      subjectA: true,
      subjectB: true,
      settledAmount: true,
      settlementCount: true,
    },
  });

  const byDevice = new Map<string, Edge[]>();
  const push = (from: string, edge: Edge) => {
    const list = byDevice.get(from);
    if (list) list.push(edge);
    else byDevice.set(from, [edge]);
  };

  for (const row of rows) {
    const amount = BigInt(row.settledAmount);
    if (ids.includes(row.subjectA)) {
      push(row.subjectA, {
        other: row.subjectB,
        settledAmount: amount,
        settlementCount: row.settlementCount,
      });
    }
    if (ids.includes(row.subjectB)) {
      push(row.subjectB, {
        other: row.subjectA,
        settledAmount: amount,
        settlementCount: row.settlementCount,
      });
    }
  }

  return byDevice;
}

/**
 * A single edge's intrinsic weight. Repeat settlement between the same pair is stronger evidence
 * than a single large one, so count drives the weight and amount only modulates it — a device
 * cannot buy trust with one big transfer.
 */
function edgeWeight(edge: Edge): number {
  const countTerm = Math.log2(1 + edge.settlementCount);
  const amountTerm = Math.log10(1 + Number(edge.settledAmount) / 100_000); // per ₹1,000 settled
  return countTerm * (1 + amountTerm);
}

/**
 * Breadth-first walk out to MAX_TRUST_HOPS, accumulating decayed edge weight. Each device is
 * counted once, at the shortest hop distance at which it is reached — otherwise a dense cluster
 * would let the same neighbour contribute repeatedly and inflate its own endorsement.
 */
export async function computeTrust(deviceId: string): Promise<TrustResult> {
  const visited = new Set<string>([deviceId]);
  const reachedByHop: number[] = [];
  let weight = 0;
  let directCounterparties = 0;
  let directSettledPaise = 0n;

  let frontier: string[] = [deviceId];

  for (let hop = 1; hop <= MAX_TRUST_HOPS && frontier.length > 0; hop++) {
    const edges = await edgesFor(frontier);
    const decay = TRUST_HOP_DECAY ** (hop - 1);
    const nextFrontier: string[] = [];

    for (const from of frontier) {
      for (const edge of edges.get(from) ?? []) {
        if (hop === 1) {
          directCounterparties++;
          directSettledPaise += edge.settledAmount;
        }
        if (visited.has(edge.other)) continue;
        visited.add(edge.other);
        nextFrontier.push(edge.other);
        weight += edgeWeight(edge) * decay;
      }
    }

    reachedByHop.push(nextFrontier.length);
    frontier = nextFrontier;
  }

  return { weight, reachedByHop, directCounterparties, directSettledPaise };
}

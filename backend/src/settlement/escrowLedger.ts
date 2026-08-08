import { prisma } from '../db/client';

/**
 * Computes the currently-available (unspent, unlocked) balance for a purse token: its signed
 * `value` minus everything already committed against it — settled transactions AND currently-
 * `locked` escrow contracts.
 *
 * THE LOCKED-FUNDS REPRESENTATION QUESTION: "locked" is not a stored field anywhere on
 * PurseToken — it's computed on demand from the EXISTENCE of EscrowContract rows with
 * status='locked' referencing a given token_id, the same way "settled" is computed from the
 * Transaction table rather than a mutable "current balance" counter.
 *
 * Why: docs/purse-token-format.md#spend-enforcement-decided already establishes that `value` is
 * signed once at issuance and NEVER mutated — the backend has no "current balance" field for a
 * purse token anywhere; normal spend enforcement is reconstructed from synced transaction history
 * at sync time specifically so there's no mutable balance that can drift from what the signed
 * history actually supports. Adding a mutable `lockedAmount` (or "remaining") field to PurseToken
 * to represent escrow holds would reintroduce exactly that risk, AND it would still need to be
 * kept in sync with the independently-computed settled-transaction total, so nothing is actually
 * saved by storing it — the computation still has to happen. Instead, this extends the SAME
 * "reconstruct from an append-only history" pattern to a second history table (EscrowContract)
 * alongside the first (Transaction).
 *
 * Tradeoff: /escrow/create's balance check is an O(n) scan over that token's settled transactions
 * + locked escrows on every call, instead of an O(1) field read. At hackathon scale (a purse
 * token's realistic lifetime transaction count) this is a non-issue; it would need an index or a
 * cached/denormalized counter before this matters at real scale.
 *
 * Escrow amounts count against the token's cap and value ceiling exactly like a normal
 * transaction would: /escrow/create enforces `amount <= token.cap` (the same per-spend ceiling a
 * normal transaction is held to) and this function folds locked escrow amounts into the same
 * `value` ceiling settled transactions already count against — a buyer can't over-commit beyond
 * their purse's signed value by stacking escrows and real transactions.
 */
export async function getAvailablePurseBalance(tokenValue: string, tokenId: string): Promise<bigint> {
  const [settledRows, lockedEscrows] = await Promise.all([
    prisma.transaction.findMany({ where: { tokenId } }),
    prisma.escrowContract.findMany({ where: { tokenId, status: 'locked' } }),
  ]);
  const settledSum = settledRows.reduce((sum, row) => sum + BigInt(row.amount), 0n);
  const lockedSum = lockedEscrows.reduce((sum, row) => sum + BigInt(row.amount), 0n);
  return BigInt(tokenValue) - settledSum - lockedSum;
}

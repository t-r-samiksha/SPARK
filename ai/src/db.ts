import { PrismaClient } from '@prisma/client';

/**
 * The intelligence service reads the same Postgres the bank server writes to. It is strictly a
 * READER: every model here derives from settled history that the settlement engine has already
 * persisted. Nothing in this service writes to the bank's tables — a scoring model must never be
 * able to corrupt the ledger it scores.
 */
export const prisma = new PrismaClient();

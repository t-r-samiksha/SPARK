-- AlterTable
-- Added nullable, backfilled, then made NOT NULL — not just a plain `ADD COLUMN ... NOT NULL`,
-- because this table already exists in production and this migration must not assume it's empty.
-- Backfill value is the existing `timestamp` column: for any pre-existing row, that's the closest
-- available approximation of "when this edge was last settled" under the old (pre-Phase-8) shape,
-- which only tracked a single timestamp.
ALTER TABLE "trust_attestations" ADD COLUMN     "last_settled_at" TIMESTAMP(3);
UPDATE "trust_attestations" SET "last_settled_at" = "timestamp" WHERE "last_settled_at" IS NULL;
ALTER TABLE "trust_attestations" ALTER COLUMN "last_settled_at" SET NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "trust_attestations_subject_a_subject_b_key" ON "trust_attestations"("subject_a", "subject_b");

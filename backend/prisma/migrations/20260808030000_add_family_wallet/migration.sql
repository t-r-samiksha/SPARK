-- CreateTable
CREATE TABLE "family_allocations" (
    "id" UUID NOT NULL,
    "parent_account_id" UUID NOT NULL,
    "child_device_id" UUID NOT NULL,
    "token_id" UUID NOT NULL,
    "allocated_amount" TEXT NOT NULL,
    "spent_amount" TEXT NOT NULL DEFAULT '0',
    "cap" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "family_allocations_pkey" PRIMARY KEY ("id")
);


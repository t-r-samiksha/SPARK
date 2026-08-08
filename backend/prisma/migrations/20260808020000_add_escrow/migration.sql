-- AlterTable
ALTER TABLE "transactions" ADD COLUMN     "escrow_contract_id" UUID;

-- CreateTable
CREATE TABLE "escrow_contracts" (
    "id" UUID NOT NULL,
    "buyer_device_id" UUID NOT NULL,
    "seller_device_id" UUID NOT NULL,
    "token_id" UUID NOT NULL,
    "amount" TEXT NOT NULL,
    "condition" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'locked',
    "dispute_reason" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolved_at" TIMESTAMP(3),

    CONSTRAINT "escrow_contracts_pkey" PRIMARY KEY ("id")
);

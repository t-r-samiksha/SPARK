-- CreateTable
CREATE TABLE "accounts" (
    "id" UUID NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "real_balance" BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "devices" (
    "device_id" UUID NOT NULL,
    "account_id" UUID NOT NULL,
    "device_public_key" TEXT NOT NULL,
    "serial_number" TEXT NOT NULL,
    "not_before" TIMESTAMP(3) NOT NULL,
    "not_after" TIMESTAMP(3) NOT NULL,
    "signature" TEXT NOT NULL,
    "revoked_at" TIMESTAMP(3),
    "revoked_reason" TEXT,
    "last_sync_updates_at" TIMESTAMP(3),

    CONSTRAINT "devices_pkey" PRIMARY KEY ("device_id")
);

-- CreateTable
CREATE TABLE "purse_tokens" (
    "token_id" UUID NOT NULL,
    "device_id" UUID NOT NULL,
    "value" TEXT NOT NULL,
    "cap" TEXT NOT NULL,
    "counter_start" INTEGER NOT NULL,
    "expiry" INTEGER NOT NULL,
    "signature" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "purse_tokens_pkey" PRIMARY KEY ("token_id")
);

-- CreateTable
CREATE TABLE "transactions" (
    "tx_id" UUID NOT NULL,
    "token_id" UUID NOT NULL,
    "amount" TEXT NOT NULL,
    "payer_device_id" UUID NOT NULL,
    "payer_account_id" UUID NOT NULL,
    "payer_cert" TEXT NOT NULL,
    "payee_device_id" UUID NOT NULL,
    "payee_account_id" UUID NOT NULL,
    "payee_cert" TEXT NOT NULL,
    "device_counter" INTEGER NOT NULL,
    "prev_tx_hash" TEXT,
    "timestamp" INTEGER NOT NULL,
    "signature" TEXT NOT NULL,
    "synced_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "transactions_pkey" PRIMARY KEY ("tx_id")
);

-- CreateTable
CREATE TABLE "revoked_certificates" (
    "cert_serial" TEXT NOT NULL,
    "revoked_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "reason" TEXT NOT NULL,

    CONSTRAINT "revoked_certificates_pkey" PRIMARY KEY ("cert_serial")
);

-- CreateTable
CREATE TABLE "double_spend_incidents" (
    "id" UUID NOT NULL,
    "token_id" UUID NOT NULL,
    "device_id" UUID NOT NULL,
    "tx_id_a" UUID NOT NULL,
    "tx_id_b" UUID NOT NULL,
    "detected_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "double_spend_incidents_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "disaster_events" (
    "id" UUID NOT NULL,
    "region_geo" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "higher_cap" TEXT,
    "essential_only" BOOLEAN NOT NULL DEFAULT false,
    "started_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ended_at" TIMESTAMP(3),

    CONSTRAINT "disaster_events_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "trust_attestations" (
    "id" UUID NOT NULL,
    "subject_a" UUID NOT NULL,
    "subject_b" UUID NOT NULL,
    "settled_amount" TEXT NOT NULL,
    "settlement_count" INTEGER NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "signature" TEXT NOT NULL,

    CONSTRAINT "trust_attestations_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "devices_device_public_key_key" ON "devices"("device_public_key");

-- CreateIndex
CREATE UNIQUE INDEX "devices_serial_number_key" ON "devices"("serial_number");

-- CreateIndex
CREATE UNIQUE INDEX "transactions_token_id_device_counter_key" ON "transactions"("token_id", "device_counter");

-- AddForeignKey
ALTER TABLE "devices" ADD CONSTRAINT "devices_account_id_fkey" FOREIGN KEY ("account_id") REFERENCES "accounts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;


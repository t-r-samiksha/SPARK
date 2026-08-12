# SPARK Tap Transport — Two-Device Airplane Mode Test Guide

This document outlines the step-by-step procedure to execute an end-to-end offline Tap-to-Pay transaction between two Android devices (or emulators) with **Airplane Mode enabled** (zero internet connection).

---

## 1. Prerequisites

1. **Two Devices / Emulators**:
   - **Device A (Payer)**: Android 8.0+ with NFC & Bluetooth enabled.
   - **Device B (Payee)**: Android 8.0+ with Host Card Emulation (HCE) support.
2. **Initial Onboarding**:
   - Both devices must have performed one-time enrollment while online to receive their signed device certificates and cache the Bank Root CA certificate.
   - Device A must have loaded its offline purse token (e.g. ₹1,000 / 100,000 paise).
3. **Set Airplane Mode**:
   - On **both devices**, turn ON **Airplane Mode**.
   - Ensure **NFC is ON** (Airplane mode in Android allows NFC and BLE to remain active).

---

## 2. APDU Handshake Protocol Flow

```
   Payer Device (Reader Mode)                       Payee Device (HostApduService)
  ----------------------------                     --------------------------------
               |                                                   |
   1. SELECT AID (F0535041524B01) -------------------------------> |
               |                                                   | Generates Ephemeral X25519 Key
               |                                                   | & 32-byte Auth Challenge
               | <---------------- Payee Cert PEM, X25519 Pub,     |
               |                   Payee Challenge, SW 9000        |
               |                                                   |
   Validates Payee Cert against Bank CA                            |
   Generates Ephemeral X25519 Key                                  |
   Derives AES-256 Key via ECDH                                    |
   Signs Payee Challenge (StrongBox)                               |
               |                                                   |
   2. INS_EXCHANGE_AUTH (0x10) ----------------------------------> |
      (Payer Cert PEM, X25519 Pub,                                 | Validates Payer Cert
       Signed Payee Challenge, Payer Challenge)                    | Verifies Payer Signature
               |                                                   | Derives matching AES-256 Key
               |                                                   | Signs Payer Challenge
               | <---------------- Payee Challenge Sig, SW 9000 ---|
               |                                                   |
   Verifies Payee Signature                                        |
   Builds & Signs Transaction (Prompt 6)                           |
   Encrypts Tx with AES-256-GCM                                    |
               |                                                   |
   3. INS_TRANSFER_TX (0x20) ------------------------------------> |
      (Encrypted Transaction Payload)                              | Decrypts with AES-256-GCM
               |                                                   | Validates Replay & Counters
               |                                                   | Records "in" to Local Ledger
               | <---------------- TransferResponse (ACCEPTED) ----|
               |                                                   |
   Records "out" to Local Ledger                                   |
   Decrements Purse Balance                                        |
   Emits UI Payment Success                                        | Emits UI Payment Received
```

---

## 3. Manual Tap Execution Steps

### Step 1: Prepare Payee
1. Open SPARK Wallet on Device B.
2. Tap **"Receive Cash"**.
3. Enter amount: **₹250.00** (25,000 paise).
4. Payee screen displays *"Ready to Receive — Hold near payer device"*.
5. `SparkHostApduService` is now active and listening for SPARK AID `F0535041524B01`.

### Step 2: Initiate Payer Tap
1. Open SPARK Wallet on Device A.
2. Tap **"Pay Peer"**.
3. Enter amount: **₹250.00**.
4. Tap **"Tap to Pay (NFC)"**.
5. `NfcReaderModeManager` activates Reader Mode with `FLAG_READER_NFC_A` and `FLAG_READER_SKIP_NDEF_CHECK`.

### Step 3: Physical Contact (Tap)
1. Hold the backs of Device A and Device B together.
2. An audible haptic vibration confirms IsoDep connection.
3. The 4-step handshake completes in **< 150 ms**:
   - Certificate exchange & offline Bank Root CA verification.
   - Challenge-response mutual authentication.
   - Ephemeral X25519 ECDH key agreement.
   - AES-256-GCM encrypted transaction transfer.

### Step 4: Verification
1. **Device A (Payer)**:
   - Screen displays *"Payment Sent: ₹250.00"*.
   - Offline spendable balance decrements from ₹1,000.00 to **₹750.00**.
   - `local_ledger` contains a new `direction = "out"` entry with `counter = 1`.
   - `unsynced_count` increments to `1`.
2. **Device B (Payee)**:
   - Screen displays *"Payment Received: ₹250.00"*.
   - `local_ledger` contains a new `direction = "in"` entry.
   - Payer certificate is cached in `cached_certs`.
   - `unsynced_count` increments to `1`.

---

## 4. Fallback Transports

If NFC is unavailable or disabled:
1. **BLE Fallback**:
   - Device B advertises Service UUID `0000FE53-0000-1000-8000-00805F9B34FB`.
   - Device A scans and connects via BLE GATT to execute the exact same handshake.
2. **QR Fallback**:
   - Device B displays a dynamic ZXing QR containing the invoice, ephemeral public key, and auth challenge.
   - Device A scans the QR with its camera, executes ECDH, signs the transaction, and presents a confirmation QR for Device B to scan.

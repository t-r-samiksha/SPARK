# Manual Test: Two-Device Airplane-Mode Tap Payment

This script verifies that the offline NFC tap payment correctly processes a transaction and transfers mesh relay payloads without internet connectivity.

## Prerequisites
1. Two physical Android devices (Device A and Device B) with NFC support.
2. Both devices have the SPARK Wallet app installed and enrolled (via internet).
3. Device A has at least ₹1000 in its offline spendable balance.
4. Device B is enrolled but does not need an initial balance.

## Preparation
1. **Enable Airplane Mode** on both devices to ensure no internet or cellular connection is available.
2. Ensure **NFC** is turned ON in settings on both devices (NFC usually remains on in modern Android airplane mode).
3. Open the SPARK app on both devices.

## Execution
1. On **Device B (Payee)**:
   - Navigate to the **Receive** screen.
   - The device is now in HCE (Host Card Emulation) mode, waiting for a connection.
2. On **Device A (Payer)**:
   - Navigate to the **Pay / Tap** screen.
   - The device is now in Reader mode.
3. **The Tap**:
   - Hold Device A over the NFC antenna area of Device B.
   - Keep the devices together until haptic feedback is felt.
4. **Validation**:
   - **Device A** should vibrate, show a success "Receipt" screen, and the main balance should decrement by the sent amount.
   - **Device B** should show the received transaction in its "Transaction History" as *Pending Sync*.
   - Verify that the local `tx_id` and signatures match locally (handled automatically by the protocol).

## Cleanup
1. Turn off Airplane mode on Device B.
2. Tap "Sync Ledger" to upload the offline transaction to the backend.

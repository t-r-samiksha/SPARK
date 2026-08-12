# Manual Test: Deliberate Double-Spend Demo

This script demonstrates the backend resolver identifying and flagging a cheating device when a signed purse token and counter state are duplicated across two physical devices, proving that offline double-spends are eventually caught upon sync.

## Preparation
1. You will need three devices: **Device A** (The Attacker, cloned to two physical phones A1 and A2) and **Device B** (The Merchant).
2. Provision Device A1 with an initial offline purse balance of ₹5000.
3. Turn **Airplane Mode ON** for all three devices.

### Simulating the Clone
For the purpose of this demo, we bypass the hardware Keystore restrictions by manually copying the database `spark_wallet_encrypted.db` from Device A1 to Device A2 via `adb pull` and `adb push`.
*(In a real scenario, this simulates a highly sophisticated attacker who extracted the keys and database from a compromised hardware enclave).*

## Execution

### Step 1: First Spend (A1 -> B)
1. On **Device B**, open the **Receive** screen.
2. On **Device A1**, open the **Pay** screen and initiate a payment of ₹4000.
3. **Tap** Device A1 to Device B.
4. Verify success. Device A1's balance is now ₹1000. Device B has a pending transaction of ₹4000.

### Step 2: Second Spend (A2 -> B)
1. On **Device B**, remain on the **Receive** screen (or reopen it).
2. On **Device A2** (which still thinks it has ₹5000), open the **Pay** screen and initiate a payment of ₹3000.
3. **Tap** Device A2 to Device B.
4. Verify success. Device A2's balance is now ₹2000. Device B has a second pending transaction of ₹3000.

*Note: Device B accepted both transactions offline because both came with valid, sequential counter signatures originating from the same valid purse token.*

### Step 3: The Reveal (Sync)
1. Turn **Airplane Mode OFF** on **Device B** to restore connectivity.
2. Open the **Sync Ledger** screen and tap **Sync Now**.
3. **Observation**:
   - The backend resolver will process the batch.
   - It will detect two diverging transaction chains originating from the same purse token and identical counter indices.
   - The backend will accept the first transaction chronologically, and flag the second as a double-spend.
   - The backend adds Device A's `subject_id` to the global CRL (Certificate Revocation List).
4. **Conclusion**:
   - Device B receives a sync update notifying it of the invalid transaction and updating its CRL.
   - The UI on Device B updates to flag the double-spend, demonstrating the eventual consistency and fraud detection of the SPARK protocol.

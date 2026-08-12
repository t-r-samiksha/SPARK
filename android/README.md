# SPARK Offline Wallet

An offline digital cash application that allows secure, peer-to-peer payments via NFC/BLE without requiring an internet connection.

## Build Instructions

1. Ensure you have Android Studio installed.
2. Clone the repository and open the `android` folder in Android Studio.
3. Sync project with Gradle files.
4. Run the app on an emulator or physical device.

### Command Line Build

To build from the command line:

```bash
./gradlew clean assembleDebug
```

## Running Unit Tests

Run the full suite of unit tests to verify local ledger chaining, purse decrement, and cryptographic validation:

```bash
./gradlew testDebugUnitTest
```

## Setup & Onboarding

1. Launch the app on your device.
2. The initial onboarding process requires internet connectivity to enroll the device and retrieve the initial signed purse token.
3. Once the main wallet screen is displayed, the app can function entirely offline.

## Architecture

- **Protocol**: Uses APDU over NFC HCE and Reader Mode, with fallback to BLE.
- **Security**: Hardware-backed KeyStore for monotonic counters and key storage. Encrypted SQLCipher database via Room.
- **Trust**: Trust chains are assembled locally via Breadth-First Search on cached attestations.
- **ML**: On-device advisory anomaly detection via TensorFlow Lite.

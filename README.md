# Velour — Adult (18+) Dating & Social Connection Platform

Velour is a production-grade 18+ dating and social connection mobile application built with **Kotlin, Jetpack Compose (Material 3), Google Identity (`CredentialManager`), and Cloud Firestore** with zero-trust server-enforced security rules.

---

## 1. Core Capabilities & Business Model

- **18+ Verified Adult Onboarding & Authentication**:
  - Mandatory 18+ age and call-consent confirmation gate prior to authentication.
  - Real Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` for interactive sign-in, `GetGoogleIdOption` for returning-user auto-sign-in).
  - Immutable `birthYear` (`<= 2008`) and immutable `gender` (`female` | `male`) enforced at the Firestore security rules layer to prevent post-signup policy abuse.
- **Configurable Female Free-Access Policy & Transparent Male Credit Pricing**:
  - **Eligible Female Accounts**: Core 1-on-1 messaging, audio calls, and video calls are **$0 (0 coins)**, protected by configurable daily fair-use thresholds (`femaleDailyFreeMessagesLimit`).
  - **Male Accounts**: Browse profiles and match for free, receive **25 welcome coins**, and see **explicit, transparent coin pricing and confirmation dialogs** before any paid chat message, audio call, or video call is initiated.
  - **Zero Silent Charges**: No paid message or call ever starts without an upfront confirmation modal displaying exact cost and available coin balance.
- **Discovery & Mutual Matching**:
  - Curated discovery cards with verified 18+ badges, online status indicators, shared interests, search, and age/distance/verified/online filters.
  - Like, Super-Like, and Pass actions with real-time mutual match detection and celebration modal.
- **Real-Time 1-on-1 Chat**:
  - Real-time message delivery, read receipts, typing indicators, quick emoji bar, and zero-permission Android Photo Picker (`PickVisualMedia`) image sharing.
  - Built-in Unmatch, Block, and Safety Report actions inside every conversation.
- **1-on-1 Audio & Video Calling**:
  - Real-time Firestore call session signaling (`ringing` -> `connected` -> `ended` / `rejected` / `exhausted`) with incoming call screen (Accept / Decline).
  - Runtime `RECORD_AUDIO` and `CAMERA` permission requests prior to call media controls.
  - Live duration timer, mute, speakerphone, camera switch, per-minute server-validated coin deduction, low-balance warning banner, and graceful automatic termination when credits reach zero.
  - Honest signaling mode disclosure (`Live Firestore WebRTC Signaling Mode`) — never fakes simulated peer video/audio streams.
- **Idempotent Coin Wallet & Receipts**:
  - Server-enforced atomic Firestore transactions with `${uid}--order_${idempotencyKey}` ledger keys in `/wallet_transactions` to prevent duplicate credit grants or double deductions.
  - Full transaction history receipts, payment verification success/failure modals, and Google Play digital billing & refund support disclosures.
- **Role-Protected Admin Console**:
  - Restricted via Firestore rules (`email_verified == true` + bootstrapped admin email or `/admins/{uid}`) to configure live coin rates, toggle Female Free-Access flags, verify profiles, and resolve user safety reports.

---

## 2. Project Architecture & Folder Structure

```text
Velour/
├── app/
│   ├── google-services.json                         # Provisioned Firebase Android config
│   ├── build.gradle.kts                             # Gradle Kotlin DSL, Compose, Firebase BOM
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                  # Permissions (Audio, Camera, Coarse Location, Notifications)
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt                  # Edge-to-edge root Auth Gate, Adaptive Nav, DataStore theme
│       │   │   ├── auth/VelourAuthManager.kt        # CredentialManager Google Sign-In & Sign-Out
│       │   │   ├── data/
│       │   │   │   ├── FirestoreErrorHandler.kt     # Structured JSON error diagnostics
│       │   │   │   ├── model/VelourModels.kt        # @IgnoreExtraProperties Firestore models & PricingConfig
│       │   │   │   └── repository/VelourRepository.kt # Atomic transactions, chat, call metering, moderation
│       │   │   └── ui/
│       │   │       ├── VelourViewModel.kt           # Reactive StateFlows, call metering loop, UI actions
│       │   │       ├── components/VelourCommonComponents.kt
│       │   │       ├── screens/
│       │   │       │   ├── AuthAndSetupScreens.kt
│       │   │       │   ├── DiscoverAndMatchesScreens.kt
│       │   │       │   ├── ChatAndCallScreens.kt
│       │   │       │   └── WalletSettingsAdminScreens.kt
│       │   │       └── theme/ (Color.kt, Theme.kt, Type.kt)
│       │   └── res/
│       │       ├── drawable/                        # Custom editorial profile art & launcher symbol
│       │       ├── font/                            # Playfair Display & Plus Jakarta Sans (.ttf)
│       │       └── values/
│       │           ├── firebase_applet_config.xml   # Custom named Firestore Database ID
│       │           └── strings.xml
│       └── test/java/com/example/
│           ├── base/FirestoreEmulatorTestBase.kt    # Robolectric + Firebase Emulator harness
│           └── data/repository/VelourRepositoryRuleTest.kt
├── firebase.json                                    # Local Firestore (8085) & Auth (9099) emulator binding
├── firebase-blueprint.json                          # Strict JSON Schema IR for all 13 collections
├── firestore.rules                                  # Production zero-trust Firestore Security Rules
├── firestore.test.js                                # Tier 1 Node.js security rules adversarial test suite
└── security_spec.md                                 # Data invariants & Dirty Dozen threat audit
```

---

## 3. Environment & Firebase Setup Checklist

1. **Application ID**: `com.aistudio.velour.mrxkqv` (configured in `app/build.gradle.kts`).
2. **Firebase Configuration**:
   - `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` are pre-provisioned.
   - All `FirebaseFirestore.getInstance(databaseId)` calls explicitly pass `R.string.firestore_database_id` so reads and writes target the provisioned named Firestore database.
   - If you export the project and use your own Firebase project, register your signing key's **SHA-1** and **SHA-256** fingerprints in **Firebase Console -> Project Settings -> Your Apps -> Android App**, enable **Google Sign-In** in **Firebase Authentication**, and download the updated `google-services.json` into `app/google-services.json`.
3. **API Keys / Secrets (`.env`)**:
   - Never hardcode secrets in Kotlin files or `local.properties`.
   - Manage optional external keys via `.env` (using `.env.example` as template) or the **Secrets panel in Google AI Studio**.

---

## 4. Building & Generating an Android APK

### Option A: One-Click APK Export in Google AI Studio (Fastest)
1. Open the **Settings (gear icon)** menu in the top-right corner of Google AI Studio Build.
2. Select **Export / Generate APK** (or **Download ZIP** to build locally).
3. Download the signed `.apk` directly to your computer or Android phone.

### Option B: Local Build on Windows PowerShell

#### Prerequisites on Windows 10/11
- **JDK 17 or JDK 21** installed (`winget install Microsoft.OpenJDK.17`).
- **Android Studio Ladybug / Meerkat** (or Android SDK Command-Line Tools with **Android SDK Platform 36** and **Build-Tools** installed).
- **Gradle 8.10+** installed (`winget install Gradle.Gradle`), or generate the wrapper inside Android Studio (`gradle wrapper`).

#### Step-by-Step Windows PowerShell Commands

```powershell
# 1. Unzip the exported project and navigate into the root folder
cd C:\Projects\Velour

# 2. Verify Java and Android SDK environment variables
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools"
java -version
adb version

# 3. Generate a local debug.keystore if not already present
if (!(Test-Path ".\debug.keystore")) {
    keytool -genkeypair -v `
      -keystore .\debug.keystore `
      -storepass android `
      -alias androiddebugkey `
      -keypass android `
      -keyalg RSA -keysize 2048 -validity 10000 `
      -dname "CN=Android Debug,O=Android,C=US"
}

# 4. Print your SHA-1 & SHA-256 fingerprints (ensure they are registered in Firebase Console for Google Sign-In)
keytool -list -v -keystore .\debug.keystore -alias androiddebugkey -storepass android

# 5. Assemble the installable Debug APK
gradle :app:assembleDebug

# Verify the generated APK path:
Get-Item .\app\build\outputs\apk\debug\app-debug.apk
```

#### Building a Signed Release APK / Play Store AAB on Windows PowerShell

```powershell
# 1. Create your production upload keystore (keep outside version control!)
keytool -genkeypair -v `
  -keystore C:\secure\velour-upload-key.jks `
  -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Set signing environment variables expected by app/build.gradle.kts
$env:KEYSTORE_PATH = "C:\secure\velour-upload-key.jks"
$env:STORE_PASSWORD = "YOUR_KEYSTORE_PASSWORD"
$env:KEY_PASSWORD = "YOUR_KEY_PASSWORD"

# 3. Build Release APK or Android App Bundle (.aab)
gradle :app:assembleRelease
gradle :app:bundleRelease
```

---

## 5. Installing & Testing on a Physical Android Phone

### Method 1: Install via USB Debugging (`adb`) from Windows PowerShell
1. On your Android phone, open **Settings -> About phone** and tap **Build number** 7 times to unlock Developer Options.
2. Go to **Settings -> System -> Developer options** and enable **USB debugging**.
3. Connect your phone via USB cable and tap **Allow** on the RSA debugging prompt.
4. Run in Windows PowerShell:

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

### Method 2: Direct APK Transfer (No Cable Required)
1. Copy `app-debug.apk` (or the APK downloaded from Google AI Studio) to Google Drive or transfer it to your phone.
2. Tap the `.apk` file in the **Files** app on Android.
3. If prompted, allow **Install unknown apps** for your file manager, then tap **Install** and **Open**.

### Recommended Two-User Live Verification Flow
1. **Account A (Female Free-Access Test)**:
   - Sign in with Google Account A, confirm 18+, select **Woman (Female Free-Access)** during profile setup.
   - Verify the banner shows `Female Free-Access Policy Active` (`$0` chat & calling).
2. **Account B (Male Credit Metering Test)**:
   - Sign in on a second device (or emulator) with Google Account B, confirm 18+, select **Man**, and verify **25 Welcome Coins** in the top coin pill.
3. **Mutual Match, Chat & Live Call**:
   - Like each other on **Discover** to trigger the **It's a Match!** celebration.
   - Send a free message from Account A (0 coins deducted), then send a paid message from Account B (confirming the upfront **5-coin** prompt and watching the wallet update to **20 coins**).
   - Start an Audio or Video call from Account B, accept on Account A, and verify live call controls, duration timer, and per-minute credit metering.

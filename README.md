# 📺 TVGrip

<div align="center">

### 🎮 Professional Low-Latency Android TV & Google TV Controller

Turn your Android phone into an all-in-one Android TV / Google TV remote, keyboard, navigation touchpad, and game controller.

Built natively for **Android TV and Google TV** using the Android TV Remote Service v2 protocol.

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Download APK](https://img.shields.io/badge/Download-TVGrip.apk-brightgreen?style=for-the-badge&logo=android)](https://github.com/mbir31/TVgrip/releases/latest)
[![CI/CD Release](https://img.shields.io/badge/CI%2FCD-Auto%20Release-orange?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/mbir31/TVgrip/actions)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](#-license)

<br/>

**📱 One phone. 🎮 Multi-controller power. 📺 Complete big-screen control.**

</div>

---

### 📥 Download Official Release APK

Directly installable APK available under GitHub Releases:

👉 **[Download TVGrip.apk (Latest Release)](https://github.com/mbir31/TVgrip/releases/latest)**

*Every push to a branch runs GitHub Actions: it builds the debug APK, builds the R8-minified release APK, runs unit tests, runs lint, and uploads both APKs as workflow artifacts. A signed public release must be published by the maintainer with a production upload key.*

---

## 🌟 CORE FEATURES & ARCHITECTURE

- 🎛️ **Full Smart TV Remote**: Tactile D-pad, OK, Home, Back, Menu/Settings, Power, Volume, channel, and media controls using the real Android TV Remote v2 key-inject protocol.
- 🔐 **Mutual TLS Pairing (mTLS)**: Real SHA-256 certificate handshake with the 6-character hexadecimal PIN shown on the TV through the Polo pairing protocol.
- 📶 **Wi-Fi Discovery**: NSD/mDNS discovery of the `_androidtvremote2._tcp` service that all Android TV / Google TV devices advertise.
- 🖱️ **Navigation Touchpad & Air Mouse**: Gyro/touch navigation drives real Android TV D-pad navigation, center-select, and page up/down actions. (The Remote v2 protocol has no absolute pointer stream; an absolute cursor would be fake.)
- 🎮 **Game Controller**: D-pad, ABXY, shoulder buttons, triggers, Start/Select/Back/Home and stick-to-d-pad navigation are sent as genuine Android TV key events. Analog streams are not exposed by the protocol, so sticks/triggers are mapped to directional/button key events instead of fake packets.
- 🎙️ **Voice-assisted Typing**: On-device speech recognition fills the keyboard text field, which is then injected into the TV using IME batch-edit text injection.
- ⌨️ **Native Phone Keyboard**: Type URLs, Wi-Fi passwords, and login credentials from your phone keyboard in seconds and send them as real IME text.

---

## 🏗️ SYSTEM ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────┐
│                    TVGrip UI Layer                          │
│  Compose Material 3 • Tactile 3D Buttons • Pure Black Canvas│
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    ViewModel & StateFlow                    │
│      Lifecycle-Aware State • Player Slot Manager            │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    TvConnectionManager                      │
│      Auto-Reconnect • Latency Diagnostics • Failover        │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│             Android TV Remote v2 (Polo Protocol)            │
│  • Port 6467: Mutual TLS Pairing & Challenge Auth           │
│  • Port 6466: Protobuf Command Stream & Remote Sessions     │
│  • Android Keystore RSA-2048 Client Identity + Cert Pinning  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │       Television Target     │
                │ Google TV / Android TV      │
                └─────────────────────────────┘
```

---

## 🔌 WI-FI PAIRING GUIDE

1. Ensure your Android phone and TV are connected to the same Wi-Fi network.
2. Open TVGrip and tap **Scan for TVs** (or enter your TV's IP address directly).
3. Select your TV from the discovered device list.
4. Enter the **6-character PIN** displayed on your TV screen.
5. TVGrip performs mutual TLS cryptographic authentication and stores the paired TV identity. The app reconnects to that TV when the same network is available and auto-reconnect is enabled.

---

## 🛠️ TECH STACK

- **Language:** 100% Kotlin
- **UI:** Jetpack Compose (Material 3 with custom 3D tactile theme)
- **Cryptography:** Android Keystore + Bouncy Castle (`bcpkix-jdk18on`, `bcprov-jdk18on`)
- **Persistence:** AndroidX Room Database & DataStore
- **Networking:** Mutual TLS (SSLSocket with certificate pinning), NSD (Network Service Discovery), Protobuf Wire Serialization
- **Sensors:** Gyroscope/rotation-vector sampling with dead-zone, smoothing, and sensitivity settings (battery-saver mode reduces the sample rate)

---

## 🛠️ RELEASE SIGNING

CI builds the release APK with a throwaway keystore so the minified build is
verified on every push. A production release must be signed by the maintainer:

```bash
export KEYSTORE_PATH=/absolute/path/to/upload-key.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
gradle :app:bundleRelease
```

Store the real upload keystore and its passwords as GitHub Actions secrets; never
commit a production keystore or password to the repository.

---

## 📄 LICENSE

TVGrip is distributed under the MIT License.

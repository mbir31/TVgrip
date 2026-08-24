# 📺 TVGrip

<div align="center">

### 🎮 Professional Low-Latency Android TV & Google TV Controller

Turn your Android phone into an all-in-one smart TV remote, air mouse, motion steering wheel, keyboard, voice controller & 4-player gamepad.

Built natively for **Android TV, Google TV, Samsung Tizen, LG webOS & Fire TV**.

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

*Every push to `main` automatically compiles, packages, and publishes `TVGrip.apk` via GitHub Actions.*

---

## 🌟 CORE FEATURES & ARCHITECTURE

- 🎛️ **Full Smart TV Remote**: Tactile D-pad, OK, Home, Back, Source Switch, Volume & Media controls.
- 🔐 **Mutual TLS Pairing (mTLS)**: Real SHA-256 certificate handshake with 6-character PIN exchange on TV screen using Google TV Polo Protocol (v2).
- 📶 **High-Speed Wi-Fi Discovery**: Zero-configuration discovery using mDNS (`_androidtvremote2._tcp`, `_googlecast._tcp`) and subnet sweeps.
- 🖱️ **Air Mouse & Trackpad**: Point and move your phone to control an on-screen mouse pointer with gyro motion smoothing.
- 🏎️ **Motion Racing Wheel**: Tilt your phone horizontally to steer in Android TV racing games with progressive throttle/brake.
- 🎮 **4-Player Gamepad Zone**: Connect up to 4 phones simultaneously as independent gamepads (ABXY, bumpers, triggers, dual analog sticks).
- 🎙️ **Voice Search & Speech**: Speak into your phone to search for movies and YouTube videos on your TV.
- ⌨️ **Native Phone Keyboard**: Type URLs, Wi-Fi passwords, and login credentials from your phone keyboard in seconds.

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
│      Lifecycle-Aware State • Multi-Player Lobby Manager     │
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
│  • Bouncy Castle X.509 2048-bit RSA Client Authentication    │
└──────────────────────────────┬──────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │       Television Target     │
                │ Google TV / Android TV / LG │
                └─────────────────────────────┘
```

---

## 🔌 WI-FI PAIRING GUIDE

1. Ensure your Android phone and TV are connected to the same Wi-Fi network.
2. Open TVGrip and tap **Scan for TVs** (or enter your TV's IP address directly).
3. Select your TV from the discovered device list.
4. Enter the **6-character PIN** displayed on your TV screen.
5. TVGrip performs mutual TLS cryptographic authentication and establishes a permanent connection.

---

## 🛠️ TECH STACK

- **Language:** 100% Kotlin
- **UI:** Jetpack Compose (Material 3 with custom 3D tactile theme)
- **Cryptography:** Bouncy Castle (`bcpkix-jdk18on`, `bcprov-jdk18on`)
- **Persistence:** AndroidX Room Database & SharedPreferences
- **Networking:** Mutual TLS (SSLSocket), NSD (Network Service Discovery), Protobuf Wire Serialization
- **Sensors:** Accelerometer & Gyroscope sensor fusion with complementary filtering

---

## 📄 LICENSE

TVGrip is distributed under the MIT License.

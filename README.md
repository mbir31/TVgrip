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
- 🔐 **Mutual TLS Pairing (mTLS)**: Real SHA-256 certificate handshake with 6-character PIN exchange on TV screen.
- 📶 **Bluetooth HID Remote Mode**: Connects directly via Bluetooth Human Interface Device profile as a hardware TV remote without Wi-Fi.
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
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
┌──────────────▼──────────────┐ ┌──────────────▼──────────────┐
│   Android TV Remote v2      │ │     Bluetooth HID Engine    │
│  TLS Socket (Port 6466/6467)│ │   Consumer Control & Mouse  │
│  Protobuf Key Inject Frames │ │   Direct Hardware HID Mode  │
└──────────────┬──────────────┘ └──────────────┬──────────────┘
               │                               │
               └───────────────┬───────────────┘
                               │
                ┌──────────────▼──────────────┐
                │       Television Target     │
                │ Google TV / Android TV / LG │
                └─────────────────────────────┘
```

---

## 🔌 PROTOCOL MODES & PAIRING GUIDE

### Option 1: Wi-Fi / Local Network (Android TV Remote v2)
1. Ensure your Android phone and TV are connected to the same Wi-Fi network.
2. Tap **PAIR A TV** in TVGrip to start mDNS discovery (`_androidtvremote2._tcp`).
3. Select your TV from the list.
4. Enter the **6-character PIN** that appears on your TV screen.
5. TVGrip performs mutual TLS certificate exchange and verifies the connection.

### Option 2: Bluetooth HID Remote (Universal)
1. Open TVGrip and switch to **Bluetooth HID Mode**.
2. Put your TV into Bluetooth pairing mode (`Settings` → `Remotes & Accessories` → `Add Accessory`).
3. Select **TVGrip Remote** from your TV's Bluetooth device list.
4. Enjoy direct hardware-level control without requiring a shared Wi-Fi network.

---

## 🔧 BUILD FROM SOURCE

```bash
# 1. Clone repository
git clone https://github.com/mbir31/TVgrip.git
cd TVgrip

# 2. Compile APK
gradle :app:assembleDebug

# 3. Run unit tests
gradle :app:testDebugUnitTest
```

---

<div align="center">

Made with love by ©munabbiRMushran🇧🇩

**© mbir31 • TVGrip**

</div>

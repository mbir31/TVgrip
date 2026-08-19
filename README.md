# 📺 TVGrip

<div align="center">

### 🎮 Your TV. Your Phone. One Powerful Controller.

Turn your Android phone into an all-in-one smart TV remote, air mouse, motion steering wheel, keyboard, voice controller & 4-player gamepad.

Built natively for **Android TV, Google TV, Samsung Tizen, LG webOS & Fire TV**.

<br/>

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/mbir31/TVgrip/releases/latest)
[![CI/CD Build](https://img.shields.io/badge/CI%2FCD-Auto%20Release-orange?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/mbir31/TVgrip/actions)
[![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen?style=for-the-badge)](#-open-source)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](#-license)

<br/>

**📱 One phone. 🎮 Multiple controllers. 📺 Total big-screen control.**

</div>

---

### 📥 Direct APK Download

Get the latest installable release APK directly from GitHub Releases:

👉 **[Download TVGrip.apk (Latest)](https://github.com/mbir31/TVgrip/releases/latest)**

*Every `git push` automatically compiles, signs, and publishes a fresh `TVGrip.apk` ready to install directly on your phone.*

---

## 🌟 WHY TVGRIP?

**One app. Zero missing remotes.**

- 🎛️ **Full Smart TV Remote**: Responsive tactile D-pad, OK, Home, Back, Source Switch, Volume & Media controls.
- 🔐 **Mutual TLS Pairing (mTLS)**: Secure 6-character PIN exchange pops up directly on your Android TV / Google TV screen.
- 📶 **Bluetooth HID Remote Mode**: Connects directly via Bluetooth Human Interface Device profile as a hardware TV remote without needing Wi-Fi.
- 🖱️ **Air Mouse & Trackpad**: Point and move your phone to control an on-screen mouse pointer with gyro motion smoothing.
- 🏎️ **Motion Racing Wheel**: Tilt your phone horizontally to steer in Android TV racing games with progressive throttle/brake.
- 🎮 **4-Player Gamepad Zone**: Connect up to 4 phones simultaneously as independent gamepads (ABXY, bumpers, triggers, dual analog sticks).
- 🎙️ **Voice Search & Speech**: Speak into your phone to search for movies and YouTube videos on your TV.
- ⌨️ **Native Phone Keyboard**: Type URLs, Wi-Fi passwords, and login credentials from your phone keyboard in seconds.

---

## 🔌 CONNECTION MODES

TVGrip provides two robust connection pathways:

### Option 1: Wi-Fi / Local Network (Android TV Remote v2)
- Fast auto-discovery via **mDNS / DNS-SD** (`_androidtvremote2._tcp`).
- Secure **Mutual TLS (mTLS)** encryption with on-device generated client X.509 certificates.
- Prompts your TV to display a **6-digit pairing code** for seamless one-time setup.

### Option 2: Bluetooth HID Remote (Universal)
- Uses Android's official **Bluetooth HID Device Profile**.
- Operates identically to physical Bluetooth TV remotes.
- Compatible with Android TV, Google TV, Samsung Tizen, LG webOS, Apple TV, and Fire TV.

---

## 🎮 THE 6 CONTROLLER MODES

### 1. 🎛️ Smart TV Remote
- **Directional Pad**: Up, Down, Left, Right, OK / Center.
- **Media Suite**: Play, Pause, Rewind, Fast-Forward, Next/Previous, Mute, Volume +/-.
- **System Actions**: Home, Back, Input/Source, Settings, Power.

### 2. 🖱️ Air Mouse & Pointer
- Point-and-aim cursor navigation powered by hardware gyroscope & accelerometer.
- Tap to click, double-tap, right-click, and smooth scrolling.

### 3. 🏎️ Motion Steering Wheel
- 1:1 responsive horizontal tilt steering.
- On-screen progressive accelerator pedal, brake, and tactile handbrake.

### 4. 🎮 4-Player Multiplayer Gamepad
- Supports 4 simultaneous player slots: 🟢 P1, 🔵 P2, 🟡 P3, 🔴 P4.
- Full layout: ABXY action cluster, D-pad, dual thumbsticks, L1/R1 bumpers, L2/R2 triggers.

### 5. 🎙️ Voice Search
- Real-time speech-to-text dictation sent directly to TV search bars.

### 6. ⌨️ Phone Keyboard
- Painlessly type long passwords and search terms directly into TV text fields.

---

## 🚀 AUTOMATED CI/CD RELEASES

TVGrip uses GitHub Actions (`.github/workflows/release.yml`) for seamless deployment:

- Automatically builds using **Java 17 (Temurin)** and **Gradle 9.3.1**.
- Signs the APK with the debug keystore.
- Publishes **`TVGrip.apk`** to the **GitHub Releases** page on every push to `main` or tag push (`v*`).

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

**Made with love by ©munabbiRMushran ❤️**

**© mbir31 • TVGrip**

### 📺 Control smarter. Play together. Enjoy more.

</div>

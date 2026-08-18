# TVGrip — Android TV Smart Controller & Multiplayer Gamepad

**TVGrip** is a modern Android companion app built with **Kotlin** and **Jetpack Compose** that transforms any Android smartphone into an ultra-low-latency, tactile remote control, gyro air mouse, racing motion steering wheel, and 4-player arcade gamepad for Android TV and Google TV devices.

---

## Key Features

- **Tactile D-Pad & Navigation Remote**: Deep directional controls, volume rockers, playback shuttle, home/back navigation with crisp haptic feedback.
- **Air Mouse (Gyro Pointer)**: Uses device gyroscope and accelerometer to control pointer movements smoothly on compatible smart TVs.
- **Motion Tilt Steering (Racing Wheel)**: Real-time steering wheel physics with pedals and gear shifts for split-screen racing games.
- **4-Player Multiplayer Lobby (P1–P4)**: Connect multiple phones as distinct controller ports with arcade color theming and rumble test capabilities.
- **Automated Network Discovery**: Dual-layer detection combining DNS-SD/mDNS (`NsdManager`) and rapid local subnet IP sweeps.
- **Multi-TV Management**: Pair, name, favorite, and switch between multiple TVs in your home seamlessly.
- **Real-Time Network Diagnostics**: Built-in ping monitor, packet delivery audit, and capability profiling.

---

## Tech Stack & Architecture

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: Clean MVVM (Model-View-ViewModel) + Kotlin Coroutines & Flow
- **Local Persistence**: Room Database
- **Sensors**: Android Sensor Framework (Gyroscope, Accelerometer, Rotation Vector)
- **Protocols**: Android TV Remote v2 (TLS/SSL) & TVGrip Companion Fast Socket Protocol

---

## Build & Installation

```bash
# Clone the repository
git clone https://github.com/your-username/tvgrip.git
cd tvgrip

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

---

## License

MIT License. Open source for all Android TV and Google TV enthusiasts.

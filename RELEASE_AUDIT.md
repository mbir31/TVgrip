# TVGrip Release-Readiness Audit

Audit date: 2026-09-04
Branch: `arena/01a06b52-tvgrip`
Head at time of audit: `bda5f12` (later release hardening commits were layered on top of this work)

## 1. Verified by code / build / unit tests

- **Build**: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease` (R8 minify) all compile in GitHub Actions.
- **Unit tests**: `:app:testDebugUnitTest` passes.
- **Lint**: `:app:lintDebug` passes (only Node.js/setup-java deprecation notices remain on the workflow).
- **Android TV Remote v2 decoder**: field tags verified against the tronikos/androidtvremote2 reference and covered by wire-level unit tests (remote_configure, remote_start, ping request/response, IME batch counters, malformed frames).
- **Polo pairing wire format**: Options (`input_encodings=1`, `output_encodings=2`, `preferred_role=3`) and Configuration (`encoding=1`, `client_role=2`) covered by unit tests; pairing secret digest is built from client_modulus + "0"+client_exponent + server_modulus + "0"+server_exponent + code[2:].
- **Connection manager**: commands are serialized through one queue so rapid press/release order is preserved; a generation counter drops stale commands after connect/disconnect; reconnects use bounded exponential backoff; a connection is reported CONNECTED only after the protocol reports an authenticated remote_start.
- **TLS/pinning**: client identity is an Android Keystore RSA-2048 cert; the remote control channel pins the stored server certificate SHA-256; pairing accepts the TV self-signed cert but records its fingerprint for later pinning.
- **Secrets at rest**: per-TV server certificate fingerprints are now encrypted with an Android Keystore AES-GCM key before being stored in Room; legacy/fallback plaintext values remain readable.
- **Port handling**: control always uses 6466; pairing probe uses 6467; the stored device port is never allowed to redirect the control socket.
- **Input correctness**: D-pad no longer emits a short click after a press/release pair; touchpad drag no longer also triggers select; held gamepad/remote keys are released before the socket closes; joystick/trigger drag math is density-independent.
- **Lifecycle hardening**: air-mouse, steering, and voice input stop when their screens leave the foreground; ViewModels also stop them on clear.
- **Battery saver**: now actually drops sensor sample rate to `SENSOR_DELAY_UI`.
- **Haptics toggle**: now wires the settings value into the actual vibrator helper.
- **Instrumented smoke tests**: `AppHomeSmokeTest.kt` is written and compiled in CI via `assembleDebugAndroidTest`; they are not executed here because no emulator/device is available in this environment.

## 2. Verified on a physical TV

Not performed. This environment has no Android TV / Google TV hardware, so the following remain unverified on real hardware:

- Does the on-screen TV display the pairing PIN, and does the full Polo pairing handshake succeed?
- Power / D-pad / OK / Back / Home / Volume / Mute / media / Menu reactions on the TV.
- IME text injection into real TV text fields (including Unicode).
- Air-mouse/touchpad behavior as key-simulation.
- Game controller button/d-pad key injection in real games/apps.
- Reconnect after Wi-Fi/TV/app restart and after a TV reset or re-pair.
- Multi-TV switching with per-TV fingerprints.

## 3. Not physically verifiable in the current environment

- Real-world Wi-Fi/mDNS discovery across routers, including router client isolation rules.
- Real-world TLS round-trip latency and voice-recognition behavior on-device.
- Android Keystore behavior on a physical device (the code has a JVM/Robolectric fallback).
- Accessibility behavior through TalkBack (roles/content descriptions are set in code but not device-verified).
- Release APK behavior after production signing (CI currently signs release with a throwaway key).

## 4. Known remaining limitations

- Android TV Remote v2 has no absolute pointer or analog controller stream; touchpad/air-mouse and analog sticks/triggers are implemented as key simulation.
- Player slots are local layout presets, not real multi-device multiplayer.
- Voice input is on-device speech recognition; it is injected as text via IME, not a TV-side voice AI.
- Instrumented/UI tests are compiled but not executed in CI (require an emulator/device runner).

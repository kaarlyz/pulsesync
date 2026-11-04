# PulseSync ⌚⚡

> High-throughput BLE telemetry bridge and real-time 6-axis IMU orientation stream between **Xiaomi Smart Band 10** and an **Arch Linux Workstation**. Built with **Kotlin**, **Jetpack Compose**, and Python asyncio/UDP relays.

---

## ⚡ Overview & Architecture

PulseSync establishes a bi-directional data bridge connecting modern wearable hardware (Xiaomi Smart Band 10 / MiWear) directly into a Linux development environment. It bypasses proprietary cloud ecosystems by extracting real-time biometric and motion sensor feeds locally via Bluetooth Low Energy (BLE GATT).

```text
  ┌─────────────────────────┐
  │  Xiaomi Smart Band 10   │
  │  - Photoplethysmography │
  │  - 6-Axis IMU (Acc/Gyro)│
  └────────────┬────────────┘
               │  BLE GATT
               ▼
  ┌─────────────────────────┐
  │    PulseSync Android    │  Jetpack Compose MVI Engine
  │  - Hardware Canvas ECG  │  120 FPS Pulse Curve
  │  - BLE Notification Svc │  Reactive StateFlow
  └────────────┬────────────┘
               │  Local WiFi (TCP / UDP Binary Stream)
               ▼
  ┌─────────────────────────┐
  │ Linux Telemetry Station │  Mahony AHRS Filter
  │  - WebGL 3D Orientation │  Pitch / Roll / Yaw Output
  │  - Terminal Live TUI    │  Sub-10ms Latency
  └─────────────────────────┘
```

---

## ✨ Key Features

### 1. 📱 Native Android Engine (`/app`)
- **Jetpack Compose 2025 Architecture:** Pure unidirectional data flow (MVI) powered by Kotlin Coroutines & `StateFlow`.
- **Hardware-Accelerated Pulse Wave:** 120 FPS custom `Canvas` oscilloscope rendering real-time cardiovascular waveforms.
- **Resilient BLE GATT Client:** Auto-reconnect lifecycle, MTU negotiation (512 bytes), and custom UUID characteristic watchers.
- **Tanka Slate Aesthetic:** Designed with deep contrast dark-slate surfaces (`#0C0F0E`, `#141A17`), electric cyan accents (`#38BDF8`), and telemetry emerald (`#10B981`).

### 2. 🛰️ Linux Workstation Telemetry & AHRS (`/scripts`)
- **6-Axis Mahony AHRS Filter:** Converts raw accelerometer ($g$) and gyroscope ($rad/s$) vectors into quaternion pitch, roll, and yaw angles.
- **Interactive 3D Visualizer Server (`gyro_visualizer_server.py`):** Lightweight local HTTP & TCP socket server providing real-time 3D wrist orientation rendering in the browser.
- **Live Terminal TUI (`telemetry_tui.py`):** ANSI terminal monitoring dashboard for headless Linux latency analysis.

### 3. ⌚ Custom Watchface Toolkit (`/custom_package`)
- Reverse-engineered descriptor manifests (`description.xml`, `capability.json`, `uidmap.map`) and custom watchface asset packaging for the MiWear runtime.

---

## 🚀 Getting Started

### 📱 Android Application Build

#### Prerequisites
- Android SDK 34+ / JDK 17+
- Android Studio or CLI SDK tools

```bash
# Clone the repository
git clone https://github.com/kaarlyz/pulsesync.git
cd pulsesync

# Compile debug APK
./gradlew assembleDebug

# Install to connected device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

### 🖥️ Linux Workstation Visualizer

Start the telemetry listener and 3D visualizer server on your Linux desktop:

```bash
# Run gyro visualizer server (HTTP :8089, TCP :8080)
python3 scripts/gyro_visualizer_server.py
```

Then open `http://localhost:8089` in your browser to view the real-time 3D wrist attitude orientation.

To run the lightweight terminal TUI monitor:
```bash
python3 scripts/telemetry_tui.py
```

---

## 📁 Repository Structure

```text
pulsesync/
├── app/                      # Native Android Kotlin app
│   ├── src/main/java/com/eka/pulsesync/
│   │   ├── ble/              # GATT client & BLE connection manager
│   │   ├── data/             # BandState & reactive models
│   │   ├── ui/               # Jetpack Compose dashboard & Canvas ECG curve
│   │   └── util/             # Notification & telemetry helpers
├── scripts/                  # Linux telemetry bridges & visualization
│   ├── gyro_visualizer_server.py # 3D WebSocket/HTTP visualizer server
│   ├── gyro_receiver.py      # UDP binary packet listener
│   ├── telemetry_tui.py      # ANSI terminal metrics dashboard
│   └── hook_smartband.js     # Frida instrumentation hooks
├── custom_package/           # Watchface packaging specs & descriptor files
├── ai_solution/              # Sensor bridge & standalone viewer
└── build.gradle.kts          # Top-level Gradle Kotlin DSL
```

---

## 🛡️ Security & Privacy
PulseSync operates strictly within the local area network (LAN) and local Bluetooth perimeter. No telemetry or biometric data leaves your personal hardware.

---

## 📄 License
MIT License. Authored by [kaarlyz](https://github.com/kaarlyz).

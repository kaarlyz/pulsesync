# PulseSync — Xiaomi Smart Band 10 & Linux Workstation Bridge

A native Android application built with **Kotlin** and **Jetpack Compose**, featuring a modern dark-slate Tanka aesthetic (`#0C0F0E`, `#141A17`, Cyan `#38BDF8`, Emerald `#10B981`).

## Tech Stack
- **Framework**: Native Android (Kotlin 2.1, Jetpack Compose 2025 BOM, Material 3)
- **Architecture**: MVI / StateFlow reactive stream
- **Graphics**: Hardware-accelerated `Canvas` pulse curve (120 FPS capable)
- **Protocol**: BLE GATT Client + Local TCP / WebSocket Socket Bridge to Arch Linux Workstation

## How to Open in Android Studio
1. Launch Android Studio:
   ```bash
   android-studio &
   ```
2. Click **Open** and select `/home/vallencia/Workspace/02-projects/pulsesync`.
3. Android Studio will automatically resolve Gradle dependencies, initialize the Android SDK, and show the live Compose Preview.
4. Run on your physical device via USB debugging or on the Android Emulator with KVM hardware acceleration.

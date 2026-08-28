# Balatro Multiplayer Native Bridge — Implementation Plan

## Objective

Create a native Android application that automates the Balatro multiplayer matchmaking server and configuration injection, replacing the need for Termux and manual Node.js server execution.

## Background & Motivation

The current Balatro multiplayer mod requires running a Node.js server (typically in Termux) and manually updating the `Multiplayer.jkr` configuration file with the local IP address of the host. This app will provide a "one-click" experience to host or join LAN games.

## Technical Architecture

### 1. Native Server with Ktor (Backend)
- **Component:** Android Foreground Service.
- **Library:** Ktor (HTTP & WebSockets).
- **Functionality:**
  - Listen on port `8788`.
  - Handle room management and message routing for the mod.
  - Run in the background even if the app is closed, using a persistent notification.

### 2. Automatic IP Resolution
- **APIs:** `ConnectivityManager`, `LinkProperties`.
- **Logic:**
  - Identify active network interfaces (`wlan0`, `ap0`, `swlan0`).
  - Extract the local IPv4 address.
  - Distinguish between **client mode** (connect to others) and **host mode** (use own IP).

### 3. Configuration Injection (Scoped Storage)
- **Permission:** Storage Access Framework (SAF) or `MANAGE_EXTERNAL_STORAGE` (depending on Android version compatibility).
- **Target File:** `Multiplayer.jkr` located in the Balatro data directory (typically `/Android/data/com.playstack.balatro.android/files/save/game/`).
- **Logic:**
  - Use `java.nio.file` or `DocumentFile` to read the configuration.
  - Apply Regex/String replacement to update `["server_url"] = "http://IP:PORT"`.
  - Ensure atomic write to prevent file corruption.

### 4. UI & App Launcher (Jetpack Compose)
- **UI Framework:** Jetpack Compose.
- **Buttons:**
  - **"Host LAN & Play":** Starts Ktor Service → Gets IP → Updates config with local IP → Launches Balatro.
  - **"Client Mode":** Inputs Host IP → Updates config with Host IP → Launches Balatro.
- **App Launch:** Use `PackageManager.getLaunchIntentForPackage("com.playstack.balatro.android")`.

## Proposed Solution & Implementation Steps

### Phase 1: Project Setup
1. Initialize an Android Studio project structure (Gradle, Kotlin, Compose).
2. Configure Ktor dependencies.
3. Define permissions in `AndroidManifest.xml`.

### Phase 2: Implementation of Core Logic
1. **Service:** Create `MultiplayerService` using Ktor.
2. **Utils:** Implement `NetworkUtils` for IP resolution and `ConfigUtils` for JKR file modification.
3. **Storage:** Implement the SAF file picker flow to grant access to the Balatro directory.

### Phase 3: UI Development
1. Design the main screen with Compose.
2. Implement state management for the server status and IP input.

### Phase 4: Integration & Testing
1. Wire buttons to the logic components.
2. Test on a device with Balatro installed.

## Verification & Testing

- Verify Ktor server responds on `http://localhost:8788`.
- Verify `Multiplayer.jkr` is correctly updated after clicking "Host" or "Join".
- Verify Balatro launches correctly after configuration.

---

> **Note on Android 11+ Restrictions:** Accessing `/Android/data/` directly is restricted. We will implement a step that asks the user to select the Balatro folder once via the system file picker to grant the app permanent access to that specific directory.

## Progress Tracking

- [x] Phase 1: Project Setup
- [x] Phase 2: Core Logic (Service, NetworkUtils, ConfigUtils, SAF flow)
- [x] Phase 3: UI Development
- [x] Phase 4: Integration & Testing — `app-debug.apk` (9.7 MB) generado

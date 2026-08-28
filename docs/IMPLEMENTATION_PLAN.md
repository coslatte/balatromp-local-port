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

### 3. Configuration Injection (Multi-Strategy Storage Access)
- **Strategy 1 — SAF (Storage Access Framework):** Uses `DocumentFile` via `ACTION_OPEN_DOCUMENT_TREE`. Works on most Android 11+ devices when the user navigates to the folder manually.
- **Strategy 2 — Direct File I/O:** Uses `java.io.File` with `MANAGE_EXTERNAL_STORAGE` permission. Works on Android 11 (some devices) and pre-11.
- **Strategy 3 — ADB Fallback:** Shows step-by-step instructions for manual file copy via `adb push`.
- **Target File:** `Multiplayer.jkr` located in `/Android/data/com.playstack.balatro.android/files/save/game/`.
- **Logic:**
  - Regex replacement to update `["server_url"] = "http://IP:PORT"`.
  - Atomic write to prevent file corruption.
  - Automatic fallback between strategies.

### 4. UI & App Launcher (Jetpack Compose)
- **UI Framework:** Jetpack Compose.
- **Buttons:**
  - **"Host LAN & Play":** Starts Ktor Service → Gets IP → Updates config with local IP → Launches Balatro.
  - **"Client Mode":** Inputs Host IP → Updates config with Host IP → Launches Balatro.
- **App Launch:** Use `PackageManager.getLaunchIntentForPackage("com.playstack.balatro.android")`.

## Implementation Steps

### Phase 1: Project Setup ✅
1. Android Studio project structure (Gradle 8.5, Kotlin 1.9.22, Compose 1.5.4).
2. Ktor 2.3.7 dependencies (CIO engine, WebSockets).
3. Permissions in `AndroidManifest.xml` (INTERNET, FOREGROUND_SERVICE, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, MANAGE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE).

### Phase 2: Core Logic ✅
1. **MultiplayerService:** Ktor WebSocket server on port 8788, foreground service with persistent notification.
2. **NetworkUtils:** IP resolution via `ConnectivityManager` and `NetworkInterface` (handles wlan0, ap0, swlan0).
3. **ConfigManager:** Three-strategy config injection (SAF → Direct I/O → ADB fallback).
4. **SAF flow:** `ACTION_OPEN_DOCUMENT_TREE` with persistent URI permissions.

### Phase 3: UI Development ✅
1. Main screen with Compose (host/client mode toggle, IP input, folder selection).
2. State management for server status, IP input, access method.
3. ADB fallback dialog with instructions.
4. Permission handling for `MANAGE_EXTERNAL_STORAGE` and `POST_NOTIFICATIONS`.

### Phase 4: Integration & Testing ✅
- `app-debug.apk` (9.7 MB) generated successfully.
- BUILD SUCCESSFUL with zero warnings.

## Verification & Testing

- Verify Ktor server responds on `http://localhost:8788`.
- Verify `Multiplayer.jkr` is correctly updated after clicking "Host" or "Join".
- Verify Balatro launches correctly after configuration.
- Test all three storage access strategies on Android 11+.

---

> **Note on Android 11+ Restrictions:** Accessing `/Android/data/` directly is restricted by Scoped Storage. The app implements three fallback strategies: SAF (primary), direct file I/O with `MANAGE_EXTERNAL_STORAGE` (secondary), and ADB instructions (last resort).

## Progress Tracking

- [x] Phase 1: Project Setup
- [x] Phase 2: Core Logic (Service, NetworkUtils, ConfigUtils, SAF flow)
- [x] Phase 3: UI Development
- [x] Phase 4: Integration & Testing — `app-debug.apk` (9.7 MB) generated

## Build Info

- **Gradle:** 8.5
- **AGP:** 8.2.2
- **Kotlin:** 1.9.22
- **Compose Compiler:** 1.5.8
- **Ktor:** 2.3.7
- **JDK:** 21 (Oracle)
- **Target SDK:** 34
- **Min SDK:** 26

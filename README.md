# Remote Bot for Telegram

<p align="center">
  <strong>Autonomous, open-source Android remote administration, surveillance, and automation tool powered by Telegram & SMS.</strong>
</p>

<p align="center">
  <a href="README_RU.md">🇷🇺 Читать на русском языке (Russian version)</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_--_15_(API_26--35)-brightgreen.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.0-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-blue.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/License-GPLv3-orange.svg" alt="License" />
</p>

---

## 📖 Overview

**Remote Bot for Telegram** transforms any Android smartphone, tablet, or TV box into a private, self-hosted remote management server. Control your device, forward notifications, manage telephony, track location, take photos, stream screen recordings, and run shell commands directly from your personal Telegram chat or via fallback SMS commands — without relying on proprietary cloud services or subscriptions.

Designed for modern **Android 14 & 15 (Target SDK 35)** with full backwards compatibility down to **Android 8.0 (API 26)**.

---

## ✨ Key Features & Capabilities

### 📱 1. Communication & Channels
* **Telegram Bot API**:
  * **Long Polling Mode**: Direct polling with customizable Bot API root (supports self-hosted Telegram Bot API servers for 2 GB file transfers and DPI censorship circumvention).
  * **FCM Push Wakeup Mode**: Ultra-low battery consumption mode. The background polling service sleeps and wakes up on-demand when an incoming command arrives.
  * **Network & Proxy**: Built-in SOCKS5, HTTP, and WebSocket (WSS) proxy engine with real-time ping check.
* **Offline SMS Bot (Dual Fallback)**:
  * Works completely offline when mobile data or Wi-Fi is disabled.
  * **Trigger Prefix**: Responds to `rb` prefix in any case (`rb`, `RB`, `Rb`, `rB`) with whitespace, newline, slash, or colon separators.
  * **Mode 1 (PIN Protected)**: Execute commands from any phone number using your secret PIN (`rb 1234 /wifi on`).
  * **Mode 2 (Trusted Numbers)**: Whitelist specific phone numbers to execute commands directly without entering a PIN.

---

### 📷 2. Camera, Audio & Screen Capture
* **Camera (`/photo`, `/videorec`)**:
  * Silent background photo capture with front or rear camera, flash toggle, and orientation rotation (`/photo front flash_off`).
  * Background video recording with configurable duration (`/videorec back 30`).
  * Real-time motion detection via CameraX ImageAnalysis (`/detectmotion`).
  * MediaStore gallery exporter: send recent photos grouped in media albums (`/gallery 5`).
* **Audio (`/rec`, `/tts`, `/playback`, `/volume`)**:
  * High-quality background microphone recording saved to AAC/m4a (`/rec 60`).
  * Text-to-Speech synthesis through the device's loudspeaker (`/tts Hello from Telegram`).
  * Media player control for local tracks and voice notes (`/playback`).
  * Stream volume adjustment (call, media, alarm, ringtone) (`/volume media 80`).
  * Sound profile switching (`/ring_mode normal|silent|vibrate`).
  * **Emergency Device Search (`/device_search`)**: Triggers maximum volume siren with interactive stop button.
* **Screen Capture (`/screenshot`, `/screen_rec`)**:
  * Autonomous hidden screenshot via Root (`/system/bin/screencap`) or Shizuku without user confirmation.
  * Screen video recording (`/screen_rec 15`) delivered as MP4.

---

### 📞 3. Telephony & SMS Management
* **SMS Messages**:
  * Send SMS messages with Dual-SIM support (`/sms [sim] <phone> <text>`).
  * Read SMS inbox and sent history with pagination (`/sms_inbox 10`).
  * **Interactive SMS Reply Flow**:
    * Tap **«💬 Reply by SMS»** inline button under incoming forwarded SMS.
    * Bot opens a clean message prompt: `✍️ Enter message for +1234567890 (SIM 1):` with an **«❌ Cancel»** button.
    * Simply type your response and send — the bot transmits the SMS from the matching SIM slot.
    * Also supports native Telegram swipe/quote reply on forwarded messages.
* **Calls & USSD**:
  * Make phone calls (`/call [sim] <phone>`).
  * View call log history with duration, timestamps, and call types (`/callshistory 20`).
  * Query USSD balance/service codes with Dual-SIM selection (`/ussd *100#`) via Accessibility Service and TelephonyManager.

---

### 🔔 4. Notification Mirroring & Instant Quick Reply
* **Real-time Forwarding**: Intercepts Android system notifications via `NotificationListenerService`.
* **Instant Messenger Reply (WhatsApp, Telegram, Signal, Viber, VK, Skype, Gmail)**:
  * Automatically extracts `RemoteInput` and `PendingIntent` from notifications.
  * Inline button **«💬 Reply»** under forwarded notification in Telegram.
  * Enter response text in Telegram -> Bot injects text into `RemoteInput.addResultsToIntent()` and triggers the intent.
  * The messenger app delivers the reply to your contact in the background without waking the screen.
  * Also supports `/answer <id> <text>` and `/resp <id> <text>`.
* **Filtering & Deduplication**:
  * Whitelist/Blacklist package filters.
  * RegEx rule engine (`TitleMatches`, `TitleContains`, `BodyMatches`, `BodyContains`).
  * Filter out repetitive spam, capture banking 2FA SMS codes.
  * Adjustable deduplication window (prevents spamming on progress updates).
  * Auto-cancel notification from status bar after forwarding.
* **Android 11–15 Package Visibility**: Full support for `QUERY_ALL_PACKAGES` and `<queries>` in app selection picker.

---

### 📍 5. Geolocation & Anti-Theft
* **Location Tracking (`/location` / `/track`)**:
  * **Multi-Provider Fused Geolocation**:
    * **Google Play Services Fused Location**: Primary fused engine combining GPS, Cell towers, and Wi-Fi on GMS devices.
    * **Huawei HMS Location Kit**: Native Fused Location support for Huawei & Honor devices without Google Play Services.
    * **AOSP System GNSS & Network**: Direct satellite reception via `GnssStatus` (GPS/GLONASS/Galileo/BeiDou) and cell towers.
    * **IP Geolocation Fallback**: Automatic IP-based location fallback when both satellites and cell towers are shielded.
  * **Default Mode**: One-time location fix with configurable timeout.
  * **Periodic Live Tracking**: Continuous updates until stopped (`/location live 30s|1m|5m|15m|30m|60m` or `/track 5m`, stop: `/track stop`, status: `/track status`).
  * **Interactive Inline Buttons**: Quick interval selection and one-click stop tracking button.
  * **Signal Diagnostic**: Explicitly reports active engine (`Google Fused`, `Huawei HMS Fused`, `GPS/GNSS`, `Cell-ID/Wi-Fi`, `IP`), C/N0 dB-Hz signal level, and satellite counts.
  * Delivers GPS coordinates, accuracy, altitude, speed, reverse-geocoded physical address, and instant Google Maps & OpenStreetMap links.
* **GPS Control (`/gps on|off|toggle`)**: Programmatic location provider toggle via Root.
* **Shake Detector Alarm (`/shake_detector on|off [threshold]`)**: Detects physical movement or tampering using accelerometer sensors and sends immediate emergency alert.

---

### 📁 6. File System, Apps & Task Manager
* **Files (`/get_file` / `/gf`, `/download`, `/ls`, `/ls_interactive` / `/lsi`)**:
  * **Interactive Explorer (`/ls_interactive` or `/lsi [path]`)**: Graphical directory navigator with inline folder buttons, navigate up `⬆️`, refresh `🔄`, and single-click downloading of files or full directory zip packaging.
  * Download any file or directory (auto-zipped on the fly) up to 2 GB (`/get_file /sdcard/DCIM zip` or `/gf <path>`).
  * Remote download from direct URL to device storage (`/download <url>`).
  * Text file explorer (`/ls /sdcard`).
  * Unsent file queue tracker (`/unsent_files`) for offline recovery.
* **Applications (`/apps`, `/app_launch`, `/install`)**:
  * **Direct APK Upload Installation**: Upload an `.apk` document to Telegram chat — the bot downloads and initiates installation automatically.
  * List installed user and system applications with package search (`/apps whatsapp`).
  * Launch any application remotely (`/app_launch com.whatsapp`).
  * Silent APK installation from local storage via Root/Shizuku (`/install /sdcard/app.apk`).
* **Configuration & Backup (`/backup`, `/restore_backup`)**:
  * Export clean configuration backup as `.json` document directly to chat (`/backup`).
  * Automatic configuration restore upon uploading `.json` document to chat or via `/restore_backup <json>`.
* **Task Manager (`/taskmanager`, `/kill`)**:
  * View active Linux processes with PID, CPU %, and memory usage.
  * Terminate any process or background application (`/kill <PID|pkg>`).

---

### ⚙️ 7. System & Root Shell
* **Linux Shell (`/shell <command>`)**: Full Root/Shizuku terminal via `libsu` with real-time stdout/stderr output.
* **System Toggles**:
  * Wi-Fi (`/wifi on|off`), Bluetooth (`/bluetooth on|off`), Flashlight (`/flashlight on|off`).
  * Mobile Data (`/mobile_data on|off`), Hotspot (`/hotspot on|off`), Airplane Mode (`/airplane on|off`).
  * Power Saving (`/powersave`), Auto-brightness (`/auto_brightness`), Sync (`/sync`).
  * Wireless ADB (`/adb_wifi 5555`) with instant connect command string.
  * Clipboard read and write (`/clipboard get`, `/clipboard set <text>`).
  * Device Power: `/reboot`, `/shutdown`, `/recovery`, `/fastboot`.
* **Diagnostics**: `/system_info`, `/network`, `/battery`, `/toggles`.

---

### ⚡ 8. Automation & Integrations
* **Tasker Integration**:
  * Execute Tasker tasks remotely from Telegram: `/tasker <TaskName> [par1] [par2]`.
  * Built-in Tasker Action Plugin: trigger Remote Bot actions from any Tasker automation profile.
* **JavaScript Engine (`/script <code>`)**: Embedded QuickJS runtime with access to device bridge APIs.
* **Cron Timers (`/timers list|add|del`)**: Schedule one-time or recurring bot commands (`add 60s /photo`, `add 10m /location`).
* **Hardware Triggers**: Automatic alerts on charger connect/disconnect, critical battery level, and device reboot.

---

### 🧪 9. Experimental Features (Developer / Debug Menu)
> The Developer Debug menu is revealed by tapping the app version 5 times in the **«About»** screen (`AboutTab`).

* **🛡️ 2FA Action Interception & Confirmation (`enable2FaInterception`)**:
  * **Status**: **Disabled by default** (toggle is OFF). Must be manually enabled in the *«🧪 Experimental Features»* card of the Debug menu.
  * **Purpose**: Allows 1-click confirmation or rejection of 2FA push notifications (VPN, ADFS, GitLab, enterprise SSO) directly from Telegram via inline buttons.
  * **Supported Authenticator Apps**: Multifactor (`ru.multifactor.app`), Duo Mobile (`com.duosecurity.duomobile`), Yandex Key (`ru.yandex.key`), LastPass Authenticator (`com.lastpass.authenticator`), Microsoft Authenticator (`com.azure.authenticator`), Google Authenticator (`com.google.android.apps.authenticator2`).
  * **When Disabled**: Push notifications are forwarded as plain informational text without action buttons; stale callback clicks are safely rejected.
  * **Reliability Architecture**:
    * **Protected 2FA Cache**: 2FA action notifications are stored in a dedicated `active2FaSbnById` map and are never evicted by background chat noise.
    * **Strict FIFO Rotation**: General notification cache expanded to 1,000 entries with deterministic FIFO eviction.
    * **Live Android OS Shade Fallback**: When an ID is missing from local memory, queries live system notifications via `NotificationListenerService.getActiveNotifications()` to locate active `PendingIntent`s.
    * **Android 14+ Compatibility**: Dispatches `PendingIntent`s with `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`.

---

## 🔒 Security Architecture

* **Strict Whitelist by Numeric ID**: Authorized strictly by immutable **Telegram User ID** (never `@username`, protecting against username change hijacking).
* **Role Model & Granular Event Subscriptions (`/events`, `/subs`)**:
  * **«Subscriber» Role (Default)**: Receives designated categories of background events (Push notifications, incoming SMS, phone calls, system alerts).
  * **«Commands Only / Operator» Role**: All background notification streams are muted. The user retains full command execution rights (`/status`, `/photo`, `/sms`, `/shell`, etc.) without receiving ambient spam from personal SMS, calls, or app notifications.
  * **Interactive Bot Menu (`/events`)**: 7 inline keyboard buttons right inside Telegram to toggle categories on/off and switch roles in 1 tap.
  * **Bot Commands**:
    * `/events` — Open the interactive subscription control menu for yourself.
    * `/events list` — Summary table of all authorized users with their roles and active category subscriptions.
    * `/events off` / `/events on` — Instantly switch to «Commands Only» or turn on all primary event notifications.
    * `/events <notif|sms|calls|system> <on|off>` — Toggle a specific notification category directly.
    * `/events <user_id> [on|off|category]` — Manage subscriptions for another user (admin).
  * **Mobile App UI**: Compact category filter chips (🔔 Push, 💬 SMS, 📞 Calls, ⚙️ Sys) under each whitelisted User ID in the *«🤖 Bot»* tab.
* **PIN Protection**: Destructive commands (reboot, wipe, shell) require explicit confirmation or SMS PIN.
* **Encrypted Secrets**: Bot tokens, proxy passwords, and encryption keys are stored using `EncryptedSharedPreferences` backed by Android Keystore.
* **No Third-Party Telemetry**: Connects directly to `api.telegram.org` or your private Bot API instance. Zero external analytics or third-party servers.

---

## 🏗 Technology Stack & Architecture

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0 (Coroutines, Flow, Serialization) |
| **UI Framework** | Jetpack Compose Material 3 with NavigationRail |
| **Architecture** | Clean Architecture, Multi-Module Gradle, MVI/MVVM |
| **Network** | Ktor Client, OkHttp 4, Kotlinx Serialization |
| **Root & Shell** | Libsu (topjohnwu), Shizuku API |
| **Location** | Dual Hybrid: Google Play Services Location (`21.4.0`) + Huawei HMS Location Kit (`6.20.0.300`) + AOSP GNSS |
| **Push** | Dual Push Gateway: Google FCM (`google-services.json`) + Huawei HMS Push Kit (`agconnect-services.json`) |
| **Database** | Room ORM, EncryptedSharedPreferences |

---

### 📚 Setup Guides
* 📘 [Google Firebase FCM Setup (`google-services.json`)](docs/FIREBASE_SETUP.md)
* 📕 [Huawei HMS Push Kit Setup (`agconnect-services.json`)](docs/HMS_SETUP.md)
* ☁️ [Cloudflare Workers Push Gateway Deployment](docs/CLOUDFLARE_WORKER_SETUP.md)
* 🛡️ [Cloudflare Workers Reverse Proxy for api.telegram.org](docs/CLOUDFLARE_TELEGRAM_REVERSE_PROXY.md)

---

## 🚀 Building from Source

### Prerequisites
* JDK 17 or higher
* Android SDK 35 (Build-tools 35.0.0+)
* Android NDK (optional, for native modules)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/your-username/remote-bot.git
cd remote-bot/newapp

# Run JVM domain tests
./gradlew :domain:test

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

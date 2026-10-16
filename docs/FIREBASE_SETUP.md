[English](FIREBASE_SETUP.md) | [Русский](FIREBASE_SETUP_RU.md)

# Guide: How to Get Your Firebase Configuration (`google-services.json`) for Remote Bot

This guide provides step-by-step instructions for creating a personal Google Firebase project and obtaining the `google-services.json` configuration file required for **FCM Push Wakeup** (waking up the bot via push notifications).

---

## 1. Registration and Project Creation in Firebase

1. Navigate to the [Firebase Console](https://console.firebase.google.com/) and sign in with your Google account.
2. Click **Add project** or **Create project**.
3. Enter a project name (e.g., `MyRemoteBot` or any custom name).
4. Click **Continue**.
5. *Enable Google Analytics*: you can disable it for faster creation and enhanced privacy, then click **Create project**.
6. Wait for the project creation to finish and click **Continue**.

---

## 2. Adding Android Application to the Project

1. On the Project Overview dashboard, click the round **Android** icon (Add app).
2. Fill in the required fields:
   * **Android package name**:
     ```text
     com.remotebot.app
     ```
     > [!IMPORTANT]
     > The package name must **exactly match** `com.remotebot.app` (our application package).
   * **App nickname**: optional (e.g., `Remote Bot`).
   * **Debug signing certificate SHA-1**: optional for basic push delivery, can be left blank.
3. Click the blue **Register app** button.

---

## 3. Downloading `google-services.json`

1. In the next step ("Download config file"), click **Download google-services.json**.
2. Save the file to your computer or phone.
3. In the Firebase console, click **Next**, then **Next** again, and finally click **Continue to console**.

---

## 4. Enabling Required Google APIs (Critically Important!)

To ensure device registration and FCM token retrieval do not fail with `Firebase Installations Service is unavailable`, enable the required project APIs:

1. Open the Google Cloud Console library for your project:
   * **Firebase Installations API**:
     👉 [https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com)
     *(Select your project at the top and click **Enable**)*.
   * **Cloud Messaging API**:
     👉 [https://console.cloud.google.com/apis/library/fcm.googleapis.com](https://console.cloud.google.com/apis/library/fcm.googleapis.com)
     *(Click **Enable**)*.

---

## 5. Importing Configuration into the Remote Bot App

1. Open the **Remote Bot** app on your device.
2. In the side navigation menu (NavigationRail), select the **🔥 FCM** tab.
3. Add the configuration using one of two methods:
   * **Method A (File)**: Tap **📁 Choose file** and select the downloaded `google-services.json`.
   * **Method B (Clipboard)**: Open `google-services.json` in any text editor, copy its contents to the clipboard, and tap **📋 From clipboard**.
4. Tap the orange button **⚡ Apply and Initialize Firebase**.
5. The application will parse the parameters, initialize `FirebaseApp`, and request an **FCM Device Token**:
   * Green status: `✅ Firebase initialized successfully!`
   * A card will appear displaying your device token with a **📋 Copy token** button.
   * This token and your Push Gateway URL are used to wake up the bot without maintaining a continuous background TCP socket.

---

## 6. Troubleshooting

| Issue / Error | Cause | Solution |
| :--- | :--- | :--- |
| `Timeout (12 sec) waiting for Google Play Services / FCM` | Google server blocks or missing Play Services | Enable VPN on the device/emulator or use an emulator with **Google APIs / Google Play**. |
| `Firebase Installations Service is unavailable` | Installations API is not enabled in Google Cloud | Enable the [Firebase Installations API](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com) as described in section 4. |
| `No client found for package com.remotebot.app` | Wrong package name specified during app creation | In the Firebase Console, add an Android app with the exact package name `com.remotebot.app` and download the updated JSON. |

[English](HMS_SETUP.md) | [Русский](HMS_SETUP_RU.md)

# Guide: How to Get Your Huawei HMS Configuration (`agconnect-services.json`) for Remote Bot

This guide provides step-by-step instructions for creating a project in Huawei AppGallery Connect (AGC) and obtaining the `agconnect-services.json` configuration file required for **HMS Push Wakeup** (waking up the bot via Huawei Mobile Services push notifications).

---

## 1. Registration and Sign-in to AppGallery Connect

1. Go to the Huawei Developers portal: [Huawei Developers](https://developer.huawei.com/consumer/en/) (or [AppGallery Connect](https://developer.huawei.com/consumer/en/service/josp/agc/index.html)).
2. Register or log in with your HUAWEI ID account.
3. Open the **AppGallery Connect** console and select **My Projects**.

---

## 2. Creating a Project and Adding an App

1. Click **Add project** and enter a project name (e.g., `RemoteBot`).
2. Inside the created project, click **Add app**:
   * **Platform**: `Android`
   * **Device**: `Phone`
   * **App name**: `Remote Bot`
   * **Package name**:
     ```text
     com.remotebot.app
     ```
     > [!IMPORTANT]
     > The package name must **exactly match** `com.remotebot.app` (our application package).
   * **App category**: `App`.
   * **Default language**: `Russian` (or any language of your choice).
3. Click **OK**.

---

## 3. Enabling Push Kit in Project Services

1. In the left sidebar of the project, select **Manage APIs**.
2. Locate **Push Kit** in the list and toggle the switch to **Enabled**.
3. In the left navigation, go to **Push Kit** $\to$ **Enable Push Service** and confirm the activation.

---

## 4. Adding SHA-256 Certificate Fingerprint (Critically Important!)

> [!WARNING]
> Unlike Firebase, Huawei **strictly requires** adding the SHA-256 fingerprint of your APK signing certificate. Without it, the SDK will fail with error `6003: CERT_FINGERPRINT_EMPTY` or `907135702`.

1. **Copy the SHA-256 fingerprint of your APK signature**:
   * **For Production Release APK (`remotebot` / `release.jks` key)**:
     ```text
     9F:1B:55:4A:BA:2B:E9:FA:43:6A:FC:36:A4:BF:D9:CD:93:6A:11:19:2D:5C:A3:D4:F0:B1:20:5B:D7:D4:6F:CC
     ```
   * **For Debug APK (standard `debug.keystore`)**:
     ```text
     79:80:EC:EB:4A:F2:72:6B:FF:C2:89:6D:88:9D:B7:8D:11:8E:E6:FD:D3:10:66:E6:2D:68:A3:D4:9C:2C:CF:EC
     ```
   > [!TIP]
   > In Huawei AppGallery Connect console you can add **both** fingerprints (using the `+` button), so push notifications work for both Release APK and Debug versions during development.
   * **If you built with your own signing key**:
     * From debug keystore:
       ```bash
       keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep "SHA256:"
       ```
     * From existing Release APK (no keystore password required):
       ```bash
       apksigner verify --print-certs app-release.apk | grep "SHA-256"
       ```
     * From Release Keystore file (`.jks` / `.keystore`):
       ```bash
       keytool -list -v -keystore /path/to/release.jks -alias your_alias | grep "SHA256:"
       ```
2. In AppGallery Connect, go to **Project Settings** $\to$ **General Information** tab.
3. Scroll down to the **App Information** section.
4. Locate the **SHA-256 certificate fingerprint** field.
5. Click the **+** icon (or checkmark) and paste your SHA-256 fingerprint.
6. Click **Save**.

---

## 5. Downloading `agconnect-services.json`

1. On the same **Project Settings** $\to$ **General Information** page under **App Information**, click:
   **agconnect-services.json** (download configuration button).
2. Save the `agconnect-services.json` file to your computer or phone.

---

## 6. Importing Configuration into the Remote Bot App

1. Open the **Remote Bot** app on your phone.
2. In the side navigation menu (NavigationRail), select the **🔔 Push** tab.
3. In the provider selection card, choose **🦅 Huawei HMS** (or **⚡ Hybrid**).
4. Load the configuration using one of two methods:
   * **Method A (File)**: Tap **📁 Choose file** and select the downloaded `agconnect-services.json`.
   * **Method B (Clipboard)**: Copy the contents of `agconnect-services.json` to the clipboard and tap **📋 From clipboard**.
5. Tap **⚡ Apply and Initialize HMS**.
6. The app will parse parameters (`app_id`, `client_id`, `api_key`), initialize `AGConnectInstance`, and request an **HMS Device Token**:
   * Green status: `✅ Huawei HMS ready! Token: ...`
   * A card will appear displaying your device token with a copy to clipboard button.

---

## 7. Troubleshooting

| Code / Error | Cause | Solution |
| :--- | :--- | :--- |
| `6003: CERT_FINGERPRINT_EMPTY` | SHA-256 fingerprint is not configured in AGC console | Add the SHA-256 of your `debug.keystore` (or release key) in AGC Console (Step 4), then download the updated JSON. |
| `907135702` | Mismatch of SHA-256 fingerprint or package name | Verify that the package name in AGC is exactly `com.remotebot.app` and the SHA-256 matches the key used to sign the installed APK. |
| `HMS Core is not available` | HMS Core service is not installed on non-Huawei devices | Install **HMS Core** (`com.huawei.hwid`) from Huawei AppGallery or switch to Google FCM. |
| `Token is empty (null)` | Push Kit service is disabled in the project | Open AGC $\to$ Manage APIs and ensure the **Push Kit** toggle is enabled (Step 3). |

[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (Cloudflare Worker — Hybrid FCM & HMS)

Serverless gateway running on Cloudflare Workers supporting dual push providers:
* **Google Firebase Cloud Messaging (FCM HTTP v1 & Legacy)**
* **Huawei Mobile Services Push Kit (HMS REST API v1 with OAuth2)**
* Cloudflare free plan includes 100,000 requests per day.
* Operates without requiring a VPS or server infrastructure.

---

## 1. Secrets and Environment Variables (Settings -> Variables and Secrets)

Configure required parameters in your Cloudflare Worker dashboard:

### Common Secret (Required):
* **`PUSH_GATEWAY_SECRET`**: your secret gateway password to authenticate incoming requests.

### For Google Firebase (FCM):
* **`FIREBASE_SERVICE_ACCOUNT`**: contents of the `serviceAccount.json` private key file from Firebase Console (*Project Settings $\to$ Service accounts $\to$ Generate new private key*).

### For Huawei HMS Push:
* **`HUAWEI_APP_ID`**: Application ID (`app_id` field from `agconnect-services.json`).
* **`HUAWEI_CLIENT_ID`**: Client ID (usually identical to `HUAWEI_APP_ID`).
* **`HUAWEI_CLIENT_SECRET`**: Client secret (`client_secret` field from `agconnect-services.json`).

---

## 2. Deployment to Cloudflare Workers

1. Open [Cloudflare Dashboard](https://dash.cloudflare.com/) $\to$ **Workers & Pages** $\to$ **Create application** $\to$ **Create Worker**.
2. Assign a name (e.g. `remotebot-push-gateway`) and click **Deploy**.
3. Click **Edit code**, paste the contents of [worker.js](worker.js), and click **Save and deploy**.
4. In **Settings** $\to$ **Variables and Secrets**, add the secrets mentioned above.
5. Click **Deploy**.

---

## 3. Telegram Webhook Routing Format

The gateway supports both automatic and explicit provider routing:

| Route URL | Provider | Behavior |
| :--- | :--- | :--- |
| `https://<worker>/tg/:token/:secretKey` | Automatic | If only FCM is configured $\to$ sends FCM. If only HMS $\to$ sends HMS. |
| `https://<worker>/tg/fcm/:fcmToken/:secretKey` | Google FCM | Explicit delivery via Firebase FCM. |
| `https://<worker>/tg/hms/:hmsToken/:secretKey` | Huawei HMS | Explicit delivery via Huawei Push Kit. |

---

## 4. Healthcheck Verification

Open in browser:
```text
https://remotebot-push-gateway.<subdomain>.workers.dev/health
```
Response:
```json
{
  "status": "healthy",
  "fcm": true,
  "hms": true
}
```

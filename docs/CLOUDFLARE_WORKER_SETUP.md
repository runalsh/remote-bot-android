[English](CLOUDFLARE_WORKER_SETUP.md) | [Русский](CLOUDFLARE_WORKER_SETUP_RU.md)

# Guide: Deploying Hybrid Push Gateway to Cloudflare Workers

This guide provides step-by-step instructions for deploying the serverless **RemoteBot Push Gateway** on the free **Cloudflare Workers** platform.

The gateway receives incoming webhooks from Telegram and immediately delivers a wake-up **Data Push** (via **Google FCM** or **Huawei HMS Push**) to your smartphone. This allows the bot to wake up instantly to execute commands while saving up to 90% of battery power.

---

## 1. Benefits of Cloudflare Workers
* **Completely Free**: up to 100,000 incoming requests per day on the free tier (plenty for thousands of messages daily).
* **No VPS/Server Needed**: operates directly on Cloudflare Edge global network with 10–30 ms latency.
* **Dual Provider Support**: delivers pushes to devices with Google Services (FCM) and Huawei/Honor devices (HMS).

---

## 2. Method A: Deployment via Web Dashboard (No terminal, directly in browser)

### Step 1: Create a Worker
1. Log in to [Cloudflare Dashboard](https://dash.cloudflare.com/) (or register for free).
2. In the left navigation sidebar, go to **Compute (Workers & Pages)**.
3. Click **Create application** $\to$ **Workers** tab $\to$ click **Create Worker**.
4. Set a name for the worker (e.g., `remotebot-push-gateway`) and click the blue **Deploy** button.

### Step 2: Paste Source Code
1. On the worker page, click **Edit code**.
2. Delete the default boilerplate code in the editor.
3. Open [`worker.js`](../server/serverpush/cloudflare-worker/worker.js), copy its entire contents, and paste it into the Cloudflare editor.
4. In the top-right corner, click **Save and deploy**.

### Step 3: Configure Variables and Secrets
1. Return to the worker main page and open the **Settings** tab $\to$ **Variables and Secrets**.
2. Click **Add** to configure the required parameters:

| Variable Name | Type | Description | Where to get it |
| :--- | :--- | :--- | :--- |
| `PUSH_GATEWAY_SECRET` | Secret (Encrypted) | Your secret gateway password to prevent spam | Any strong secret string (e.g., `my_secret_key_123`) |
| `FIREBASE_SERVICE_ACCOUNT` | Secret (Encrypted) | *(Optional)* Firebase service account private key | Firebase Console $\to$ Project Settings $\to$ Service accounts $\to$ Generate private key. Paste the entire `.json` file content |
| `HUAWEI_APP_ID` | Variable / Secret | *(Optional)* Huawei Application ID | The `app_id` field from your `agconnect-services.json` file |
| `HUAWEI_CLIENT_ID` | Variable / Secret | *(Optional)* Huawei Client ID | The `client_id` field (usually same as `HUAWEI_APP_ID`) |
| `HUAWEI_CLIENT_SECRET` | Secret (Encrypted) | *(Optional)* Huawei Client Secret | The `client_secret` field from your `agconnect-services.json` file |

> [!TIP]
> If you only use Google FCM, configuring `FIREBASE_SERVICE_ACCOUNT` is sufficient.  
> If you only use Huawei, specify the three `HUAWEI_*` variables.  
> If you want hybrid support, specify all variables; the gateway will handle both.

3. After adding variables, click **Deploy** to apply changes.

---

## 3. Method B: Deployment via CLI (Wrangler)

If you have Node.js and Wrangler installed:

```bash
cd server/serverpush/cloudflare-worker

# Authenticate with Cloudflare
npx wrangler login

# Set secrets
npx wrangler secret put PUSH_GATEWAY_SECRET
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT   # when using FCM
npx wrangler secret put HUAWEI_CLIENT_SECRET       # when using HMS

# Deploy worker
npx wrangler deploy
```

---

## 4. Healthcheck Verification

After deployment, open your worker's `/health` endpoint in your browser:
```text
https://remotebot-push-gateway.<your-subdomain>.workers.dev/health
```

The response will be in JSON format:
```json
{
  "status": "healthy",
  "fcm": true,
  "hms": true
}
```
*(The `true`/`false` values reflect which providers are configured and ready to dispatch pushes)*.

---

## 5. App Configuration in Remote Bot

1. Open the **Remote Bot** app on your phone.
2. Navigate to the **🔔 Push** tab in the side menu.
3. Scroll down to the **⚙️ Push Gateway Settings** card:
   * **Push Gateway URL**: enter your worker URL:
     ```text
     https://remotebot-push-gateway.<your-subdomain>.workers.dev
     ```
   * **Push Secret Key**: enter the same secret you set in `PUSH_GATEWAY_SECRET` (e.g., `my_secret_key_123`).
   * Toggle the **Push Wakeup Mode** switch to **Enabled** (green).
   * Tap **Save Push Gateway Parameters**.

---

## 6. Execution Flow Architecture

```
[User] 
   │ 
   ▼ (sends command to Telegram)
[Telegram Bot Servers]
   │ 
   ▼ (Webhook POST /tg/:token/:secretKey)
[Cloudflare Worker Gateway]
   │ 
   ▼ (dispatches lightweight Data Push "wakeup" ~50 bytes)
[Google FCM / Huawei HMS]
   │ 
   ▼ (wakes up Android application)
[Remote Bot Service on Phone]
   │ 
   ▼ (retrieves update from payload or polls Telegram)
[Sends response back to user]
```

---

## 7. Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `HTTP 401 Unauthorized` | Secret key mismatch | Verify that the secret key in Remote Bot matches `PUSH_GATEWAY_SECRET` in Cloudflare. |
| `HTTP 400 Invalid path` | Malformed endpoint path | Ensure Telegram webhooks target `/tg/:token/:secretKey` or `/tg/hms/:token/:secretKey`. |
| Push not delivered to Huawei | Huawei OAuth error | Check `HUAWEI_APP_ID` and `HUAWEI_CLIENT_SECRET` in worker secrets. Inspect worker logs for details. |
| Push not delivered to Google | Google OAuth error | Ensure `FIREBASE_SERVICE_ACCOUNT` contains valid JSON with a functional `private_key`. |

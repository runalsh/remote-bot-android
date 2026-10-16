[English](CLOUDFLARE_TELEGRAM_REVERSE_PROXY.md) | [Русский](CLOUDFLARE_TELEGRAM_REVERSE_PROXY_RU.md)

# Guide: Creating Personal Reverse Proxy for api.telegram.org on Cloudflare Workers

This guide explains how to set up your own free, secure reverse proxy for Telegram servers (`api.telegram.org`) using **Cloudflare Workers** in 2 minutes and connect it to **RemoteBot**.

---

## 1. Why is this needed?

* **Bypass ISP blocks and DPI**: If direct access to `api.telegram.org` is throttled or blocked by your carrier/ISP, Cloudflare Worker acts as a transparent tunnel without requiring heavy VPNs or slow, unreliable third-party SOCKS5/HTTP proxies.
* **High speed and stability**: Requests from your mobile device connect to the nearest Cloudflare Edge data center over direct HTTPS, and Cloudflare routes to Telegram via high-speed global backbones.
* **100% Free**: Up to 100,000 requests per day on the free Cloudflare plan (more than enough for personal bot administration).
* **Security & Privacy**: The worker code is just 7 lines of JavaScript running entirely in your personal Cloudflare account without third-party logging.

---

## 2. Worker Source Code

The code forwards all incoming HTTP methods (GET, POST), headers, and payloads (including file, photo, and video uploads) directly to `api.telegram.org`:

```javascript
export default {
  async fetch(request) {
    const url = new URL(request.url);
    url.hostname = "api.telegram.org";
    return fetch(url.toString(), request);
  },
};
```

---

## 3. Step-by-Step Creation in Cloudflare (via Browser)

### Step 1: Create a Worker
1. Log in to [Cloudflare Dashboard](https://dash.cloudflare.com/) (or register for free).
2. In the left navigation, select **Compute (Workers & Pages)**.
3. Click **Create application** $\to$ **Workers** tab $\to$ **Create Worker**.
4. Enter an identifier, for example: `my-tg-proxy`.
5. Click the blue **Deploy** button.

---

### Step 2: Paste Worker Code
1. On the created worker page, click **Edit code**.
2. Delete the default boilerplate code in the left editor pane.
3. Paste the following snippet:
   ```javascript
   export default {
     async fetch(request) {
       const url = new URL(request.url);
       url.hostname = "api.telegram.org";
       return fetch(url.toString(), request);
     },
   };
   ```
4. In the top-right corner, click **Deploy** (or **Save and deploy**).

---

### Step 3: Get Your Worker Address
1. Return to the worker overview page.
2. In the **Domains & Routes** section, find the link to your worker. It will look like:
   ```text
   https://my-tg-proxy.YOUR_SUBDOMAIN.workers.dev
   ```
3. Copy this URL.

> [!TIP]
> **If your carrier blocks `*.workers.dev` domains:**  
> You can bind your own custom domain for free in **Settings** $\to$ **Domains & Routes** $\to$ **Add Custom Domain** (e.g., `tg.mydomain.com`). Custom domains in Cloudflare bypass regional domain blocks seamlessly.

---

## 4. Configuring RemoteBot Application

1. Open the **RemoteBot** app on your Android device.
2. Navigate to the **🤖 Telegram** tab.
3. Scroll down to:
   **Custom API Root / Proxy (leave blank for default)**
4. Paste your Cloudflare worker address:
   ```text
   https://my-tg-proxy.YOUR_SUBDOMAIN.workers.dev
   ```
   *(You can enter it with or without `https://` — the app will automatically format it).*
5. Tap **💾 Save & Verify**.

---

## 5. How It Works Under the Hood

* RemoteBot sends requests to your worker URL:
   ```text
   https://my-tg-proxy.YOUR_SUBDOMAIN.workers.dev/bot<TOKEN>/getMe
   ```
* Cloudflare Worker intercepts the request, rewrites the hostname to `api.telegram.org`:
   ```text
   https://api.telegram.org/bot<TOKEN>/getMe
   ```
* And returns the response directly back to your application.
* The app verification status will display:
   ```text
   ✅ Connection to Telegram established successfully!
   Bot: @your_bot
   ```
* The app logs (Logcat / Logs tab) will confirm the custom API root is in use instead of the standard Telegram endpoint.

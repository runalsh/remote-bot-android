/**
 * RemoteBot Webhook Push Gateway (Cloudflare Worker)
 * Edge serverless gateway: Telegram Webhook -> Push Wakeup signal (~50 bytes)
 * Supports:
 *  1. Google Firebase Cloud Messaging (FCM HTTP v1 & Legacy)
 *  2. Huawei Mobile Services Push Kit (HMS REST API v1 with OAuth2)
 *  3. Dual / Hybrid Push Routing
 */

let cachedAccessToken = null;
let tokenExpiresAt = 0;

let cachedHuaweiToken = null;
let huaweiTokenExpiresAt = 0;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Health check
    if (request.method === "GET" && url.pathname === "/health") {
      return new Response(JSON.stringify({
        status: "healthy",
        fcm: !!(env.FIREBASE_SERVICE_ACCOUNT || env.FCM_LEGACY_SERVER_KEY),
        hms: !!(env.HUAWEI_APP_ID && env.HUAWEI_CLIENT_SECRET)
      }), {
        headers: { "Content-Type": "application/json" }
      });
    }

    // Telegram webhook endpoint:
    // Supported routes:
    // - /tg/:token/:secretKey
    // - /tg/fcm/:token/:secretKey
    // - /tg/hms/:token/:secretKey
    if (url.pathname.startsWith("/tg/")) {
      if (request.method !== "POST") {
        return new Response(JSON.stringify({ error: "Method Not Allowed" }), {
          status: 405,
          headers: { "Content-Type": "application/json" }
        });
      }

      const parts = url.pathname.replace(/^\/+|\/+$/g, "").split("/");
      
      let provider = "auto";
      let pushToken = "";
      let clientSecret = "";

      if (parts.length === 3) {
        // /tg/:token/:secretKey
        pushToken = parts[1];
        clientSecret = parts[2];
        const provQuery = url.searchParams.get("provider");
        if (provQuery) {
          provider = provQuery.toLowerCase();
        }
      } else if (parts.length === 4) {
        // /tg/:provider/:token/:secretKey (e.g. /tg/hms/... or /tg/fcm/...)
        provider = parts[1].toLowerCase();
        pushToken = parts[2];
        clientSecret = parts[3];
      } else {
        return new Response(JSON.stringify({
          error: "Invalid path. Expected /tg/:token/:secretKey or /tg/hms/:token/:secretKey"
        }), {
          status: 400,
          headers: { "Content-Type": "application/json" }
        });
      }

      if (env.PUSH_GATEWAY_SECRET && clientSecret !== env.PUSH_GATEWAY_SECRET) {
        return new Response(JSON.stringify({ error: "Unauthorized" }), {
          status: 401,
          headers: { "Content-Type": "application/json" }
        });
      }

      let updateId = 0;
      let updateJson = "";
      try {
        const body = await request.json();
        updateId = body.update_id || 0;
        updateJson = JSON.stringify(body);
      } catch (e) {
        return new Response(JSON.stringify({ error: "Invalid JSON" }), {
          status: 400,
          headers: { "Content-Type": "application/json" }
        });
      }

      // Determine dispatch push provider:
      const hasHms = !!(env.HUAWEI_APP_ID && env.HUAWEI_CLIENT_SECRET);
      const hasFcm = !!(env.FIREBASE_SERVICE_ACCOUNT || env.FCM_LEGACY_SERVER_KEY);

      const targetHms = provider === "hms" || (provider === "auto" && hasHms && !hasFcm);
      const targetFcm = provider === "fcm" || (provider === "auto" && hasFcm);

      // Dispatch Huawei HMS Push in background
      if (targetHms && hasHms) {
        const clientId = env.HUAWEI_CLIENT_ID || env.HUAWEI_APP_ID;
        const promise = sendHmsWakeup(pushToken, updateId, updateJson, env.HUAWEI_APP_ID, clientId, env.HUAWEI_CLIENT_SECRET);
        if (ctx?.waitUntil) ctx.waitUntil(promise);
      }

      // Dispatch Google FCM Push in background
      if (targetFcm && hasFcm) {
        let promise;
        if (env.FIREBASE_SERVICE_ACCOUNT) {
          promise = sendFcmWakeupV1(pushToken, updateId, updateJson, env.FIREBASE_SERVICE_ACCOUNT);
        } else if (env.FCM_LEGACY_SERVER_KEY) {
          promise = sendFcmWakeupLegacy(pushToken, updateId, updateJson, env.FCM_LEGACY_SERVER_KEY);
        }
        if (promise && ctx?.waitUntil) ctx.waitUntil(promise);
      }

      // Immediate 200 OK response to Telegram server
      return new Response(JSON.stringify({ status: "ok" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    return new Response("Not Found", { status: 404 });
  }
};

/**
 * =========================================================================
 * 1. Huawei HMS Push Kit API (REST v1)
 * =========================================================================
 */
async function sendHmsWakeup(hmsToken, updateId, updateJson, appId, clientId, clientSecret) {
  try {
    const accessToken = await getHuaweiOAuthToken(clientId, clientSecret);

    const hmsData = {
      action: "wakeup",
      update_id: String(updateId)
    };
    if (updateJson && updateJson.length < 3800) {
      hmsData.update_json = updateJson;
    }

    const payload = {
      validate_only: false,
      message: {
        data: JSON.stringify(hmsData),
        token: [hmsToken],
        android: {
          urgency: "HIGH",
          category: "IM"
        }
      }
    };

    const res = await fetch(`https://push-api.cloud.huawei.com/v1/${appId}/messages:send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=UTF-8",
        "Authorization": `Bearer ${accessToken}`
      },
      body: JSON.stringify(payload)
    });

    const respText = await res.text();
    console.log(`[HMS] Wakeup sent for update_id ${updateId}, status: ${res.status}, resp: ${respText}`);
  } catch (err) {
    console.error(`[HMS] Error: ${err.message || err}`);
  }
}

/**
 * Get OAuth 2.0 Bearer Token from Huawei Cloud
 */
async function getHuaweiOAuthToken(clientId, clientSecret) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedHuaweiToken && huaweiTokenExpiresAt > now + 60) {
    return cachedHuaweiToken;
  }

  const res = await fetch("https://oauth-login.cloud.huawei.com/oauth2/v3/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      client_id: clientId,
      client_secret: clientSecret
    }).toString()
  });

  const data = await res.json();
  if (!res.ok || !data.access_token) {
    throw new Error(`Huawei OAuth error: ${JSON.stringify(data)}`);
  }

  cachedHuaweiToken = data.access_token;
  huaweiTokenExpiresAt = now + (data.expires_in || 3600);
  return cachedHuaweiToken;
}

/**
 * =========================================================================
 * 2. Modern Google FCM HTTP v1 API
 * =========================================================================
 */
async function sendFcmWakeupV1(fcmToken, updateId, updateJson, serviceAccountRaw) {
  try {
    const sa = typeof serviceAccountRaw === "string" ? JSON.parse(serviceAccountRaw) : serviceAccountRaw;
    const token = await getGoogleOAuthToken(sa);

    const fcmData = {
      action: "wakeup",
      update_id: String(updateId)
    };
    if (updateJson && updateJson.length < 3800) {
      fcmData.update_json = updateJson;
    }

    const payload = {
      message: {
        token: fcmToken,
        data: fcmData,
        android: {
          priority: "high"
        }
      }
    };

    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify(payload)
    });

    const respText = await res.text();
    console.log(`[FCM v1] Wakeup sent for update_id ${updateId}, status: ${res.status}, resp: ${respText}`);
  } catch (err) {
    console.error(`[FCM v1] Error: ${err.message || err}`);
  }
}

/**
 * Zero-dependency Google OAuth2 token generator using WebCrypto RS256
 */
async function getGoogleOAuthToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedAccessToken && tokenExpiresAt > now + 120) {
    return cachedAccessToken;
  }

  const header = {
    alg: "RS256",
    typ: "JWT",
    ...(sa.private_key_id ? { kid: sa.private_key_id } : {})
  };

  const claimSet = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now - 30,
    exp: now + 3600
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedClaimSet = base64UrlEncode(JSON.stringify(claimSet));
  const signatureInput = `${encodedHeader}.${encodedClaimSet}`;

  const keyBuffer = pemToArrayBuffer(sa.private_key);
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBuffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const encoder = new TextEncoder();
  const signatureBytes = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, encoder.encode(signatureInput))
  );

  const jwt = `${signatureInput}.${base64UrlEncodeBytes(signatureBytes)}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenData = await tokenRes.json();
  if (!tokenRes.ok || !tokenData.access_token) {
    throw new Error(`Google OAuth error: ${JSON.stringify(tokenData)}`);
  }

  cachedAccessToken = tokenData.access_token;
  tokenExpiresAt = now + (tokenData.expires_in || 3600);
  return cachedAccessToken;
}

function pemToArrayBuffer(pem) {
  if (!pem) return new ArrayBuffer(0);
  const normalized = pem.replace(/\\n/g, "\n");
  const b64 = normalized
    .replace(/-----BEGIN[ A-Z0-9_-]+-----/g, "")
    .replace(/-----END[ A-Z0-9_-]+-----/g, "")
    .replace(/[\s\r\n]+/g, "");
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64UrlEncode(str) {
  const encoder = new TextEncoder();
  return base64UrlEncodeBytes(encoder.encode(str));
}

function base64UrlEncodeBytes(bytes) {
  let binary = "";
  const uint8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const len = uint8.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(uint8[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * =========================================================================
 * 3. Legacy Firebase FCM API
 * =========================================================================
 */
async function sendFcmWakeupLegacy(fcmToken, updateId, serverKey) {
  const payload = {
    to: fcmToken,
    data: {
      action: "wakeup",
      update_id: String(updateId)
    }
  };

  try {
    const res = await fetch("https://fcm.googleapis.com/fcm/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `key=${serverKey}`
      },
      body: JSON.stringify(payload)
    });
    console.log(`[FCM Legacy] Wakeup sent for update_id ${updateId}, status: ${res.status}`);
  } catch (err) {
    console.error(`[FCM Legacy] Error: ${err}`);
  }
}

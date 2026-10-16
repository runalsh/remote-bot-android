import test from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

test("Health check endpoint returns 200 and disabled flags by default", async () => {
  const req = new Request("http://localhost/health", { method: "GET" });
  const env = {};
  const res = await worker.fetch(req, env, {});

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "healthy");
  assert.equal(data.fcm, false);
  assert.equal(data.hms, false);
});

test("Health check endpoint reflects configured FCM and HMS credentials", async () => {
  const req = new Request("http://localhost/health", { method: "GET" });
  const env = {
    FCM_LEGACY_SERVER_KEY: "legacy_fcm_key",
    HUAWEI_APP_ID: "12345678",
    HUAWEI_CLIENT_SECRET: "hms_secret_xyz"
  };
  const res = await worker.fetch(req, env, {});

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "healthy");
  assert.equal(data.fcm, true);
  assert.equal(data.hms, true);
});

test("Webhook endpoint returns 400 on incomplete path", async () => {
  const req = new Request("http://localhost/tg/only_token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 100 })
  });
  const res = await worker.fetch(req, {}, {});

  assert.equal(res.status, 400);
  const data = await res.json();
  assert.match(data.error, /Invalid path/);
});

test("Webhook endpoint returns 400 on excessive path segments", async () => {
  const req = new Request("http://localhost/tg/one/two/three/four", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 100 })
  });
  const res = await worker.fetch(req, {}, {});

  assert.equal(res.status, 400);
  const data = await res.json();
  assert.match(data.error, /Invalid path/);
});

test("Webhook endpoint returns 401 when secret does not match PUSH_GATEWAY_SECRET", async () => {
  const req = new Request("http://localhost/tg/sample_token/wrong_secret", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 101 })
  });
  const env = { PUSH_GATEWAY_SECRET: "correct_secret" };
  const res = await worker.fetch(req, env, {});

  assert.equal(res.status, 401);
  const data = await res.json();
  assert.equal(data.error, "Unauthorized");
});

test("Webhook endpoint returns 405 on non-POST method", async () => {
  const req = new Request("http://localhost/tg/sample_token/test_secret", {
    method: "GET"
  });
  const res = await worker.fetch(req, {}, {});

  assert.equal(res.status, 405);
  const data = await res.json();
  assert.equal(data.error, "Method Not Allowed");
});

test("Webhook endpoint returns 400 on invalid JSON body", async () => {
  const req = new Request("http://localhost/tg/sample_token/test_secret", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "invalid-not-json"
  });
  const res = await worker.fetch(req, {}, {});

  assert.equal(res.status, 400);
  const data = await res.json();
  assert.equal(data.error, "Invalid JSON");
});

test("Webhook endpoint processes valid legacy format /tg/:token/:secretKey", async () => {
  let waited = false;
  const ctx = {
    waitUntil(promise) {
      waited = true;
    }
  };
  const env = {
    PUSH_GATEWAY_SECRET: "test_secret_123"
  };
  const req = new Request("http://localhost/tg/sample_fcm_token/test_secret_123", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 998877 })
  });
  const res = await worker.fetch(req, env, ctx);

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "ok");
});

test("Webhook endpoint processes valid HMS route /tg/hms/:token/:secretKey", async () => {
  const backgroundTasks = [];
  const ctx = {
    waitUntil(promise) {
      backgroundTasks.push(promise);
    }
  };
  const env = {
    PUSH_GATEWAY_SECRET: "test_secret_123",
    HUAWEI_APP_ID: "100200300",
    HUAWEI_CLIENT_SECRET: "hms_sec"
  };
  const req = new Request("http://localhost/tg/hms/hms_target_device_token/test_secret_123", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 112233 })
  });
  const res = await worker.fetch(req, env, ctx);

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "ok");
  assert.equal(backgroundTasks.length, 1);
});

test("Webhook endpoint processes valid query provider /tg/:token/:secretKey?provider=hms", async () => {
  const backgroundTasks = [];
  const ctx = {
    waitUntil(promise) {
      backgroundTasks.push(promise);
    }
  };
  const env = {
    PUSH_GATEWAY_SECRET: "test_secret_123",
    HUAWEI_APP_ID: "100200300",
    HUAWEI_CLIENT_SECRET: "hms_sec"
  };
  const req = new Request("http://localhost/tg/hms_target_device_token/test_secret_123?provider=hms", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ update_id: 445566 })
  });
  const res = await worker.fetch(req, env, ctx);

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, "ok");
  assert.equal(backgroundTasks.length, 1);
});

test("Unknown endpoint returns 404", async () => {
  const req = new Request("http://localhost/non-existent-endpoint", { method: "GET" });
  const res = await worker.fetch(req, {}, {});

  assert.equal(res.status, 404);
});


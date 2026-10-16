import test from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

test("Proxy redirects request to api.telegram.org preserving path and query", async () => {
  let fetchedUrl = "";
  let fetchedOptions = null;

  globalThis.fetch = async (url, options) => {
    fetchedUrl = url;
    fetchedOptions = options;
    return new Response(JSON.stringify({ ok: true, result: "proxied" }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };

  const req = new Request("https://my-tg-proxy.workers.dev/bot12345:ABC/getUpdates?offset=100", {
    method: "POST",
    headers: { "X-Custom": "Test" },
    body: JSON.stringify({ timeout: 30 })
  });

  const res = await worker.fetch(req);
  assert.equal(res.status, 200);
  assert.equal(fetchedUrl, "https://api.telegram.org/bot12345:ABC/getUpdates?offset=100");
  const data = await res.json();
  assert.equal(data.ok, true);
});


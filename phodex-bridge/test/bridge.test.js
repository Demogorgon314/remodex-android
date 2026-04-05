// FILE: bridge.test.js
// Purpose: Verifies relay watchdog helpers used to recover from stale sleep/wake sockets.
// Layer: Unit test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, ../src/bridge

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildHeartbeatBridgeStatus,
  buildPublishedBridgeStatus,
  classifyBridgeTrafficMessageLabel,
  createBridgeTrafficStats,
  hasRelayConnectionGoneStale,
  rememberTrafficRequestMethod,
  sanitizeThreadHistoryImagesForRelay,
} = require("../src/bridge");

test("hasRelayConnectionGoneStale returns true once the relay silence crosses the timeout", () => {
  assert.equal(
    hasRelayConnectionGoneStale(1_000, {
      now: 71_000,
      staleAfterMs: 70_000,
    }),
    true
  );
});

test("hasRelayConnectionGoneStale returns false for fresh or missing activity timestamps", () => {
  assert.equal(
    hasRelayConnectionGoneStale(1_000, {
      now: 70_999,
      staleAfterMs: 70_000,
    }),
    false
  );
  assert.equal(hasRelayConnectionGoneStale(Number.NaN), false);
});

test("buildHeartbeatBridgeStatus downgrades stale connected snapshots", () => {
  assert.deepEqual(
    buildHeartbeatBridgeStatus(
      {
        state: "running",
        connectionStatus: "connected",
        pid: 123,
        lastError: "",
      },
      1_000,
      {
        now: 26_500,
        staleAfterMs: 25_000,
        staleMessage: "Relay heartbeat stalled; reconnect pending.",
      }
    ),
    {
      state: "running",
      connectionStatus: "disconnected",
      pid: 123,
      lastError: "Relay heartbeat stalled; reconnect pending.",
    }
  );
});

test("buildHeartbeatBridgeStatus leaves fresh or already-disconnected snapshots unchanged", () => {
  const freshStatus = {
    state: "running",
    connectionStatus: "connected",
    pid: 123,
    lastError: "",
  };
  assert.deepEqual(
    buildHeartbeatBridgeStatus(freshStatus, 1_000, {
      now: 20_000,
      staleAfterMs: 25_000,
    }),
    freshStatus
  );

  const disconnectedStatus = {
    state: "running",
    connectionStatus: "disconnected",
    pid: 123,
    lastError: "",
  };
  assert.deepEqual(buildHeartbeatBridgeStatus(disconnectedStatus, 1_000), disconnectedStatus);
});

test("buildPublishedBridgeStatus refreshes traffic snapshots during heartbeats", () => {
  const published = buildPublishedBridgeStatus(
    {
      state: "running",
      connectionStatus: "connected",
      pid: 123,
      lastError: "",
      traffic: {
        startedAt: "1970-01-01T00:00:00.000Z",
        updatedAt: "1970-01-01T00:00:00.000Z",
        channels: {},
      },
    },
    {
      startedAt: "1970-01-01T00:00:01.000Z",
      updatedAt: "1970-01-01T00:00:02.000Z",
      channels: {
        relayInboundWire: {
          messages: 2,
          bytes: 256,
          topLabels: [
            {
              label: "kind:encryptedEnvelope",
              messages: 2,
              bytes: 256,
            },
          ],
        },
      },
    },
    {
      lastActivityAt: 5_000,
      heartbeatOptions: {
        now: 20_000,
        staleAfterMs: 25_000,
      },
    }
  );

  assert.equal(published.connectionStatus, "connected");
  assert.equal(published.traffic.channels.relayInboundWire.bytes, 256);
  assert.equal(published.traffic.updatedAt, "1970-01-01T00:00:02.000Z");
});

test("sanitizeThreadHistoryImagesForRelay replaces inline history images with lightweight references", () => {
  const rawMessage = JSON.stringify({
    id: "req-thread-read",
    result: {
      thread: {
        id: "thread-images",
        turns: [
          {
            id: "turn-1",
            items: [
              {
                id: "item-user",
                type: "user_message",
                content: [
                  {
                    type: "input_text",
                    text: "Look at this screenshot",
                  },
                  {
                    type: "image",
                    image_url: "data:image/png;base64,AAAA",
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  });

  const sanitized = JSON.parse(
    sanitizeThreadHistoryImagesForRelay(rawMessage, "thread/read")
  );
  const content = sanitized.result.thread.turns[0].items[0].content;

  assert.deepEqual(content[0], {
    type: "input_text",
    text: "Look at this screenshot",
  });
  assert.deepEqual(content[1], {
    type: "image",
    url: "remodex://history-image-elided",
  });
});

test("sanitizeThreadHistoryImagesForRelay leaves unrelated RPC payloads unchanged", () => {
  const rawMessage = JSON.stringify({
    id: "req-other",
    result: {
      ok: true,
    },
  });

  assert.equal(
    sanitizeThreadHistoryImagesForRelay(rawMessage, "turn/start"),
    rawMessage
  );
});

test("bridge traffic classification attributes responses back to the original request method", () => {
  const applicationRequestMethodsById = new Map();
  rememberTrafficRequestMethod(
    applicationRequestMethodsById,
    JSON.stringify({
      id: "req-thread-read",
      method: "thread/read",
      params: {
        threadId: "thread-1",
      },
    })
  );

  assert.equal(
    classifyBridgeTrafficMessageLabel(JSON.stringify({
      id: "req-thread-read",
      result: {
        thread: {
          id: "thread-1",
        },
      },
    }), {
      applicationRequestMethodsById,
    }),
    "thread/read:response"
  );

  assert.equal(
    classifyBridgeTrafficMessageLabel(JSON.stringify({
      id: "bridge-managed-1",
      error: {
        message: "expired",
      },
    }), {
      bridgeManagedCodexRequestWaiters: new Map([
        [
          "bridge-managed-1",
          {
            method: "getAuthStatus",
          },
        ],
      ]),
    }),
    "bridge/getAuthStatus:error"
  );
});

test("bridge traffic stats keep per-channel totals plus top labels", () => {
  let currentTime = 1_000;
  const trafficStats = createBridgeTrafficStats({
    now() {
      return currentTime;
    },
  });

  trafficStats.record("relayOutboundApplication", JSON.stringify({
    id: "req-thread-read",
    result: {
      thread: {
        id: "thread-1",
      },
    },
  }), {
    label: "thread/read:response",
  });
  currentTime = 2_000;
  trafficStats.record("relayOutboundApplication", JSON.stringify({
    method: "turn/update",
    params: {
      delta: "ok",
    },
  }), {
    label: "turn/update",
  });
  currentTime = 3_000;
  trafficStats.record("codexInbound", JSON.stringify({
    kind: "encryptedEnvelope",
  }), {
    label: "kind:encryptedEnvelope",
  });

  const snapshot = trafficStats.snapshot();
  assert.equal(snapshot.startedAt, "1970-01-01T00:00:01.000Z");
  assert.equal(snapshot.updatedAt, "1970-01-01T00:00:03.000Z");
  assert.equal(snapshot.channels.relayOutboundApplication.messages, 2);
  assert.equal(
    snapshot.channels.relayOutboundApplication.topLabels[0].label,
    "thread/read:response"
  );
  assert.equal(snapshot.channels.codexInbound.topLabels[0].label, "kind:encryptedEnvelope");
});

// FILE: remodex-cli.test.js
// Purpose: Verifies the public CLI exposes version, service control, and machine-readable status output.
// Layer: Integration-lite test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, child_process, path, ../package.json, ../bin/remodex

const test = require("node:test");
const assert = require("node:assert/strict");
const { version } = require("../package.json");
const { main } = require("../bin/remodex");

test("remodex --version prints the package version", () => {
  const messages = [];

  return main({
    argv: ["node", "remodex", "--version"],
    consoleImpl: {
      log(message) {
        messages.push(message);
      },
      error(message) {
        throw new Error(`unexpected error: ${message}`);
      },
    },
    exitImpl(code) {
      throw new Error(`unexpected exit ${code}`);
    },
  }).finally(() => {
    assert.deepEqual(messages, [version]);
  });
});

test("remodex restart reuses the macOS service start flow", async () => {
  const calls = [];
  const messages = [];

  await main({
    argv: ["node", "remodex", "restart"],
    platform: "darwin",
    consoleImpl: {
      log(message) {
        messages.push(message);
      },
      error(message) {
        messages.push(message);
      },
    },
    exitImpl(code) {
      throw new Error(`unexpected exit ${code}`);
    },
    deps: {
      readBridgeConfig() {
        calls.push("read-config");
      },
      async startMacOSBridgeService(options) {
        calls.push(["start-service", options]);
        return {
          plistPath: "/tmp/remodex.plist",
          pairingSession: { relay: "ws://127.0.0.1:9000/relay" },
        };
      },
    },
  });

  assert.deepEqual(calls, [
    "read-config",
    ["start-service", { waitForPairing: false }],
  ]);
  assert.deepEqual(messages, [
    "[remodex] macOS bridge service restarted.",
  ]);
});

test("remodex status --json exposes daemon metadata for companion apps", async () => {
  const writes = [];
  const originalWrite = process.stdout.write;

  process.stdout.write = (chunk, encoding, callback) => {
    writes.push(String(chunk));
    if (typeof callback === "function") {
      callback();
    }
    return true;
  };

  try {
    await main({
      argv: ["node", "remodex", "status", "--json"],
      platform: "darwin",
      consoleImpl: {
        log() {},
        error(message) {
          throw new Error(`unexpected error: ${message}`);
        },
      },
      exitImpl(code) {
        throw new Error(`unexpected exit ${code}`);
      },
      deps: {
        getMacOSBridgeServiceStatus() {
          return {
            daemonConfig: {
              relayUrl: "ws://127.0.0.1:9000/relay",
            },
            bridgeStatus: {
              connectionStatus: "connected",
              pid: 77,
            },
            pairingSession: {
              pairingPayload: {
                relay: "ws://127.0.0.1:9000/relay",
                sessionId: "session-json",
              },
            },
          };
        },
        printMacOSBridgeServiceStatus() {
          throw new Error("status printer should not run for --json");
        },
      },
    });
  } finally {
    process.stdout.write = originalWrite;
  }

  const payload = JSON.parse(writes.join("").trim());
  assert.equal(payload.currentVersion, version);
  assert.equal(payload.daemonConfig?.relayUrl, "ws://127.0.0.1:9000/relay");
  assert.equal(payload.bridgeStatus?.connectionStatus, "connected");
  assert.equal(payload.pairingSession?.pairingPayload?.sessionId, "session-json");
});

test("remodex status --json exposes linux service metadata for companion apps", async () => {
  const writes = [];
  const originalWrite = process.stdout.write;

  process.stdout.write = (chunk, encoding, callback) => {
    writes.push(String(chunk));
    if (typeof callback === "function") {
      callback();
    }
    return true;
  };

  try {
    await main({
      argv: ["node", "remodex", "status", "--json"],
      platform: "linux",
      consoleImpl: {
        log() {},
        error(message) {
          throw new Error(`unexpected error: ${message}`);
        },
      },
      exitImpl(code) {
        throw new Error(`unexpected exit ${code}`);
      },
      deps: {
        getLinuxBridgeServiceStatus() {
          return {
            manager: "systemd-user",
            activeState: "active",
            daemonConfig: {
              relayUrl: "ws://127.0.0.1:9000/relay",
            },
            bridgeStatus: {
              connectionStatus: "connected",
              pid: 88,
            },
            pairingSession: {
              pairingPayload: {
                relay: "ws://127.0.0.1:9000/relay",
                sessionId: "linux-session-json",
              },
            },
          };
        },
        printLinuxBridgeServiceStatus() {
          throw new Error("status printer should not run for --json");
        },
      },
    });
  } finally {
    process.stdout.write = originalWrite;
  }

  const payload = JSON.parse(writes.join("").trim());
  assert.equal(payload.currentVersion, version);
  assert.equal(payload.manager, "systemd-user");
  assert.equal(payload.activeState, "active");
  assert.equal(payload.daemonConfig?.relayUrl, "ws://127.0.0.1:9000/relay");
  assert.equal(payload.bridgeStatus?.connectionStatus, "connected");
  assert.equal(payload.pairingSession?.pairingPayload?.sessionId, "linux-session-json");
});

test("remodex up on linux starts the managed service and prints the pairing qr", async () => {
  const calls = [];

  await main({
    argv: ["node", "remodex", "up"],
    platform: "linux",
    consoleImpl: {
      log() {},
      error(message) {
        throw new Error(`unexpected error: ${message}`);
      },
    },
    exitImpl(code) {
      throw new Error(`unexpected exit ${code}`);
    },
    deps: {
      async startLinuxBridgeService(options) {
        calls.push(["start-linux-service", options]);
        return {
          unitPath: "/tmp/com.remodex.bridge.service",
          pairingSession: {
            pairingPayload: {
              sessionId: "linux-up-session",
            },
          },
        };
      },
      printLinuxBridgePairingQr(options) {
        calls.push(["print-linux-qr", options]);
      },
    },
  });

  assert.deepEqual(calls, [
    ["start-linux-service", { waitForPairing: true }],
    ["print-linux-qr", {
      pairingSession: {
        pairingPayload: {
          sessionId: "linux-up-session",
        },
      },
    }],
  ]);
});

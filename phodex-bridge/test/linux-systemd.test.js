// FILE: linux-systemd.test.js
// Purpose: Verifies Linux systemd user-service generation and status helpers for the background Remodex bridge.
// Layer: Unit test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, fs, os, path, ../src/linux-systemd, ../src/daemon-state

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const {
  buildUserServiceUnit,
  getLinuxBridgeServiceStatus,
  resolveUserServiceUnitPath,
  stopLinuxBridgeService,
} = require("../src/linux-systemd");
const {
  readBridgeStatus,
  readPairingSession,
  writeBridgeStatus,
  writeDaemonConfig,
  writePairingSession,
} = require("../src/daemon-state");

test("buildUserServiceUnit points systemd at run-service with remodex state paths", () => {
  const unit = buildUserServiceUnit({
    homeDir: "/home/tester",
    pathEnv: "/usr/local/bin:/usr/bin",
    stateDir: "/home/tester/.remodex",
    stdoutLogPath: "/home/tester/.remodex/logs/bridge.stdout.log",
    stderrLogPath: "/home/tester/.remodex/logs/bridge.stderr.log",
    workingDirectory: "/tmp/remodex/phodex-bridge",
    nodePath: "/usr/bin/node",
    cliPath: "/tmp/remodex/phodex-bridge/bin/remodex.js",
  });

  assert.match(unit, /Description=Remodex bridge/);
  assert.match(unit, /"\/usr\/bin\/node" "\/tmp\/remodex\/phodex-bridge\/bin\/remodex\.js" "run-service"/);
  assert.match(unit, /Environment="REMODEX_DEVICE_STATE_DIR=\/home\/tester\/\.remodex"/);
  assert.match(unit, /WantedBy=default\.target/);
});

test("resolveUserServiceUnitPath writes into the user's systemd user folder", () => {
  assert.equal(
    resolveUserServiceUnitPath({
      env: { HOME: "/home/tester" },
      osImpl: { homedir: () => "/home/fallback" },
    }),
    path.join("/home/tester", ".config", "systemd", "user", "com.remodex.bridge.service")
  );
});

test("stopLinuxBridgeService clears stale pairing and status files", () => {
  withTempDaemonEnv(() => {
    writePairingSession({ sessionId: "session-1" });
    writeBridgeStatus({ state: "running", connectionStatus: "connected" });

    stopLinuxBridgeService({
      platform: "linux",
      execFileSyncImpl() {
        const error = new Error("Unit com.remodex.bridge.service could not be found.");
        error.stderr = Buffer.from("Unit com.remodex.bridge.service could not be found.");
        throw error;
      },
    });

    assert.equal(readPairingSession(), null);
    assert.equal(readBridgeStatus(), null);
  });
});

test("getLinuxBridgeServiceStatus reports systemd + runtime metadata together", () => {
  withTempDaemonEnv(({ rootDir }) => {
    writeDaemonConfig({ relayUrl: "ws://127.0.0.1:9000/relay" });
    writePairingSession({ sessionId: "session-2" });
    writeBridgeStatus({ state: "running", connectionStatus: "connected", pid: 55 });

    const unitPath = path.join(rootDir, ".config", "systemd", "user", "com.remodex.bridge.service");
    fs.mkdirSync(path.dirname(unitPath), { recursive: true });
    fs.writeFileSync(unitPath, "[Unit]\nDescription=Remodex bridge\n");

    const status = getLinuxBridgeServiceStatus({
      platform: "linux",
      env: { HOME: rootDir, REMODEX_DEVICE_STATE_DIR: rootDir },
      execFileSyncImpl() {
        return [
          "LoadState=loaded",
          "ActiveState=active",
          "SubState=running",
          "ExecMainPID=55",
          "UnitFileState=enabled",
          "",
        ].join("\n");
      },
    });

    assert.equal(status.installed, true);
    assert.equal(status.unitLoaded, true);
    assert.equal(status.activeState, "active");
    assert.equal(status.subState, "running");
    assert.equal(status.servicePid, 55);
    assert.equal(status.daemonConfig?.relayUrl, "ws://127.0.0.1:9000/relay");
    assert.equal(status.bridgeStatus?.connectionStatus, "connected");
    assert.equal(status.pairingSession?.pairingPayload?.sessionId, "session-2");
  });
});

function withTempDaemonEnv(run) {
  const previousDir = process.env.REMODEX_DEVICE_STATE_DIR;
  const previousHome = process.env.HOME;
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), "remodex-linux-systemd-"));
  process.env.REMODEX_DEVICE_STATE_DIR = rootDir;
  process.env.HOME = rootDir;

  try {
    return run({ rootDir });
  } finally {
    if (previousDir === undefined) {
      delete process.env.REMODEX_DEVICE_STATE_DIR;
    } else {
      process.env.REMODEX_DEVICE_STATE_DIR = previousDir;
    }
    if (previousHome === undefined) {
      delete process.env.HOME;
    } else {
      process.env.HOME = previousHome;
    }
    fs.rmSync(rootDir, { recursive: true, force: true });
  }
}

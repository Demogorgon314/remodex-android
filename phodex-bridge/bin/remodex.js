#!/usr/bin/env node
// FILE: remodex.js
// Purpose: CLI surface for foreground bridge runs, pairing reset, thread resume, and local service control.
// Layer: CLI binary
// Exports: none
// Depends on: ../src

const {
  getLinuxBridgeServiceStatus,
  getMacOSBridgeServiceStatus,
  printLinuxBridgePairingQr,
  printMacOSBridgePairingQr,
  printLinuxBridgeServiceStatus,
  printMacOSBridgeServiceStatus,
  readBridgeConfig,
  resetLinuxBridgePairing,
  resetMacOSBridgePairing,
  runLinuxBridgeService,
  runMacOSBridgeService,
  startBridge,
  startLinuxBridgeService,
  startMacOSBridgeService,
  stopLinuxBridgeService,
  stopMacOSBridgeService,
  resetBridgePairing,
  openLastActiveThread,
  watchThreadRollout,
} = require("../src");
const { version } = require("../package.json");

const defaultDeps = {
  getLinuxBridgeServiceStatus,
  getMacOSBridgeServiceStatus,
  printLinuxBridgePairingQr,
  printMacOSBridgePairingQr,
  printLinuxBridgeServiceStatus,
  printMacOSBridgeServiceStatus,
  readBridgeConfig,
  resetLinuxBridgePairing,
  resetMacOSBridgePairing,
  runLinuxBridgeService,
  runMacOSBridgeService,
  startBridge,
  startLinuxBridgeService,
  startMacOSBridgeService,
  stopLinuxBridgeService,
  stopMacOSBridgeService,
  resetBridgePairing,
  openLastActiveThread,
  watchThreadRollout,
};

if (require.main === module) {
  void main();
}

// ─── ENTRY POINT ─────────────────────────────────────────────

async function main({
  argv = process.argv,
  platform = process.platform,
  consoleImpl = console,
  exitImpl = process.exit,
  deps = defaultDeps,
} = {}) {
  const { command, jsonOutput, watchThreadId } = parseCliArgs(argv.slice(2));

  if (isVersionCommand(command)) {
    emitVersion({ jsonOutput, consoleImpl });
    return;
  }

  if (command === "up") {
    if (platform === "darwin") {
      const result = await deps.startMacOSBridgeService({
        waitForPairing: true,
      });
      deps.printMacOSBridgePairingQr({
        pairingSession: result.pairingSession,
      });
      return;
    }

    if (platform === "linux") {
      const result = await deps.startLinuxBridgeService({
        waitForPairing: true,
      });
      deps.printLinuxBridgePairingQr({
        pairingSession: result.pairingSession,
      });
      return;
    }

    deps.startBridge();
    return;
  }

  if (command === "run") {
    deps.startBridge();
    return;
  }

  if (command === "run-service") {
    if (platform === "darwin") {
      deps.runMacOSBridgeService();
      return;
    }

    if (platform === "linux") {
      deps.runLinuxBridgeService();
      return;
    }

    deps.startBridge();
    return;
  }

  if (command === "start") {
    deps.readBridgeConfig();
    const result = await startManagedBridgeService({
      commandName: command,
      platform,
      deps,
      consoleImpl,
      exitImpl,
    });
    emitResult({
      payload: {
        ok: true,
        currentVersion: version,
        platform,
        plistPath: result?.plistPath,
        unitPath: result?.unitPath,
        pairingSession: result?.pairingSession,
      },
      message: platform === "darwin"
        ? "[remodex] macOS bridge service is running."
        : "[remodex] Linux bridge service is running.",
      jsonOutput,
      consoleImpl,
    });
    return;
  }

  if (command === "restart") {
    deps.readBridgeConfig();
    const result = await startManagedBridgeService({
      commandName: command,
      platform,
      deps,
      consoleImpl,
      exitImpl,
    });
    emitResult({
      payload: {
        ok: true,
        currentVersion: version,
        platform,
        plistPath: result?.plistPath,
        unitPath: result?.unitPath,
        pairingSession: result?.pairingSession,
      },
      message: platform === "darwin"
        ? "[remodex] macOS bridge service restarted."
        : "[remodex] Linux bridge service restarted.",
      jsonOutput,
      consoleImpl,
    });
    return;
  }

  if (command === "stop") {
    stopManagedBridgeService({
      platform,
      deps,
      consoleImpl,
      exitImpl,
    });
    emitResult({
      payload: {
        ok: true,
        currentVersion: version,
        platform,
      },
      message: platform === "darwin"
        ? "[remodex] macOS bridge service stopped."
        : "[remodex] Linux bridge service stopped.",
      jsonOutput,
      consoleImpl,
    });
    return;
  }

  if (command === "status") {
    const status = getManagedBridgeServiceStatus({
      platform,
      deps,
      consoleImpl,
      exitImpl,
    });
    if (jsonOutput) {
      emitJson({
        ...status,
        currentVersion: version,
      });
      return;
    }
    printManagedBridgeServiceStatus({
      platform,
      deps,
      consoleImpl,
      exitImpl,
    });
    return;
  }

  if (command === "reset-pairing") {
    try {
      if (platform === "darwin") {
        deps.resetMacOSBridgePairing();
        emitResult({
          payload: {
            ok: true,
            currentVersion: version,
            platform: "darwin",
          },
          message: "[remodex] Stopped the macOS bridge service and cleared the saved pairing state. Run `remodex up` to pair again.",
          jsonOutput,
          consoleImpl,
        });
      } else if (platform === "linux") {
        deps.resetLinuxBridgePairing();
        emitResult({
          payload: {
            ok: true,
            currentVersion: version,
            platform,
          },
          message: "[remodex] Stopped the Linux bridge service and cleared the saved pairing state. Run `remodex up` to pair again.",
          jsonOutput,
          consoleImpl,
        });
      } else {
        deps.resetBridgePairing();
        emitResult({
          payload: {
            ok: true,
            currentVersion: version,
            platform,
          },
          message: "[remodex] Cleared the saved pairing state. Run `remodex up` to pair again.",
          jsonOutput,
          consoleImpl,
        });
      }
    } catch (error) {
      consoleImpl.error(`[remodex] ${(error && error.message) || "Failed to clear the saved pairing state."}`);
      exitImpl(1);
    }
    return;
  }

  if (command === "resume") {
    try {
      const state = deps.openLastActiveThread();
      emitResult({
        payload: {
          ok: true,
          currentVersion: version,
          threadId: state.threadId,
          source: state.source || "unknown",
        },
        message: `[remodex] Opened last active thread: ${state.threadId} (${state.source || "unknown"})`,
        jsonOutput,
        consoleImpl,
      });
    } catch (error) {
      consoleImpl.error(`[remodex] ${(error && error.message) || "Failed to reopen the last thread."}`);
      exitImpl(1);
    }
    return;
  }

  if (command === "watch") {
    try {
      deps.watchThreadRollout(watchThreadId);
    } catch (error) {
      consoleImpl.error(`[remodex] ${(error && error.message) || "Failed to watch the thread rollout."}`);
      exitImpl(1);
    }
    return;
  }

  consoleImpl.error(`Unknown command: ${command}`);
  consoleImpl.error(
    "Usage: remodex up | remodex run | remodex start | remodex restart | remodex stop | remodex status | "
    + "remodex reset-pairing | remodex resume | remodex watch [threadId] | remodex --version | "
    + "append --json to start/restart/stop/status/reset-pairing/resume for machine-readable output"
  );
  exitImpl(1);
}

function parseCliArgs(rawArgs) {
  const positionals = [];
  let jsonOutput = false;

  for (const arg of rawArgs) {
    if (arg === "--json") {
      jsonOutput = true;
      continue;
    }

    positionals.push(arg);
  }

  return {
    command: positionals[0] || "up",
    jsonOutput,
    watchThreadId: positionals[1] || "",
  };
}

function emitVersion({
  jsonOutput = false,
  consoleImpl = console,
} = {}) {
  if (jsonOutput) {
    emitJson({
      currentVersion: version,
    });
    return;
  }

  consoleImpl.log(version);
}

function emitResult({
  payload,
  message,
  jsonOutput = false,
  consoleImpl = console,
} = {}) {
  if (jsonOutput) {
    emitJson(payload);
    return;
  }

  consoleImpl.log(message);
}

function emitJson(payload) {
  process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
}

function assertMacOSCommand(name, {
  platform = process.platform,
  consoleImpl = console,
  exitImpl = process.exit,
} = {}) {
  if (platform === "darwin" || platform === "linux") {
    return;
  }

  consoleImpl.error(`[remodex] \`${name}\` is only available on macOS and Linux. Use \`remodex up\` or \`remodex run\` for the foreground bridge on this OS.`);
  exitImpl(1);
}

async function startManagedBridgeService({
  commandName = "start",
  platform = process.platform,
  deps = defaultDeps,
  consoleImpl = console,
  exitImpl = process.exit,
} = {}) {
  assertMacOSCommand(commandName, {
    platform,
    consoleImpl,
    exitImpl,
  });
  if (platform === "darwin") {
    return deps.startMacOSBridgeService({
      waitForPairing: false,
    });
  }
  return deps.startLinuxBridgeService({
    waitForPairing: false,
  });
}

function stopManagedBridgeService({
  platform = process.platform,
  deps = defaultDeps,
  consoleImpl = console,
  exitImpl = process.exit,
} = {}) {
  assertMacOSCommand("stop", {
    platform,
    consoleImpl,
    exitImpl,
  });
  if (platform === "darwin") {
    deps.stopMacOSBridgeService();
    return;
  }
  deps.stopLinuxBridgeService();
}

function getManagedBridgeServiceStatus({
  platform = process.platform,
  deps = defaultDeps,
  consoleImpl = console,
  exitImpl = process.exit,
} = {}) {
  assertMacOSCommand("status", {
    platform,
    consoleImpl,
    exitImpl,
  });
  if (platform === "darwin") {
    return deps.getMacOSBridgeServiceStatus();
  }
  return deps.getLinuxBridgeServiceStatus();
}

function printManagedBridgeServiceStatus({
  platform = process.platform,
  deps = defaultDeps,
  consoleImpl = console,
  exitImpl = process.exit,
} = {}) {
  assertMacOSCommand("status", {
    platform,
    consoleImpl,
    exitImpl,
  });
  if (platform === "darwin") {
    deps.printMacOSBridgeServiceStatus();
    return;
  }
  deps.printLinuxBridgeServiceStatus();
}

function isVersionCommand(value) {
  return value === "-v" || value === "--v" || value === "-V" || value === "--version" || value === "version";
}

module.exports = {
  isVersionCommand,
  main,
};

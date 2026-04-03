// FILE: linux-systemd.js
// Purpose: Owns Linux-only systemd user-service install/start/stop/status helpers for the background Remodex bridge.
// Layer: CLI helper
// Exports: start/stop/status helpers plus the systemd service runner used by `remodex up`.
// Depends on: child_process, fs, os, path, ./bridge, ./daemon-state, ./codex-desktop-refresher, ./qr, ./secure-device-state

const { execFileSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { startBridge } = require("./bridge");
const { readBridgeConfig } = require("./codex-desktop-refresher");
const { printQR } = require("./qr");
const { resetBridgeDeviceState } = require("./secure-device-state");
const {
  clearBridgeStatus,
  clearPairingSession,
  ensureRemodexLogsDir,
  ensureRemodexStateDir,
  readBridgeStatus,
  readDaemonConfig,
  readPairingSession,
  resolveBridgeStderrLogPath,
  resolveBridgeStdoutLogPath,
  resolveRemodexStateDir,
  writeBridgeStatus,
  writeDaemonConfig,
  writePairingSession,
} = require("./daemon-state");

const SERVICE_NAME = "com.remodex.bridge.service";
const DEFAULT_PAIRING_WAIT_TIMEOUT_MS = 10_000;
const DEFAULT_PAIRING_WAIT_INTERVAL_MS = 200;

function runLinuxBridgeService({ env = process.env, platform = process.platform } = {}) {
  assertLinuxPlatform(platform);
  const config = readDaemonConfig({ env });
  if (!config?.relayUrl) {
    const message = "No relay URL configured for the Linux bridge service.";
    clearPairingSession({ env });
    writeBridgeStatus({
      state: "error",
      connectionStatus: "error",
      pid: process.pid,
      lastError: message,
    }, { env });
    console.error(`[remodex] ${message}`);
    return;
  }

  startBridge({
    config,
    printPairingQr: false,
    onPairingPayload(pairingPayload) {
      writePairingSession(pairingPayload, { env });
    },
    onBridgeStatus(status) {
      writeBridgeStatus(status, { env });
    },
  });
}

async function startLinuxBridgeService({
  env = process.env,
  platform = process.platform,
  fsImpl = fs,
  execFileSyncImpl = execFileSync,
  osImpl = os,
  nodePath = process.execPath,
  cliPath = path.resolve(__dirname, "..", "bin", "remodex.js"),
  waitForPairing = false,
  pairingTimeoutMs = DEFAULT_PAIRING_WAIT_TIMEOUT_MS,
  pairingPollIntervalMs = DEFAULT_PAIRING_WAIT_INTERVAL_MS,
} = {}) {
  assertLinuxPlatform(platform);
  const config = readBridgeConfig({ env });
  assertRelayConfigured(config);
  const startedAt = Date.now();

  writeDaemonConfig(config, { env, fsImpl });
  clearPairingSession({ env, fsImpl });
  clearBridgeStatus({ env, fsImpl });
  ensureRemodexStateDir({ env, fsImpl, osImpl });
  ensureRemodexLogsDir({ env, fsImpl, osImpl });

  const unitPath = writeUserServiceUnit({
    env,
    fsImpl,
    osImpl,
    nodePath,
    cliPath,
  });

  runSystemctl(["--user", "daemon-reload"], { execFileSyncImpl });
  runSystemctl(["--user", "enable", SERVICE_NAME], { execFileSyncImpl });
  runSystemctl(["--user", "restart", SERVICE_NAME], { execFileSyncImpl });

  if (!waitForPairing) {
    return {
      unitPath,
      pairingSession: null,
    };
  }

  const pairingSession = await waitForFreshPairingSession({
    env,
    fsImpl,
    startedAt,
    timeoutMs: pairingTimeoutMs,
    intervalMs: pairingPollIntervalMs,
  });
  return {
    unitPath,
    pairingSession,
  };
}

function stopLinuxBridgeService({
  env = process.env,
  platform = process.platform,
  execFileSyncImpl = execFileSync,
  fsImpl = fs,
} = {}) {
  assertLinuxPlatform(platform);
  try {
    runSystemctl(["--user", "disable", "--now", SERVICE_NAME], {
      execFileSyncImpl,
    });
  } catch (error) {
    if (!isMissingSystemdUnitError(error)) {
      throw error;
    }
  }
  clearPairingSession({ env, fsImpl });
  clearBridgeStatus({ env, fsImpl });
}

function resetLinuxBridgePairing({
  env = process.env,
  platform = process.platform,
  execFileSyncImpl = execFileSync,
  fsImpl = fs,
  resetBridgePairingImpl = resetBridgeDeviceState,
} = {}) {
  assertLinuxPlatform(platform);
  stopLinuxBridgeService({
    env,
    platform,
    execFileSyncImpl,
    fsImpl,
  });
  return resetBridgePairingImpl();
}

function getLinuxBridgeServiceStatus({
  env = process.env,
  platform = process.platform,
  execFileSyncImpl = execFileSync,
  fsImpl = fs,
  osImpl = os,
} = {}) {
  assertLinuxPlatform(platform);
  const unitPath = resolveUserServiceUnitPath({ env, osImpl });
  const serviceState = readSystemdServiceState({
    execFileSyncImpl,
    missingAsStopped: true,
  });
  return {
    label: SERVICE_NAME,
    manager: "systemd-user",
    platform: "linux",
    installed: fsImpl.existsSync(unitPath),
    unitPath,
    unitLoaded: serviceState.loadState === "loaded",
    activeState: serviceState.activeState,
    subState: serviceState.subState,
    servicePid: serviceState.execMainPid,
    unitFileState: serviceState.unitFileState,
    daemonConfig: readDaemonConfig({ env, fsImpl }),
    bridgeStatus: readBridgeStatus({ env, fsImpl }),
    pairingSession: readPairingSession({ env, fsImpl }),
    stdoutLogPath: resolveBridgeStdoutLogPath({ env }),
    stderrLogPath: resolveBridgeStderrLogPath({ env }),
  };
}

function printLinuxBridgeServiceStatus(options = {}) {
  const status = getLinuxBridgeServiceStatus(options);
  const bridgeState = status.bridgeStatus?.state || "unknown";
  const connectionStatus = status.bridgeStatus?.connectionStatus || "unknown";
  const pairingCreatedAt = status.pairingSession?.createdAt || "none";
  console.log(`[remodex] Service label: ${status.label}`);
  console.log("[remodex] Service manager: systemd --user");
  console.log(`[remodex] Installed: ${status.installed ? "yes" : "no"}`);
  console.log(`[remodex] Loaded: ${status.unitLoaded ? "yes" : "no"}`);
  console.log(`[remodex] Active state: ${status.activeState}`);
  console.log(`[remodex] Sub-state: ${status.subState}`);
  console.log(`[remodex] PID: ${status.servicePid || status.bridgeStatus?.pid || "unknown"}`);
  console.log(`[remodex] Bridge state: ${bridgeState}`);
  console.log(`[remodex] Connection: ${connectionStatus}`);
  console.log(`[remodex] Pairing payload: ${pairingCreatedAt}`);
  console.log(`[remodex] Unit file: ${status.unitPath}`);
  console.log(`[remodex] Stdout log: ${status.stdoutLogPath}`);
  console.log(`[remodex] Stderr log: ${status.stderrLogPath}`);
}

function printLinuxBridgePairingQr({ pairingSession = null, env = process.env, fsImpl = fs } = {}) {
  const nextPairingSession = pairingSession || readPairingSession({ env, fsImpl });
  const pairingPayload = nextPairingSession?.pairingPayload;
  if (!pairingPayload) {
    throw new Error("The Linux bridge service did not publish a pairing payload yet.");
  }

  printQR(pairingPayload);
}

function writeUserServiceUnit({
  env = process.env,
  fsImpl = fs,
  osImpl = os,
  nodePath = process.execPath,
  cliPath = path.resolve(__dirname, "..", "bin", "remodex.js"),
} = {}) {
  const unitPath = resolveUserServiceUnitPath({ env, osImpl });
  const stateDir = resolveRemodexStateDir({ env, osImpl });
  const stdoutLogPath = resolveBridgeStdoutLogPath({ env, osImpl });
  const stderrLogPath = resolveBridgeStderrLogPath({ env, osImpl });
  const homeDir = env.HOME || osImpl.homedir();
  const workingDirectory = path.resolve(__dirname, "..");
  const serialized = buildUserServiceUnit({
    homeDir,
    pathEnv: env.PATH || "",
    stateDir,
    stdoutLogPath,
    stderrLogPath,
    workingDirectory,
    nodePath,
    cliPath,
  });

  fsImpl.mkdirSync(path.dirname(unitPath), { recursive: true });
  fsImpl.writeFileSync(unitPath, serialized, "utf8");
  return unitPath;
}

function buildUserServiceUnit({
  homeDir,
  pathEnv,
  stateDir,
  stdoutLogPath,
  stderrLogPath,
  workingDirectory,
  nodePath,
  cliPath,
}) {
  return `[Unit]
Description=Remodex bridge
After=default.target

[Service]
Type=simple
WorkingDirectory=${quoteSystemdPath(workingDirectory)}
ExecStart=${quoteSystemdExec(nodePath, cliPath, "run-service")}
Restart=on-failure
RestartSec=2
Environment=${quoteSystemdEnv("HOME", homeDir)}
Environment=${quoteSystemdEnv("PATH", pathEnv)}
Environment=${quoteSystemdEnv("REMODEX_DEVICE_STATE_DIR", stateDir)}
StandardOutput=append:${quoteSystemdPath(stdoutLogPath)}
StandardError=append:${quoteSystemdPath(stderrLogPath)}

[Install]
WantedBy=default.target
`;
}

function resolveUserServiceUnitPath({ env = process.env, osImpl = os } = {}) {
  const configHome = normalizeNonEmptyString(env.XDG_CONFIG_HOME)
    || path.join(env.HOME || osImpl.homedir(), ".config");
  return path.join(configHome, "systemd", "user", SERVICE_NAME);
}

function readSystemdServiceState({
  execFileSyncImpl = execFileSync,
  missingAsStopped = false,
} = {}) {
  try {
    const output = runSystemctl([
      "--user",
      "show",
      "--property=LoadState,ActiveState,SubState,ExecMainPID,UnitFileState",
      SERVICE_NAME,
    ], {
      execFileSyncImpl,
      encoding: "utf8",
    });
    return parseSystemdShowOutput(output);
  } catch (error) {
    if (missingAsStopped && isMissingSystemdUnitError(error)) {
      return {
        loadState: "not-found",
        activeState: "inactive",
        subState: "dead",
        execMainPid: null,
        unitFileState: "disabled",
      };
    }
    throw error;
  }
}

function runSystemctl(args, {
  execFileSyncImpl = execFileSync,
  encoding = "utf8",
} = {}) {
  try {
    return execFileSyncImpl("systemctl", args, {
      encoding,
      stdio: ["ignore", "pipe", "pipe"],
    });
  } catch (error) {
    if (error?.code === "ENOENT") {
      throw new Error("`systemctl --user` is required for Linux bridge service management.");
    }
    throw error;
  }
}

function parseSystemdShowOutput(output) {
  const values = Object.create(null);
  for (const line of String(output || "").split(/\r?\n/)) {
    if (!line || !line.includes("=")) {
      continue;
    }
    const separatorIndex = line.indexOf("=");
    const key = line.slice(0, separatorIndex);
    const value = line.slice(separatorIndex + 1);
    values[key] = value;
  }

  const pidValue = Number.parseInt(values.ExecMainPID || "", 10);
  return {
    loadState: values.LoadState || "unknown",
    activeState: values.ActiveState || "unknown",
    subState: values.SubState || "unknown",
    execMainPid: Number.isFinite(pidValue) && pidValue > 0 ? pidValue : null,
    unitFileState: values.UnitFileState || "unknown",
  };
}

async function waitForFreshPairingSession({
  env = process.env,
  fsImpl = fs,
  startedAt,
  timeoutMs,
  intervalMs,
} = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const pairingSession = readPairingSession({ env, fsImpl });
    const createdAt = Date.parse(pairingSession?.createdAt || "");
    if (pairingSession?.pairingPayload && Number.isFinite(createdAt) && createdAt >= startedAt) {
      return pairingSession;
    }
    await sleep(intervalMs);
  }

  throw new Error(
    `Timed out waiting for the Linux bridge service to publish a pairing QR. `
    + `Check ${resolveBridgeStdoutLogPath({ env })} and ${resolveBridgeStderrLogPath({ env })}.`
  );
}

function isMissingSystemdUnitError(error) {
  const combined = [
    error?.message,
    error?.stderr?.toString?.("utf8"),
    error?.stdout?.toString?.("utf8"),
  ].filter(Boolean).join("\n").toLowerCase();
  return combined.includes("unit com.remodex.bridge.service could not be found")
    || combined.includes("unit com.remodex.bridge.service not found")
    || combined.includes("not loaded");
}

function assertLinuxPlatform(platform = process.platform) {
  if (platform !== "linux") {
    throw new Error("Linux bridge service management is only available on Linux.");
  }
}

function assertRelayConfigured(config) {
  if (typeof config?.relayUrl === "string" && config.relayUrl.trim()) {
    return;
  }
  throw new Error("No relay URL configured. Run ./run-local-remodex.sh or set REMODEX_RELAY before enabling the Linux bridge service.");
}

function quoteSystemdValue(value) {
  return `"${String(value).replaceAll("\\", "\\\\").replaceAll("\"", "\\\"")}"`;
}

function quoteSystemdPath(value) {
  return String(value)
    .replaceAll("\\", "\\\\")
    .replaceAll(" ", "\\x20")
    .replaceAll(":", "\\:");
}

function quoteSystemdEnv(name, value) {
  return `"${String(name)}=${String(value).replaceAll("\\", "\\\\").replaceAll("\"", "\\\"")}"`;
}

function quoteSystemdExec(...parts) {
  return parts.map(quoteSystemdValue).join(" ");
}

function normalizeNonEmptyString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

module.exports = {
  buildUserServiceUnit,
  getLinuxBridgeServiceStatus,
  printLinuxBridgePairingQr,
  printLinuxBridgeServiceStatus,
  resetLinuxBridgePairing,
  resolveUserServiceUnitPath,
  runLinuxBridgeService,
  startLinuxBridgeService,
  stopLinuxBridgeService,
};

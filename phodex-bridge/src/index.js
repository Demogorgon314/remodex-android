// FILE: index.js
// Purpose: Small entrypoint wrapper for bridge lifecycle commands.
// Layer: CLI entry
// Exports: bridge lifecycle, pairing reset, thread resume/watch, and platform service helpers.
// Depends on: ./bridge, ./secure-device-state, ./session-state, ./rollout-watch, ./linux-systemd, ./macos-launch-agent

const { startBridge } = require("./bridge");
const { resetBridgeDeviceState } = require("./secure-device-state");
const { openLastActiveThread } = require("./session-state");
const { watchThreadRollout } = require("./rollout-watch");
const { readBridgeConfig } = require("./codex-desktop-refresher");
const {
  getLinuxBridgeServiceStatus,
  printLinuxBridgePairingQr,
  printLinuxBridgeServiceStatus,
  resetLinuxBridgePairing,
  runLinuxBridgeService,
  startLinuxBridgeService,
  stopLinuxBridgeService,
} = require("./linux-systemd");
const {
  getMacOSBridgeServiceStatus,
  printMacOSBridgePairingQr,
  printMacOSBridgeServiceStatus,
  resetMacOSBridgePairing,
  runMacOSBridgeService,
  startMacOSBridgeService,
  stopMacOSBridgeService,
} = require("./macos-launch-agent");

module.exports = {
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
  startBridge,
  runMacOSBridgeService,
  startLinuxBridgeService,
  startMacOSBridgeService,
  stopLinuxBridgeService,
  stopMacOSBridgeService,
  resetBridgePairing: resetBridgeDeviceState,
  openLastActiveThread,
  watchThreadRollout,
};

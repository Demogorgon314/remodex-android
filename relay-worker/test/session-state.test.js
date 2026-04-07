import test from "node:test";
import assert from "node:assert/strict";

import {
  CLOSE_CODE_IPHONE_REPLACED,
  CLOSE_CODE_SESSION_UNAVAILABLE,
  MAC_ABSENCE_GRACE_MS,
  closeConnection,
  connectIphone,
  connectMac,
  createSessionSnapshot,
  expireMacAbsenceIfNeeded,
  updateMacRegistration,
} from "../src/session/state.js";

test("session state accepts mac first and then iphone", () => {
  const baseSnapshot = createSessionSnapshot({ sessionId: "session-1" });
  const macConnected = connectMac(baseSnapshot, {
    connectionId: "mac-a",
    notificationSecret: "secret-a",
    registration: {
      macDeviceId: "mac-1",
      macIdentityPublicKey: "mac-public",
      trustedPhoneDeviceId: "phone-1",
      trustedPhonePublicKey: "phone-public",
    },
  });
  const iphoneConnected = connectIphone(macConnected.snapshot, {
    connectionId: "iphone-a",
    now: 1_000,
  });

  assert.equal(macConnected.snapshot.macConnectionId, "mac-a");
  assert.equal(iphoneConnected.snapshot.iphoneConnectionId, "iphone-a");
  assert.equal(iphoneConnected.reject, undefined);
});

test("session state replaces the previous iphone connection", () => {
  const macConnected = connectMac(createSessionSnapshot({ sessionId: "session-2" }), {
    connectionId: "mac-a",
  });
  const firstIphone = connectIphone(macConnected.snapshot, {
    connectionId: "iphone-a",
    now: 1_000,
  });
  const secondIphone = connectIphone(firstIphone.snapshot, {
    connectionId: "iphone-b",
    now: 1_100,
  });

  assert.equal(secondIphone.snapshot.iphoneConnectionId, "iphone-b");
  assert.ok(
    secondIphone.effects.some((effect) => (
      effect.type === "close_role"
      && effect.role === "iphone"
      && effect.code === CLOSE_CODE_IPHONE_REPLACED
    ))
  );
});

test("session state enters a grace window when the mac disconnects", () => {
  const macConnected = connectMac(createSessionSnapshot({ sessionId: "session-3" }), {
    connectionId: "mac-a",
    notificationSecret: "secret-a",
  });
  const iphoneConnected = connectIphone(macConnected.snapshot, {
    connectionId: "iphone-a",
    now: 1_000,
  });
  const macClosed = closeConnection(iphoneConnected.snapshot, {
    role: "mac",
    connectionId: "mac-a",
    now: 2_000,
  });

  assert.equal(macClosed.snapshot.macConnectionId, null);
  assert.equal(macClosed.snapshot.iphoneConnectionId, "iphone-a");
  assert.equal(macClosed.snapshot.macAbsenceDeadline, 2_000 + MAC_ABSENCE_GRACE_MS);
});

test("session state expires the grace window and closes iphone sockets", () => {
  const expired = expireMacAbsenceIfNeeded(createSessionSnapshot({
    sessionId: "session-4",
    iphoneConnectionId: "iphone-a",
    notificationSecret: "secret-a",
    macAbsenceDeadline: 1_500,
  }), {
    now: 1_500,
  });

  assert.equal(expired.snapshot.iphoneConnectionId, null);
  assert.equal(expired.snapshot.notificationSecret, null);
  assert.ok(
    expired.effects.some((effect) => (
      effect.type === "close_role"
      && effect.role === "iphone"
      && effect.code === CLOSE_CODE_SESSION_UNAVAILABLE
    ))
  );
});

test("session state publishes updated trusted-phone registration changes", () => {
  const connected = connectMac(createSessionSnapshot({ sessionId: "session-5" }), {
    connectionId: "mac-a",
    registration: {
      macDeviceId: "mac-5",
      macIdentityPublicKey: "mac-public",
    },
  });
  const updated = updateMacRegistration(connected.snapshot, {
    macDeviceId: "mac-5",
    macIdentityPublicKey: "mac-public",
    trustedPhoneDeviceId: "phone-5",
    trustedPhonePublicKey: "phone-public",
  });

  assert.equal(updated.snapshot.registration.trustedPhoneDeviceId, "phone-5");
  assert.ok(updated.effects.some((effect) => effect.type === "registry_upsert"));
});

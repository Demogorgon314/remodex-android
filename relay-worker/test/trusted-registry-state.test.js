import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";

import {
  createTrustedRegistrySnapshot,
  removeLiveSessionIfSessionMatches,
  resolveTrustedSession,
  upsertLiveSession,
} from "../src/registry/state.js";
import { buildTrustedSessionResolveBytes } from "../src/protocol/trusted-session.js";

function makePhoneIdentity() {
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const publicJwk = publicKey.export({ format: "jwk" });
  return {
    phoneDeviceId: "phone-1",
    phoneIdentityPublicKey: publicJwk.x,
    privateKey,
  };
}

function makeResolveBody({
  macDeviceId,
  phoneIdentity,
  nonce,
  timestamp,
}) {
  const transcriptBytes = buildTrustedSessionResolveBytes({
    macDeviceId,
    phoneDeviceId: phoneIdentity.phoneDeviceId,
    phoneIdentityPublicKey: phoneIdentity.phoneIdentityPublicKey,
    nonce,
    timestamp,
  });
  return {
    macDeviceId,
    phoneDeviceId: phoneIdentity.phoneDeviceId,
    phoneIdentityPublicKey: phoneIdentity.phoneIdentityPublicKey,
    nonce,
    timestamp,
    signature: sign(null, transcriptBytes, phoneIdentity.privateKey).toString("base64"),
  };
}

test("trusted registry resolves the live session for a trusted iphone", async () => {
  const phoneIdentity = makePhoneIdentity();
  const snapshot = upsertLiveSession(createTrustedRegistrySnapshot(), {
    sessionId: "live-session-1",
    macDeviceId: "mac-1",
    macIdentityPublicKey: "mac-public-key-1",
    displayName: "Emanuele-Mac",
    trustedPhoneDeviceId: phoneIdentity.phoneDeviceId,
    trustedPhonePublicKey: phoneIdentity.phoneIdentityPublicKey,
  });

  const result = await resolveTrustedSession(
    snapshot,
    makeResolveBody({
      macDeviceId: "mac-1",
      phoneIdentity,
      nonce: "nonce-1",
      timestamp: Date.now(),
    }),
    {
      hasActiveSession: () => true,
    }
  );

  assert.deepEqual(result.response, {
    ok: true,
    macDeviceId: "mac-1",
    macIdentityPublicKey: "mac-public-key-1",
    displayName: "Emanuele-Mac",
    sessionId: "live-session-1",
  });
});

test("trusted registry rejects replayed nonces", async () => {
  const phoneIdentity = makePhoneIdentity();
  const body = makeResolveBody({
    macDeviceId: "mac-2",
    phoneIdentity,
    nonce: "nonce-2",
    timestamp: Date.now(),
  });
  let snapshot = upsertLiveSession(createTrustedRegistrySnapshot(), {
    sessionId: "live-session-2",
    macDeviceId: "mac-2",
    macIdentityPublicKey: "mac-public-key-2",
    trustedPhoneDeviceId: phoneIdentity.phoneDeviceId,
    trustedPhonePublicKey: phoneIdentity.phoneIdentityPublicKey,
  });

  const first = await resolveTrustedSession(snapshot, body, {
    hasActiveSession: () => true,
  });
  snapshot = first.snapshot;

  await assert.rejects(
    () => resolveTrustedSession(snapshot, body, {
      hasActiveSession: () => true,
    }),
    (error) => error.code === "resolve_request_replayed"
  );
});

test("trusted registry removes the live session only when the session id matches", () => {
  const initial = upsertLiveSession(createTrustedRegistrySnapshot(), {
    sessionId: "live-session-3",
    macDeviceId: "mac-3",
    macIdentityPublicKey: "mac-public-key-3",
  });

  const preserved = removeLiveSessionIfSessionMatches(initial, {
    sessionId: "other-session",
  });
  const removed = removeLiveSessionIfSessionMatches(initial, {
    sessionId: "live-session-3",
  });

  assert.equal(preserved.liveSession.sessionId, "live-session-3");
  assert.equal(removed.liveSession, null);
});

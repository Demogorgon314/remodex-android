import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";

import {
  buildTrustedSessionResolveBytes,
  verifyTrustedSessionResolveSignature,
} from "../src/protocol/trusted-session.js";

test("trusted-session transcript is length-prefixed and deterministic", () => {
  const bytes = buildTrustedSessionResolveBytes({
    macDeviceId: "mac-1",
    phoneDeviceId: "phone-1",
    phoneIdentityPublicKey: "QUJDRA==",
    nonce: "nonce-1",
    timestamp: 12345,
  });

  assert.equal(bytes.length, 86);
  assert.deepEqual(
    Array.from(bytes.slice(0, 12)),
    [0, 0, 0, 34, 114, 101, 109, 111, 100, 101, 120, 45]
  );
});

test("trusted-session signature verification accepts a valid Ed25519 signature", async () => {
  const { publicKey, privateKey } = generateKeyPairSync("ed25519");
  const publicJwk = publicKey.export({ format: "jwk" });
  const transcriptBytes = buildTrustedSessionResolveBytes({
    macDeviceId: "mac-2",
    phoneDeviceId: "phone-2",
    phoneIdentityPublicKey: publicJwk.x,
    nonce: "nonce-2",
    timestamp: Date.now(),
  });
  const signature = sign(null, transcriptBytes, privateKey).toString("base64");

  assert.equal(
    await verifyTrustedSessionResolveSignature(publicJwk.x, transcriptBytes, signature),
    true
  );
});

test("trusted-session signature verification rejects invalid signatures", async () => {
  const { publicKey } = generateKeyPairSync("ed25519");
  const publicJwk = publicKey.export({ format: "jwk" });
  const transcriptBytes = buildTrustedSessionResolveBytes({
    macDeviceId: "mac-3",
    phoneDeviceId: "phone-3",
    phoneIdentityPublicKey: publicJwk.x,
    nonce: "nonce-3",
    timestamp: Date.now(),
  });

  assert.equal(
    await verifyTrustedSessionResolveSignature(publicJwk.x, transcriptBytes, "AAAA"),
    false
  );
});

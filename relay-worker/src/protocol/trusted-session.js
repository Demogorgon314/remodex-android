import { normalizeNonEmptyString } from "../common.js";

export const TRUSTED_SESSION_RESOLVE_TAG = "remodex-trusted-session-resolve-v1";
export const TRUSTED_SESSION_RESOLVE_SKEW_MS = 90_000;

export function createRelayError(status, code, message) {
  return Object.assign(new Error(message), {
    status,
    code,
  });
}

export function buildTrustedSessionResolveBytes({
  macDeviceId,
  phoneDeviceId,
  phoneIdentityPublicKey,
  nonce,
  timestamp,
}) {
  const publicKeyBytes = decodeBase64ToBytes(phoneIdentityPublicKey);
  if (!publicKeyBytes) {
    return new Uint8Array();
  }

  return concatBytes([
    encodeLengthPrefixedUTF8(TRUSTED_SESSION_RESOLVE_TAG),
    encodeLengthPrefixedUTF8(macDeviceId),
    encodeLengthPrefixedUTF8(phoneDeviceId),
    encodeLengthPrefixedData(publicKeyBytes),
    encodeLengthPrefixedUTF8(nonce),
    encodeLengthPrefixedUTF8(String(timestamp)),
  ]);
}

export async function verifyTrustedSessionResolveSignature(
  publicKeyBase64,
  transcriptBytes,
  signatureBase64
) {
  try {
    const signatureBytes = decodeBase64ToBytes(signatureBase64);
    if (!signatureBytes || !transcriptBytes?.length) {
      return false;
    }

    const key = await crypto.subtle.importKey(
      "jwk",
      {
        crv: "Ed25519",
        kty: "OKP",
        x: base64ToBase64Url(publicKeyBase64),
      },
      {
        name: "Ed25519",
      },
      false,
      ["verify"]
    );

    return await crypto.subtle.verify(
      "Ed25519",
      key,
      signatureBytes,
      transcriptBytes
    );
  } catch {
    return false;
  }
}

export function normalizeMacRegistration(registration, sessionId = "") {
  return {
    sessionId: normalizeNonEmptyString(sessionId),
    macDeviceId: normalizeNonEmptyString(registration?.macDeviceId),
    macIdentityPublicKey: normalizeNonEmptyString(registration?.macIdentityPublicKey),
    displayName: normalizeNonEmptyString(registration?.displayName),
    trustedPhoneDeviceId: normalizeNonEmptyString(registration?.trustedPhoneDeviceId),
    trustedPhonePublicKey: normalizeNonEmptyString(registration?.trustedPhonePublicKey),
  };
}

export function base64ToBase64Url(value) {
  return String(value || "")
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/g, "");
}

function encodeLengthPrefixedUTF8(value) {
  return encodeLengthPrefixedData(new TextEncoder().encode(String(value)));
}

function encodeLengthPrefixedData(value) {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value);
  const output = new Uint8Array(4 + bytes.length);
  new DataView(output.buffer).setUint32(0, bytes.length);
  output.set(bytes, 4);
  return output;
}

function concatBytes(chunks) {
  const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const output = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    output.set(chunk, offset);
    offset += chunk.length;
  }
  return output;
}

function decodeBase64ToBytes(value) {
  const normalized = normalizeNonEmptyString(value);
  if (!normalized) {
    return null;
  }

  try {
    const base64 = normalized
      .replaceAll("-", "+")
      .replaceAll("_", "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const binary = atob(padded);
    const output = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      output[index] = binary.charCodeAt(index);
    }
    return output;
  } catch {
    return null;
  }
}

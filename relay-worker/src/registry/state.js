import {
  TRUSTED_SESSION_RESOLVE_SKEW_MS,
  buildTrustedSessionResolveBytes,
  createRelayError,
  normalizeMacRegistration,
  verifyTrustedSessionResolveSignature,
} from "../protocol/trusted-session.js";
import { normalizeNonEmptyString } from "../common.js";

export function createTrustedRegistrySnapshot(snapshot = {}) {
  return {
    liveSession: normalizeLiveSession(snapshot.liveSession),
    usedResolveNonces: normalizeNonceMap(snapshot.usedResolveNonces),
  };
}

export function upsertLiveSession(snapshot, registration) {
  const normalizedRegistration = normalizeLiveSession(registration);
  if (!normalizedRegistration.macDeviceId) {
    return createTrustedRegistrySnapshot(snapshot);
  }

  return {
    ...createTrustedRegistrySnapshot(snapshot),
    liveSession: normalizedRegistration,
  };
}

export function removeLiveSessionIfSessionMatches(snapshot, { sessionId } = {}) {
  const normalizedSnapshot = createTrustedRegistrySnapshot(snapshot);
  const normalizedSessionId = normalizeNonEmptyString(sessionId);
  if (!normalizedSessionId || normalizedSnapshot.liveSession?.sessionId !== normalizedSessionId) {
    return normalizedSnapshot;
  }

  return {
    ...normalizedSnapshot,
    liveSession: null,
  };
}

export async function resolveTrustedSession(
  snapshot,
  {
    macDeviceId,
    phoneDeviceId,
    phoneIdentityPublicKey,
    timestamp,
    nonce,
    signature,
  } = {},
  {
    now = Date.now(),
    hasActiveSession = () => false,
  } = {}
) {
  const normalizedSnapshot = createTrustedRegistrySnapshot(snapshot);
  const normalizedMacDeviceId = normalizeNonEmptyString(macDeviceId);
  const normalizedPhoneDeviceId = normalizeNonEmptyString(phoneDeviceId);
  const normalizedPhoneIdentityPublicKey = normalizeNonEmptyString(phoneIdentityPublicKey);
  const normalizedNonce = normalizeNonEmptyString(nonce);
  const normalizedSignature = normalizeNonEmptyString(signature);
  const normalizedTimestamp = Number(timestamp);

  if (
    !normalizedMacDeviceId
    || !normalizedPhoneDeviceId
    || !normalizedPhoneIdentityPublicKey
    || !normalizedNonce
    || !normalizedSignature
    || !Number.isFinite(normalizedTimestamp)
  ) {
    throw createRelayError(400, "invalid_request", "The trusted-session resolve request is missing required fields.");
  }

  if (Math.abs(now - normalizedTimestamp) > TRUSTED_SESSION_RESOLVE_SKEW_MS) {
    throw createRelayError(401, "resolve_request_expired", "This trusted-session resolve request has expired.");
  }

  const prunedSnapshot = pruneUsedResolveNonces(normalizedSnapshot, now);
  const liveSession = prunedSnapshot.liveSession;
  if (!liveSession || liveSession.macDeviceId !== normalizedMacDeviceId) {
    throw createRelayError(404, "session_unavailable", "The trusted Mac is offline right now.");
  }

  if (!await hasActiveSession(liveSession.sessionId)) {
    throw createRelayError(404, "session_unavailable", "The trusted Mac is offline right now.");
  }

  if (
    liveSession.trustedPhoneDeviceId !== normalizedPhoneDeviceId
    || liveSession.trustedPhonePublicKey !== normalizedPhoneIdentityPublicKey
  ) {
    throw createRelayError(403, "phone_not_trusted", "This iPhone is not trusted for the requested Mac.");
  }

  const nonceKey = `${normalizedPhoneDeviceId}|${normalizedNonce}`;
  if (prunedSnapshot.usedResolveNonces[nonceKey]) {
    throw createRelayError(409, "resolve_request_replayed", "This trusted-session resolve request was already used.");
  }

  const transcriptBytes = buildTrustedSessionResolveBytes({
    macDeviceId: normalizedMacDeviceId,
    phoneDeviceId: normalizedPhoneDeviceId,
    phoneIdentityPublicKey: normalizedPhoneIdentityPublicKey,
    nonce: normalizedNonce,
    timestamp: normalizedTimestamp,
  });
  const isValidSignature = await verifyTrustedSessionResolveSignature(
    normalizedPhoneIdentityPublicKey,
    transcriptBytes,
    normalizedSignature
  );
  if (!isValidSignature) {
    throw createRelayError(403, "invalid_signature", "The trusted-session resolve signature is invalid.");
  }

  return {
    snapshot: {
      ...prunedSnapshot,
      usedResolveNonces: {
        ...prunedSnapshot.usedResolveNonces,
        [nonceKey]: now + TRUSTED_SESSION_RESOLVE_SKEW_MS,
      },
    },
    response: {
      ok: true,
      macDeviceId: normalizedMacDeviceId,
      macIdentityPublicKey: liveSession.macIdentityPublicKey,
      displayName: liveSession.displayName || null,
      sessionId: liveSession.sessionId,
    },
  };
}

function pruneUsedResolveNonces(snapshot, now) {
  const nextNonces = {};
  for (const [nonceKey, expiresAt] of Object.entries(snapshot.usedResolveNonces)) {
    if (Number(expiresAt) > now) {
      nextNonces[nonceKey] = Number(expiresAt);
    }
  }

  return {
    ...snapshot,
    usedResolveNonces: nextNonces,
  };
}

function normalizeLiveSession(value) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const normalized = normalizeMacRegistration(value, value.sessionId);
  return normalized.macDeviceId ? normalized : null;
}

function normalizeNonceMap(value) {
  if (!value || typeof value !== "object") {
    return {};
  }

  return Object.fromEntries(
    Object.entries(value)
      .filter(([, expiresAt]) => Number.isFinite(Number(expiresAt)))
      .map(([key, expiresAt]) => [key, Number(expiresAt)])
  );
}

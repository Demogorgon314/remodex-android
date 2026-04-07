import { normalizeMacRegistration } from "../protocol/trusted-session.js";
import { normalizeNonEmptyString, readHeaderString } from "../common.js";

export const CLOSE_CODE_INVALID_SESSION = 4000;
export const CLOSE_CODE_MAC_REPLACED = 4001;
export const CLOSE_CODE_SESSION_UNAVAILABLE = 4002;
export const CLOSE_CODE_IPHONE_REPLACED = 4003;
export const CLOSE_CODE_MAC_ABSENCE_BUFFER_FULL = 4004;
export const MAC_ABSENCE_GRACE_MS = 15_000;

export function createSessionSnapshot(snapshot = {}) {
  return {
    sessionId: normalizeNonEmptyString(snapshot.sessionId),
    macConnectionId: normalizeNonEmptyString(snapshot.macConnectionId) || null,
    iphoneConnectionId: normalizeNonEmptyString(snapshot.iphoneConnectionId) || null,
    registration: normalizeLiveRegistration(snapshot.registration, snapshot.sessionId),
    notificationSecret: normalizeNonEmptyString(snapshot.notificationSecret) || null,
    macAbsenceDeadline: Number(snapshot.macAbsenceDeadline || 0),
  };
}

export function bindSessionId(snapshot, sessionId) {
  return {
    ...createSessionSnapshot(snapshot),
    sessionId: normalizeNonEmptyString(sessionId),
  };
}

export function connectMac(
  snapshot,
  {
    connectionId,
    registration,
    notificationSecret,
  } = {}
) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  const normalizedConnectionId = normalizeNonEmptyString(connectionId);
  const normalizedRegistration = normalizeLiveRegistration(
    registration,
    normalizedSnapshot.sessionId
  );
  const nextSnapshot = {
    ...normalizedSnapshot,
    macConnectionId: normalizedConnectionId,
    registration: normalizedRegistration,
    notificationSecret: normalizeNonEmptyString(notificationSecret) || null,
    macAbsenceDeadline: 0,
  };

  return {
    snapshot: nextSnapshot,
    effects: [
      {
        type: "close_role",
        role: "mac",
        exceptConnectionId: normalizedConnectionId,
        code: CLOSE_CODE_MAC_REPLACED,
        reason: "Replaced by new Mac connection",
      },
      ...registrationEffects(normalizedSnapshot.registration, normalizedRegistration, normalizedSnapshot.sessionId),
      {
        type: "delete_alarm",
      },
    ],
  };
}

export function connectIphone(
  snapshot,
  {
    connectionId,
    now = Date.now(),
  } = {}
) {
  const expired = expireMacAbsenceIfNeeded(snapshot, { now });
  const normalizedSnapshot = expired.snapshot;
  if (!canAcceptIphoneConnection(normalizedSnapshot, now)) {
    return {
      snapshot: normalizedSnapshot,
      effects: expired.effects,
      reject: {
        code: CLOSE_CODE_SESSION_UNAVAILABLE,
        reason: "Mac session not available",
      },
    };
  }

  return {
    snapshot: {
      ...normalizedSnapshot,
      iphoneConnectionId: normalizeNonEmptyString(connectionId),
    },
    effects: [
      ...expired.effects,
      {
        type: "close_role",
        role: "iphone",
        exceptConnectionId: normalizeNonEmptyString(connectionId),
        code: CLOSE_CODE_IPHONE_REPLACED,
        reason: "Replaced by newer iPhone connection",
      },
    ],
  };
}

export function updateMacRegistration(snapshot, registration) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  const normalizedRegistration = normalizeLiveRegistration(
    registration,
    normalizedSnapshot.sessionId
  );

  return {
    snapshot: {
      ...normalizedSnapshot,
      registration: normalizedRegistration,
    },
    effects: registrationEffects(
      normalizedSnapshot.registration,
      normalizedRegistration,
      normalizedSnapshot.sessionId
    ),
  };
}

export function closeConnection(
  snapshot,
  {
    role,
    connectionId,
    now = Date.now(),
  } = {}
) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  const normalizedConnectionId = normalizeNonEmptyString(connectionId);

  if (role === "mac") {
    if (!normalizedConnectionId || normalizedSnapshot.macConnectionId !== normalizedConnectionId) {
      return {
        snapshot: normalizedSnapshot,
        effects: [],
      };
    }

    if (normalizedSnapshot.iphoneConnectionId) {
      const macAbsenceDeadline = now + MAC_ABSENCE_GRACE_MS;
      return {
        snapshot: {
          ...normalizedSnapshot,
          macConnectionId: null,
          macAbsenceDeadline,
        },
        effects: [
          ...removalEffects(normalizedSnapshot.registration, normalizedSnapshot.sessionId),
          {
            type: "set_alarm",
            scheduledTime: macAbsenceDeadline,
          },
        ],
      };
    }

    return {
      snapshot: {
        ...normalizedSnapshot,
        macConnectionId: null,
        notificationSecret: null,
        macAbsenceDeadline: 0,
      },
      effects: [
        ...removalEffects(normalizedSnapshot.registration, normalizedSnapshot.sessionId),
        {
          type: "delete_alarm",
        },
      ],
    };
  }

  if (role === "iphone") {
    if (!normalizedConnectionId || normalizedSnapshot.iphoneConnectionId !== normalizedConnectionId) {
      return {
        snapshot: normalizedSnapshot,
        effects: [],
      };
    }

    if (!normalizedSnapshot.macConnectionId) {
      return {
        snapshot: {
          ...normalizedSnapshot,
          iphoneConnectionId: null,
          notificationSecret: null,
          macAbsenceDeadline: 0,
        },
        effects: [
          {
            type: "delete_alarm",
          },
        ],
      };
    }

    return {
      snapshot: {
        ...normalizedSnapshot,
        iphoneConnectionId: null,
      },
      effects: [],
    };
  }

  return {
    snapshot: normalizedSnapshot,
    effects: [],
  };
}

export function expireMacAbsenceIfNeeded(
  snapshot,
  {
    now = Date.now(),
  } = {}
) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  if (
    normalizedSnapshot.macConnectionId
    || !normalizedSnapshot.macAbsenceDeadline
    || normalizedSnapshot.macAbsenceDeadline > now
  ) {
    return {
      snapshot: normalizedSnapshot,
      effects: [],
    };
  }

  return {
    snapshot: {
      ...normalizedSnapshot,
      iphoneConnectionId: null,
      notificationSecret: null,
      macAbsenceDeadline: 0,
    },
    effects: [
      {
        type: "close_role",
        role: "iphone",
        code: CLOSE_CODE_SESSION_UNAVAILABLE,
        reason: "Mac disconnected",
      },
      {
        type: "delete_alarm",
      },
    ],
  };
}

export function hasActiveMacSession(snapshot) {
  return Boolean(createSessionSnapshot(snapshot).macConnectionId);
}

export function hasAuthenticatedMacSession(snapshot, notificationSecret) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  return (
    Boolean(normalizedSnapshot.macConnectionId)
    && normalizedSnapshot.notificationSecret === (normalizeNonEmptyString(notificationSecret) || null)
  );
}

export function canAcceptIphoneConnection(snapshot, now = Date.now()) {
  const normalizedSnapshot = createSessionSnapshot(snapshot);
  return Boolean(
    normalizedSnapshot.macConnectionId
    || (normalizedSnapshot.macAbsenceDeadline && normalizedSnapshot.macAbsenceDeadline > now)
  );
}

export function readMacRegistrationHeaders(headers, sessionId) {
  return normalizeLiveRegistration({
    macDeviceId: readHeaderString(headers.get("x-mac-device-id")),
    macIdentityPublicKey: readHeaderString(headers.get("x-mac-identity-public-key")),
    displayName: readHeaderString(headers.get("x-machine-name")),
    trustedPhoneDeviceId: readHeaderString(headers.get("x-trusted-phone-device-id")),
    trustedPhonePublicKey: readHeaderString(headers.get("x-trusted-phone-public-key")),
  }, sessionId);
}

function normalizeLiveRegistration(value, sessionId) {
  const normalized = normalizeMacRegistration(value, sessionId);
  return normalized.macDeviceId ? normalized : null;
}

function registrationEffects(previousRegistration, nextRegistration, sessionId) {
  return [
    ...removalEffects(previousRegistration, sessionId, nextRegistration?.macDeviceId),
    ...upsertEffects(nextRegistration),
  ];
}

function removalEffects(previousRegistration, sessionId, nextMacDeviceId = "") {
  if (!previousRegistration?.macDeviceId) {
    return [];
  }

  if (previousRegistration.macDeviceId === normalizeNonEmptyString(nextMacDeviceId)) {
    return [];
  }

  return [{
    type: "registry_remove",
    macDeviceId: previousRegistration.macDeviceId,
    sessionId,
  }];
}

function upsertEffects(nextRegistration) {
  if (!nextRegistration?.macDeviceId) {
    return [];
  }

  return [{
    type: "registry_upsert",
    registration: nextRegistration,
  }];
}

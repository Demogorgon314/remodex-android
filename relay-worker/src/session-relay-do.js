import { DurableObject } from "cloudflare:workers";
import {
  CLOSE_CODE_INVALID_SESSION,
  CLOSE_CODE_MAC_ABSENCE_BUFFER_FULL,
  bindSessionId,
  closeConnection,
  connectIphone,
  connectMac,
  createSessionSnapshot,
  expireMacAbsenceIfNeeded,
  hasActiveMacSession,
  readMacRegistrationHeaders,
  updateMacRegistration,
} from "./session/state.js";
import {
  createHTTPError,
  isWebSocketUpgrade,
  jsonResponse,
  normalizeNonEmptyString,
  normalizeWireMessage,
  readJSONBody,
  safeParseJSON,
} from "./common.js";

const SNAPSHOT_KEY = "sessionSnapshot";

export class SessionRelayDurableObject extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx = ctx;
    this.env = env;
    this.snapshot = createSessionSnapshot();
    this.ready = this.ctx.blockConcurrencyWhile(async () => {
      const storedSnapshot = await this.ctx.storage.get(SNAPSHOT_KEY);
      this.snapshot = createSessionSnapshot(storedSnapshot);
    });
  }

  async fetch(request) {
    await this.ready;

    const url = new URL(request.url);
    const pathname = url.pathname;
    if (request.method === "GET" && pathname === "/internal/status") {
      const expired = expireMacAbsenceIfNeeded(this.snapshot);
      await this.commit(expired.snapshot, expired.effects);
      return jsonResponse(200, {
        ok: true,
        sessionId: expired.snapshot.sessionId,
        hasActiveMac: hasActiveMacSession(expired.snapshot),
        hasConnectedIphone: Boolean(expired.snapshot.iphoneConnectionId),
      });
    }

    if (!isWebSocketUpgrade(request)) {
      return jsonResponse(404, {
        ok: false,
        error: "Not found",
      });
    }

    const match = pathname.match(/^\/relay\/([^/?]+)/);
    const sessionId = normalizeNonEmptyString(match?.[1]);
    const role = normalizeNonEmptyString(request.headers.get("x-role"));
    if (!sessionId || (role !== "mac" && role !== "iphone")) {
      throw createHTTPError(400, "invalid_upgrade", "Missing sessionId or invalid x-role header.");
    }

    this.snapshot = bindSessionId(this.snapshot, sessionId);
    const webSocketPair = new WebSocketPair();
    const [client, server] = Object.values(webSocketPair);
    const connectionId = crypto.randomUUID();

    server.serializeAttachment({
      role,
      connectionId,
      sessionId,
    });
    this.ctx.acceptWebSocket(server, [role]);

    let result;
    if (role === "mac") {
      result = connectMac(this.snapshot, {
        connectionId,
        registration: readMacRegistrationHeaders(request.headers, sessionId),
        notificationSecret: request.headers.get("x-notification-secret"),
      });
    } else {
      result = connectIphone(this.snapshot, {
        connectionId,
      });
    }

    await this.commit(result.snapshot, result.effects);
    if (result.reject) {
      server.close(result.reject.code, result.reject.reason);
    }

    return new Response(null, {
      status: 101,
      webSocket: client,
    });
  }

  async webSocketMessage(ws, message) {
    await this.ready;
    const attachment = ws.deserializeAttachment() || {};
    const role = attachment.role;
    if (role !== "mac" && role !== "iphone") {
      ws.close(CLOSE_CODE_INVALID_SESSION, "Missing sessionId or invalid x-role header");
      return;
    }

    const expired = expireMacAbsenceIfNeeded(this.snapshot);
    await this.commit(expired.snapshot, expired.effects);

    const normalizedMessage = normalizeWireMessage(message);
    if (role === "mac") {
      const parsed = safeParseJSON(normalizedMessage);
      if (parsed?.kind === "relayMacRegistration" && typeof parsed.registration === "object") {
        const result = updateMacRegistration(this.snapshot, {
          ...parsed.registration,
          sessionId: this.snapshot.sessionId,
        });
        await this.commit(result.snapshot, result.effects);
        return;
      }

      for (const iphoneSocket of this.ctx.getWebSockets("iphone")) {
        iphoneSocket.send(normalizedMessage);
      }
      return;
    }

    const macSocket = this.ctx.getWebSockets("mac")[0] || null;
    if (!macSocket || !hasActiveMacSession(this.snapshot)) {
      ws.close(CLOSE_CODE_MAC_ABSENCE_BUFFER_FULL, "Mac temporarily unavailable");
      return;
    }

    macSocket.send(normalizedMessage);
  }

  async webSocketClose(ws) {
    await this.ready;
    const attachment = ws.deserializeAttachment() || {};
    const result = closeConnection(this.snapshot, {
      role: attachment.role,
      connectionId: attachment.connectionId,
    });
    await this.commit(result.snapshot, result.effects);
  }

  async alarm() {
    await this.ready;
    const result = expireMacAbsenceIfNeeded(this.snapshot);
    await this.commit(result.snapshot, result.effects);
  }

  async commit(nextSnapshot, effects = []) {
    this.snapshot = createSessionSnapshot(nextSnapshot);
    await this.ctx.storage.put(SNAPSHOT_KEY, this.snapshot);
    await this.runEffects(effects);
  }

  async runEffects(effects) {
    for (const effect of effects) {
      switch (effect.type) {
        case "close_role":
          this.closeRoleSockets(effect);
          break;
        case "registry_upsert":
          await this.upsertRegistry(effect.registration);
          break;
        case "registry_remove":
          await this.removeRegistry(effect.macDeviceId, effect.sessionId);
          break;
        case "set_alarm":
          await this.ctx.storage.setAlarm(effect.scheduledTime);
          break;
        case "delete_alarm":
          await this.ctx.storage.deleteAlarm();
          break;
        default:
          break;
      }
    }
  }

  closeRoleSockets({
    role,
    exceptConnectionId = "",
    code,
    reason,
  }) {
    for (const socket of this.ctx.getWebSockets(role)) {
      const attachment = socket.deserializeAttachment() || {};
      if (attachment.connectionId === exceptConnectionId) {
        continue;
      }
      socket.close(code, reason);
    }
  }

  async upsertRegistry(registration) {
    const macDeviceId = normalizeNonEmptyString(registration?.macDeviceId);
    if (!macDeviceId) {
      return;
    }

    const stub = this.env.TRUSTED_REGISTRY_DO.get(
      this.env.TRUSTED_REGISTRY_DO.idFromName(macDeviceId)
    );
    await stub.fetch("https://registry.internal/internal/upsert", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        registration,
      }),
    });
  }

  async removeRegistry(macDeviceId, sessionId) {
    const normalizedMacDeviceId = normalizeNonEmptyString(macDeviceId);
    if (!normalizedMacDeviceId) {
      return;
    }

    const stub = this.env.TRUSTED_REGISTRY_DO.get(
      this.env.TRUSTED_REGISTRY_DO.idFromName(normalizedMacDeviceId)
    );
    await stub.fetch("https://registry.internal/internal/remove", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        sessionId,
      }),
    });
  }
}

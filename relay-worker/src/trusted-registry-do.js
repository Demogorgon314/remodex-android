import { DurableObject } from "cloudflare:workers";
import {
  createTrustedRegistrySnapshot,
  removeLiveSessionIfSessionMatches,
  resolveTrustedSession,
  upsertLiveSession,
} from "./registry/state.js";
import { createHTTPError, jsonResponse, normalizeNonEmptyString, readJSONBody } from "./common.js";

const SNAPSHOT_KEY = "registrySnapshot";

export class TrustedRegistryDurableObject extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx = ctx;
    this.env = env;
    this.snapshot = createTrustedRegistrySnapshot();
    this.ready = this.ctx.blockConcurrencyWhile(async () => {
      const storedSnapshot = await this.ctx.storage.get(SNAPSHOT_KEY);
      this.snapshot = createTrustedRegistrySnapshot(storedSnapshot);
    });
  }

  async fetch(request) {
    await this.ready;

    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/internal/upsert") {
      const body = await readJSONBody(request);
      this.snapshot = upsertLiveSession(this.snapshot, body.registration);
      await this.ctx.storage.put(SNAPSHOT_KEY, this.snapshot);
      return jsonResponse(200, { ok: true });
    }

    if (request.method === "POST" && url.pathname === "/internal/remove") {
      const body = await readJSONBody(request);
      this.snapshot = removeLiveSessionIfSessionMatches(this.snapshot, {
        sessionId: body.sessionId,
      });
      await this.ctx.storage.put(SNAPSHOT_KEY, this.snapshot);
      return jsonResponse(200, { ok: true });
    }

    if (request.method === "POST" && url.pathname === "/v1/trusted/session/resolve") {
      const body = await readJSONBody(request);
      const result = await resolveTrustedSession(this.snapshot, body, {
        hasActiveSession: async (sessionId) => this.hasActiveSession(sessionId),
      });
      this.snapshot = result.snapshot;
      await this.ctx.storage.put(SNAPSHOT_KEY, this.snapshot);
      return jsonResponse(200, result.response);
    }

    return jsonResponse(404, {
      ok: false,
      error: "Not found",
    });
  }

  async hasActiveSession(sessionId) {
    const normalizedSessionId = normalizeNonEmptyString(sessionId);
    if (!normalizedSessionId) {
      return false;
    }

    const stub = this.env.SESSION_RELAY_DO.get(
      this.env.SESSION_RELAY_DO.idFromName(normalizedSessionId)
    );
    const response = await stub.fetch("https://session.internal/internal/status");
    if (!response.ok) {
      return false;
    }

    const body = await response.json();
    return body.hasActiveMac === true;
  }
}

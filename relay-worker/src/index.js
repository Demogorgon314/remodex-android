import { SessionRelayDurableObject } from "./session-relay-do.js";
import { TrustedRegistryDurableObject } from "./trusted-registry-do.js";
import { createHTTPError, isWebSocketUpgrade, jsonResponse, normalizeNonEmptyString, readJSONBody } from "./common.js";

export { SessionRelayDurableObject, TrustedRegistryDurableObject };

export default {
  async fetch(request, env) {
    try {
      return await handleRequest(request, env);
    } catch (error) {
      return jsonResponse(error.status || 500, {
        ok: false,
        error: error.message || "Internal server error",
        code: error.code || "internal_error",
      });
    }
  },
};

async function handleRequest(request, env) {
  const url = new URL(request.url);
  const pathname = url.pathname;

  if (request.method === "GET" && pathname === "/health") {
    return jsonResponse(200, { ok: true });
  }

  if (pathname.startsWith("/relay/")) {
    return routeRelayUpgrade(request, env);
  }

  if (request.method === "POST" && pathname === "/v1/trusted/session/resolve") {
    return routeTrustedResolve(request, env, url);
  }

  if (
    request.method === "POST"
    && (
      pathname === "/v1/push/session/register-device"
      || pathname === "/v1/push/session/notify-completion"
    )
  ) {
    return jsonResponse(404, {
      ok: false,
      error: "Not found",
    });
  }

  return jsonResponse(404, {
    ok: false,
    error: "Not found",
  });
}

function routeRelayUpgrade(request, env) {
  if (request.method !== "GET" || !isWebSocketUpgrade(request)) {
    throw createHTTPError(426, "expected_websocket", "Expected Upgrade: websocket");
  }

  const url = new URL(request.url);
  const sessionId = normalizeNonEmptyString(url.pathname.match(/^\/relay\/([^/?]+)/)?.[1]);
  const role = normalizeNonEmptyString(request.headers.get("x-role"));
  if (!sessionId || (role !== "mac" && role !== "iphone")) {
    throw createHTTPError(400, "invalid_upgrade", "Missing sessionId or invalid x-role header.");
  }

  const stub = env.SESSION_RELAY_DO.get(env.SESSION_RELAY_DO.idFromName(sessionId));
  return stub.fetch(request);
}

async function routeTrustedResolve(request, env, url) {
  const body = await readJSONBody(request);
  const macDeviceId = normalizeNonEmptyString(body.macDeviceId);
  if (!macDeviceId) {
    throw createHTTPError(400, "invalid_request", "The trusted-session resolve request is missing required fields.");
  }

  const stub = env.TRUSTED_REGISTRY_DO.get(env.TRUSTED_REGISTRY_DO.idFromName(macDeviceId));
  return stub.fetch(new Request(url.toString(), {
    method: request.method,
    headers: request.headers,
    body: JSON.stringify(body),
  }));
}

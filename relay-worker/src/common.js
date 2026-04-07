const JSON_HEADERS = {
  "content-type": "application/json",
};

export function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: JSON_HEADERS,
  });
}

export async function readJSONBody(request) {
  const rawBody = await request.text();
  if (!rawBody.trim()) {
    return {};
  }

  try {
    return JSON.parse(rawBody);
  } catch {
    throw createHTTPError(400, "invalid_json", "Invalid JSON body");
  }
}

export function createHTTPError(status, code, message) {
  return Object.assign(new Error(message), {
    status,
    code,
  });
}

export function normalizeNonEmptyString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

export function readHeaderString(value) {
  const normalized = normalizeNonEmptyString(value);
  return normalized || null;
}

export function normalizeWireMessage(value) {
  if (typeof value === "string") {
    return value;
  }

  if (value instanceof ArrayBuffer) {
    return new TextDecoder().decode(new Uint8Array(value));
  }

  if (ArrayBuffer.isView(value)) {
    return new TextDecoder().decode(value);
  }

  return String(value ?? "");
}

export function safeParseJSON(value) {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }

  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

export function isWebSocketUpgrade(request) {
  return (request.headers.get("Upgrade") || "").toLowerCase() === "websocket";
}

// FILE: voice-handler.js
// Purpose: Handles bridge-owned voice transcription requests without exposing auth tokens to iPhone.
// Layer: Bridge handler
// Exports: createVoiceHandler
// Depends on: global fetch/FormData/Blob, local codex app-server auth via sendCodexRequest

const fs = require("fs/promises");
const os = require("os");
const path = require("path");

const CHATGPT_TRANSCRIPTIONS_URL = "https://chatgpt.com/backend-api/transcribe";
const MAX_AUDIO_BYTES = 10 * 1024 * 1024;
const MAX_DURATION_MS = 60_000;

function createVoiceHandler({
  sendCodexRequest,
  fetchImpl = globalThis.fetch,
  FormDataImpl = globalThis.FormData,
  BlobImpl = globalThis.Blob,
  readLocalAuthToken = readLocalChatGPTAuthTokenFromDisk,
  logPrefix = "[remodex]",
} = {}) {
  function handleVoiceRequest(rawMessage, sendResponse) {
    let parsed;
    try {
      parsed = JSON.parse(rawMessage);
    } catch {
      return false;
    }

    const method = typeof parsed?.method === "string" ? parsed.method.trim() : "";
    if (method !== "voice/transcribe") {
      return false;
    }

    const id = parsed.id;
    const params = parsed.params || {};

    transcribeVoice(params, {
      sendCodexRequest,
      fetchImpl,
      FormDataImpl,
      BlobImpl,
      readLocalAuthToken,
    })
      .then((result) => {
        sendResponse(JSON.stringify({ id, result }));
      })
      .catch((error) => {
        console.error(`${logPrefix} voice transcription failed: ${error.message}`);
        sendResponse(JSON.stringify({
          id,
          error: {
            code: -32000,
            message: error.userMessage || error.message || "Voice transcription failed.",
            data: {
              errorCode: error.errorCode || "voice_transcription_failed",
            },
          },
        }));
      });

    return true;
  }

  return {
    handleVoiceRequest,
  };
}

// ─── Audio validation helpers ───────────────────────────────

// Validates iPhone-owned audio input and proxies it to the official transcription endpoint.
async function transcribeVoice(
  params,
  { sendCodexRequest, fetchImpl, FormDataImpl, BlobImpl, readLocalAuthToken }
) {
  if (typeof sendCodexRequest !== "function") {
    throw voiceError("bridge_not_ready", "Voice transcription is not available right now.");
  }
  if (typeof fetchImpl !== "function" || !FormDataImpl || !BlobImpl) {
    throw voiceError("transcription_unavailable", "Voice transcription is unavailable on this bridge.");
  }

  const mimeType = readString(params.mimeType);
  if (mimeType !== "audio/wav") {
    throw voiceError("unsupported_mime_type", "Only WAV audio is supported for voice transcription.");
  }

  const sampleRateHz = readPositiveNumber(params.sampleRateHz);
  if (sampleRateHz !== 24_000) {
    throw voiceError("unsupported_sample_rate", "Voice transcription requires 24 kHz mono WAV audio.");
  }

  const durationMs = readPositiveNumber(params.durationMs);
  if (durationMs <= 0) {
    throw voiceError("invalid_duration", "Voice messages must include a positive duration.");
  }
  if (durationMs > MAX_DURATION_MS) {
    throw voiceError("duration_too_long", "Voice messages are limited to 60 seconds.");
  }

  const audioBuffer = decodeAudioBase64(params.audioBase64);
  if (audioBuffer.length > MAX_AUDIO_BYTES) {
    throw voiceError("audio_too_large", "Voice messages are limited to 10 MB.");
  }

  const authContext = await loadAuthContext(sendCodexRequest, { readLocalAuthToken });
  return requestTranscription({
    authContext,
    audioBuffer,
    mimeType,
    fetchImpl,
    FormDataImpl,
    BlobImpl,
    sendCodexRequest,
    readLocalAuthToken,
  });
}

async function requestTranscription({
  authContext,
  audioBuffer,
  mimeType,
  fetchImpl,
  FormDataImpl,
  BlobImpl,
  sendCodexRequest,
  readLocalAuthToken,
}) {
  const makeAttempt = async (activeAuthContext) => {
    const formData = new FormDataImpl();
    formData.append("file", new BlobImpl([audioBuffer], { type: mimeType }), "voice.wav");

    const headers = {
      Authorization: `Bearer ${activeAuthContext.token}`,
    };

    return fetchImpl(activeAuthContext.transcriptionURL, {
      method: "POST",
      headers,
      body: formData,
    });
  };

  let response = await makeAttempt(authContext);
  if (response.status === 401) {
    const refreshedAuthContext = await loadAuthContext(sendCodexRequest, {
      forceRefresh: true,
      readLocalAuthToken,
    });
    response = await makeAttempt(refreshedAuthContext);
  }

  if (!response.ok) {
    let errorMessage = `Transcription failed with status ${response.status}.`;
    try {
      const errorPayload = await response.json();
      const providerMessage = readString(errorPayload?.error?.message) || readString(errorPayload?.message);
      if (providerMessage) {
        errorMessage = providerMessage;
      }
    } catch {
      // Keep the generic message when the provider body is empty or non-JSON.
    }

    if (response.status === 401 || response.status === 403) {
      throw voiceError("not_authenticated", "Your ChatGPT login has expired. Sign in again.");
    }

    throw voiceError("transcription_failed", errorMessage);
  }

  const payload = await response.json().catch(() => null);
  const text = readString(payload?.text) || readString(payload?.transcript);
  if (!text) {
    throw voiceError("transcription_invalid_response", "The transcription response did not include any text.");
  }

  return { text };
}

// Reads the current bridge-owned auth state from the local codex app-server and refreshes if needed.
async function loadAuthContext(
  sendCodexRequest,
  {
    forceRefresh = false,
    readLocalAuthToken = readLocalChatGPTAuthTokenFromDisk,
  } = {}
) {
  const { authMethod, token, isChatGPT } = await resolveCurrentOrRefreshedAuthStatus(sendCodexRequest, {
    forceRefresh,
    readLocalAuthToken,
  });

  if (!token) {
    throw voiceError("not_authenticated", "Sign in with ChatGPT before using voice transcription.");
  }
  if (!isChatGPT) {
    throw voiceError("not_chatgpt", "Voice transcription requires a ChatGPT account.");
  }

  return {
    authMethod,
    token,
    isChatGPT,
    transcriptionURL: CHATGPT_TRANSCRIPTIONS_URL,
    chatgptAccountId: readChatGPTAccountIdFromToken(token),
  };
}

function decodeAudioBase64(value) {
  const normalized = normalizeBase64(value);
  if (!normalized) {
    throw voiceError("missing_audio", "The voice request did not include any audio.");
  }

  if (!isLikelyBase64(normalized)) {
    throw voiceError("invalid_audio", "The recorded audio could not be decoded.");
  }

  const audioBuffer = Buffer.from(normalized, "base64");
  if (!audioBuffer.length) {
    throw voiceError("invalid_audio", "The recorded audio could not be decoded.");
  }

  if (audioBuffer.toString("base64") !== normalized) {
    throw voiceError("invalid_audio", "The recorded audio could not be decoded.");
  }

  if (!isLikelyWavBuffer(audioBuffer)) {
    throw voiceError("invalid_audio", "The recorded audio is not a valid WAV file.");
  }

  return audioBuffer;
}

// Keeps the bridge strict about the payload shape so malformed uploads fail before fetch().
function normalizeBase64(value) {
  return typeof value === "string" ? value.replace(/\s+/g, "").trim() : "";
}

function isLikelyBase64(value) {
  return /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value);
}

function isLikelyWavBuffer(buffer) {
  return buffer.length >= 44
    && buffer.toString("ascii", 0, 4) === "RIFF"
    && buffer.toString("ascii", 8, 12) === "WAVE";
}

function readChatGPTAccountIdFromToken(token) {
  const payload = decodeJWTPayload(token);
  const authClaim = payload?.["https://api.openai.com/auth"];
  return readString(
    authClaim?.chatgpt_account_id
      || authClaim?.chatgptAccountId
      || payload?.chatgpt_account_id
      || payload?.chatgptAccountId
  );
}

function decodeJWTPayload(token) {
  const segments = typeof token === "string" ? token.split(".") : [];
  if (segments.length < 2) {
    return null;
  }

  const normalized = segments[1]
    .replace(/-/g, "+")
    .replace(/_/g, "/")
    .padEnd(Math.ceil(segments[1].length / 4) * 4, "=");

  try {
    return JSON.parse(Buffer.from(normalized, "base64").toString("utf8"));
  } catch {
    return null;
  }
}

function readString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readPositiveNumber(value) {
  const numericValue = typeof value === "number" ? value : Number(value);
  return Number.isFinite(numericValue) && numericValue >= 0 ? numericValue : 0;
}

function voiceError(errorCode, userMessage) {
  const error = new Error(userMessage);
  error.errorCode = errorCode;
  error.userMessage = userMessage;
  return error;
}

// Returns an ephemeral ChatGPT token so the phone can call the transcription API directly.
// Uses its own token resolution instead of loadAuthContext so errors are specific and actionable.
async function resolveVoiceAuth(
  sendCodexRequest,
  params = null,
  { readLocalAuthToken = readLocalChatGPTAuthTokenFromDisk } = {}
) {
  const forceRefresh = Boolean(params?.forceRefresh);
  const { authMethod, token, isChatGPT, requiresOpenaiAuth } = await resolveCurrentOrRefreshedAuthStatus(sendCodexRequest, {
    forceRefresh,
    readLocalAuthToken,
    rpcErrorCode: "auth_unavailable",
    rpcErrorMessage: "Could not read ChatGPT session from the Mac runtime. Is the bridge running?",
  });

  // Check for a usable ChatGPT token first. The runtime may set requiresOpenaiAuth
  // even when a valid ChatGPT session is present (the flag is about the runtime's
  // preferred auth mode, not whether ChatGPT tokens are actually available).
  if (isChatGPT && token) {
    return { token };
  }

  if (!token) {
    console.error(`[remodex] voice/resolveAuth: no token. authMethod=${authMethod || "none"} requiresOpenaiAuth=${requiresOpenaiAuth}`);
    throw voiceError("token_missing", "No ChatGPT session token available. Sign in to ChatGPT on the Mac.");
  }

  throw voiceError("not_chatgpt", "Voice transcription requires a ChatGPT account.");
}

async function resolveCurrentOrRefreshedAuthStatus(
  sendCodexRequest,
  {
    forceRefresh = false,
    readLocalAuthToken = readLocalChatGPTAuthTokenFromDisk,
    rpcErrorCode = "not_authenticated",
    rpcErrorMessage = "Sign in with ChatGPT before using voice transcription.",
  } = {}
) {
  if (forceRefresh) {
    const refreshedStatus = await readAuthStatus(sendCodexRequest, {
      refreshToken: true,
      rpcErrorCode,
      rpcErrorMessage,
    });
    return withLocalAuthFallback(refreshedStatus, readLocalAuthToken);
  }

  const currentStatus = await withLocalAuthFallback(
    await readAuthStatus(sendCodexRequest, {
      refreshToken: false,
      rpcErrorCode,
      rpcErrorMessage,
    }),
    readLocalAuthToken
  );

  if (currentStatus.token) {
    return currentStatus;
  }

  const refreshedStatus = await readAuthStatus(sendCodexRequest, {
    refreshToken: true,
    rpcErrorCode,
    rpcErrorMessage,
  });

  return withLocalAuthFallback(refreshedStatus, readLocalAuthToken);
}

async function withLocalAuthFallback(status, readLocalAuthToken) {
  if (status?.token || typeof readLocalAuthToken !== "function") {
    return status;
  }

  const localAuthStatus = await readLocalAuthToken().catch(() => null);
  if (!localAuthStatus?.token || !localAuthStatus?.isChatGPT) {
    return status;
  }

  return {
    authMethod: localAuthStatus.authMethod || status.authMethod || "chatgpt",
    token: localAuthStatus.token,
    isChatGPT: true,
    requiresOpenaiAuth: localAuthStatus.requiresOpenaiAuth ?? status.requiresOpenaiAuth,
  };
}

async function readLocalChatGPTAuthTokenFromDisk({
  readFileImpl = fs.readFile,
  codexHome = process.env.CODEX_HOME || path.join(os.homedir(), ".codex"),
} = {}) {
  const authFile = path.join(codexHome, "auth.json");
  const contents = await readFileImpl(authFile, "utf8");
  const auth = JSON.parse(contents);
  const authMethod = readString(auth?.auth_mode);
  const tokenContainer = auth?.tokens && typeof auth.tokens === "object" ? auth.tokens : auth;
  const token = readString(tokenContainer?.access_token);
  const isChatGPT = token != null
    && authMethod !== "apikey"
    && authMethod !== "api_key"
    && authMethod !== "apiKey";

  return {
    authMethod: authMethod || (isChatGPT ? "chatgpt" : null),
    token,
    isChatGPT,
    requiresOpenaiAuth: isChatGPT,
  };
}

async function readAuthStatus(
  sendCodexRequest,
  { refreshToken, rpcErrorCode, rpcErrorMessage }
) {
  let authStatus;
  try {
    authStatus = await sendCodexRequest("getAuthStatus", {
      includeToken: true,
      refreshToken,
    });
  } catch (err) {
    throw voiceError(rpcErrorCode, rpcErrorMessage);
  }

  const authMethod = readString(authStatus?.authMethod);
  const token = readString(authStatus?.authToken);
  return {
    authMethod,
    token,
    isChatGPT: authMethod === "chatgpt" || authMethod === "chatgptAuthTokens",
    requiresOpenaiAuth: Boolean(authStatus?.requiresOpenaiAuth),
  };
}

module.exports = {
  createVoiceHandler,
  readLocalChatGPTAuthTokenFromDisk,
  resolveVoiceAuth,
};

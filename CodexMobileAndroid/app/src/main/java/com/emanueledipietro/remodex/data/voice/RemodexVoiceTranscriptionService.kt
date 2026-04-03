package com.emanueledipietro.remodex.data.voice

import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.firstString
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

data class RemodexVoiceTranscriptionPreflight(
    val byteCount: Int,
    val durationSeconds: Double,
) {
    val failureMessage: String?
        get() = when {
            durationSeconds > MaxDurationSeconds -> "Voice clips must be 60 seconds or less."
            byteCount > MaxByteCount -> "Voice clips must be smaller than 10 MB."
            else -> null
        }

    fun validate() {
        failureMessage?.let { message ->
            throw RemodexVoiceTranscriptionException.InvalidInput(message)
        }
    }

    companion object {
        const val MaxDurationSeconds: Double = 60.0
        const val MaxByteCount: Int = 10 * 1024 * 1024
    }
}

sealed class RemodexVoiceTranscriptionException(
    override val message: String,
) : Exception(message) {
    class InvalidInput(message: String) : RemodexVoiceTranscriptionException(message)

    class InvalidResponse(message: String) : RemodexVoiceTranscriptionException(message)

    class Failed(message: String) : RemodexVoiceTranscriptionException(message)

    class AuthExpired : RemodexVoiceTranscriptionException(
        "Your ChatGPT login has expired. Sign in again.",
    )
}

interface VoiceTranscriptionClient {
    suspend fun transcribe(
        wavFile: File,
        token: String,
    ): String
}

class OkHttpVoiceTranscriptionClient(
    private val client: OkHttpClient,
) : VoiceTranscriptionClient {
    override suspend fun transcribe(
        wavFile: File,
        token: String,
    ): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "voice.wav",
                body = wavFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url(ChatGptTranscriptionUrl)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 401 || response.code == 403 -> {
                    throw RemodexVoiceTranscriptionException.AuthExpired()
                }

                !response.isSuccessful -> {
                    val serverMessage = extractErrorMessage(body)
                    throw RemodexVoiceTranscriptionException.Failed(
                        serverMessage ?: "Transcription failed (${response.code}).",
                    )
                }
            }

            return@withContext decodeTranscriptText(body)
        }
    }

    private fun extractErrorMessage(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val errorMessage = json.optJSONObject("error")?.optString("message")?.trim()
        if (!errorMessage.isNullOrEmpty()) {
            return errorMessage
        }
        return json.optString("message").trim().ifEmpty { null }
    }

    private fun decodeTranscriptText(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw RemodexVoiceTranscriptionException.Failed(
                "Could not parse transcript response.",
            )

        val transcript = listOf("text", "transcript")
            .firstNotNullOfOrNull { key ->
                json.optString(key).trim().takeIf(String::isNotEmpty)
            }

        return transcript ?: throw RemodexVoiceTranscriptionException.Failed(
            "Transcript response was empty.",
        )
    }

    private companion object {
        private const val ChatGptTranscriptionUrl = "https://chatgpt.com/backend-api/transcribe"
    }
}

interface RemodexVoiceTranscriptionService {
    suspend fun transcribeVoiceAudioFile(
        file: File,
        durationSeconds: Double,
    ): String
}

class DefaultRemodexVoiceTranscriptionService(
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val transcriptionClient: VoiceTranscriptionClient,
    private val onAuthStateInvalidated: suspend () -> Unit = {},
) : RemodexVoiceTranscriptionService {
    override suspend fun transcribeVoiceAudioFile(
        file: File,
        durationSeconds: Double,
    ): String = withContext(Dispatchers.IO) {
        val preflight = RemodexVoiceTranscriptionPreflight(
            byteCount = file.length().toInt(),
            durationSeconds = durationSeconds,
        )
        preflight.validate()

        val token = try {
            resolveVoiceAuthToken()
        } catch (error: Throwable) {
            if (shouldInvalidateVoiceAuth(error)) {
                onAuthStateInvalidated()
            }
            throw error
        }

        try {
            transcriptionClient.transcribe(wavFile = file, token = token)
        } catch (error: RemodexVoiceTranscriptionException.AuthExpired) {
            onAuthStateInvalidated()
            val freshToken = resolveVoiceAuthToken(forceRefresh = true)
            transcriptionClient.transcribe(wavFile = file, token = freshToken)
        }
    }

    private suspend fun resolveVoiceAuthToken(forceRefresh: Boolean = false): String {
        val response = secureConnectionCoordinator.sendRequest(
            method = "voice/resolveAuth",
            params = if (forceRefresh) {
                buildJsonObject {
                    put("forceRefresh", JsonPrimitive(true))
                }
            } else {
                null
            },
        )
        val payload = response.result?.jsonObjectOrNull
        val token = payload?.firstString("token")
        if (token.isNullOrBlank()) {
            throw RemodexVoiceTranscriptionException.InvalidResponse(
                "voice/resolveAuth did not return a valid token",
            )
        }
        return token
    }

    private fun shouldInvalidateVoiceAuth(error: Throwable): Boolean {
        return error is RpcError || error is RemodexVoiceTranscriptionException.AuthExpired
    }
}

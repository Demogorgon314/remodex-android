package com.emanueledipietro.remodex.data.voice

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.ScriptedRpcRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.SecureTransportException
import com.emanueledipietro.remodex.data.connection.UnexpectedRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.UnusedTrustedSessionResolver
import com.emanueledipietro.remodex.data.connection.createTestMacIdentity
import com.emanueledipietro.remodex.data.connection.createTestPairingPayload
import com.emanueledipietro.remodex.data.connection.firstBoolean
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemodexVoiceTranscriptionServiceTest {
    @Test
    fun `transport race does not invalidate voice auth state`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )
        setSecureConnectionState(
            coordinator = coordinator,
            snapshot = SecureConnectionSnapshot(
                phaseMessage = "Connected",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 1,
            ),
        )
        var invalidationCount = 0
        val service = DefaultRemodexVoiceTranscriptionService(
            secureConnectionCoordinator = coordinator,
            transcriptionClient = object : VoiceTranscriptionClient {
                override suspend fun transcribe(
                    wavFile: java.io.File,
                    token: String,
                ): String {
                    fail("Voice transcription client should not run without a secure transport session.")
                    return ""
                }
            },
            onAuthStateInvalidated = {
                invalidationCount += 1
            },
        )
        val wavFile = java.io.File.createTempFile("remodex-voice-", ".wav").apply {
            writeBytes(ByteArray(32))
            deleteOnExit()
        }

        try {
            service.transcribeVoiceAudioFile(
                file = wavFile,
                durationSeconds = 1.0,
            )
            fail("Expected secure transport failure.")
        } catch (error: Throwable) {
            assertTrue(error is SecureTransportException)
        } finally {
            wavFile.delete()
        }

        assertEquals(0, invalidationCount)
    }

    @Test
    fun `auth retry forces a fresh voice token after the provider returns 401`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-voice-refresh",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var authResolveCount = 0
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "voice/resolveAuth" to { request ->
                    authResolveCount += 1
                    val forceRefresh = request.params?.jsonObjectOrNull?.firstBoolean("forceRefresh") ?: false
                    if (authResolveCount == 1) {
                        assertEquals(false, forceRefresh)
                    } else {
                        assertEquals(true, forceRefresh)
                    }
                    buildJsonObject {
                        put(
                            "token",
                            JsonPrimitive(if (authResolveCount == 1) "stale-token" else "fresh-token"),
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val usedTokens = mutableListOf<String>()
        var invalidationCount = 0
        val service = DefaultRemodexVoiceTranscriptionService(
            secureConnectionCoordinator = coordinator,
            transcriptionClient = object : VoiceTranscriptionClient {
                override suspend fun transcribe(
                    wavFile: java.io.File,
                    token: String,
                ): String {
                    usedTokens += token
                    if (token == "stale-token") {
                        throw RemodexVoiceTranscriptionException.AuthExpired()
                    }
                    return "fresh transcript"
                }
            },
            onAuthStateInvalidated = {
                invalidationCount += 1
            },
        )
        val wavFile = java.io.File.createTempFile("remodex-voice-", ".wav").apply {
            writeBytes(ByteArray(32))
            deleteOnExit()
        }

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)

            val transcript = service.transcribeVoiceAudioFile(
                file = wavFile,
                durationSeconds = 1.0,
            )

            assertEquals("fresh transcript", transcript)
            assertEquals(listOf("stale-token", "fresh-token"), usedTokens)
            assertEquals(1, invalidationCount)
            assertEquals(2, authResolveCount)
            assertEquals(
                listOf(false, true),
                relayFactory.receivedRequests
                    .filter { it.method == "voice/resolveAuth" }
                    .map { it.params?.jsonObjectOrNull?.firstBoolean("forceRefresh") ?: false },
            )
        } finally {
            wavFile.delete()
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSecureConnectionState(
        coordinator: SecureConnectionCoordinator,
        snapshot: SecureConnectionSnapshot,
    ) {
        val field = coordinator.javaClass.getDeclaredField("connectionState")
        field.isAccessible = true
        val state = field.get(coordinator) as MutableStateFlow<SecureConnectionSnapshot>
        state.value = snapshot
    }

    private suspend fun TestScope.awaitSecureState(
        coordinator: SecureConnectionCoordinator,
        expectedState: SecureConnectionState,
    ) {
        repeat(40) {
            advanceUntilIdle()
            if (coordinator.state.value.secureState == expectedState) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected $expectedState but was ${coordinator.state.value.secureState}")
    }
}

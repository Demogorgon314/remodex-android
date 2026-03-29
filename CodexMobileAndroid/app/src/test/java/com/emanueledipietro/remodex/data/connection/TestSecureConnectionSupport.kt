package com.emanueledipietro.remodex.data.connection

import java.security.SecureRandom
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters

data class TestMacIdentity(
    val privateKeyBase64: String,
    val publicKeyBase64: String,
)

fun createTestMacIdentity(): TestMacIdentity {
    val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
    return TestMacIdentity(
        privateKeyBase64 = SecureCrypto.encodeBase64(privateKey.encoded),
        publicKeyBase64 = SecureCrypto.encodeBase64(privateKey.generatePublicKey().encoded),
    )
}

fun createTestPairingPayload(
    macDeviceId: String,
    macIdentityPublicKey: String,
    relay: String = "ws://127.0.0.1:7777/relay",
    sessionId: String = "session-android-test",
): PairingQrPayload {
    return PairingQrPayload(
        v = remodexPairingQrVersion,
        relay = relay,
        sessionId = sessionId,
        macDeviceId = macDeviceId,
        macIdentityPublicKey = macIdentityPublicKey,
        expiresAt = 4_102_444_800_000,
    )
}

fun seedTrustedMacState(
    store: SecureStore,
    macDeviceId: String,
    macIdentityPublicKey: String,
    relayUrl: String = "ws://127.0.0.1:7777/relay",
    sessionId: String = "session-trusted-test",
) {
    SecureCrypto.rememberResolvedTrustedSession(
        store = store,
        profileId = "test-profile-$macDeviceId",
        response = TrustedSessionResolveResponse(
            ok = true,
            macDeviceId = macDeviceId,
            macIdentityPublicKey = macIdentityPublicKey,
            displayName = "Desk Mac",
            sessionId = sessionId,
        ),
        relayUrl = relayUrl,
        resetReplayCursor = true,
    )
    SecureCrypto.writeTrustedMacRegistry(
        store = store,
        registry = TrustedMacRegistry(
            records = mapOf(
                macDeviceId to TrustedMacRecord(
                    macDeviceId = macDeviceId,
                    macIdentityPublicKey = macIdentityPublicKey,
                    lastPairedAtEpochMs = 1_713_000_000_000,
                    relayUrl = relayUrl,
                    displayName = "Desk Mac",
                    lastResolvedSessionId = sessionId,
                    lastResolvedAtEpochMs = 1_713_000_000_000,
                    lastUsedAtEpochMs = 1_713_000_000_000,
                ),
            ),
        ),
        lastTrustedMacDeviceId = macDeviceId,
    )
}

object UnusedTrustedSessionResolver : TrustedSessionResolver {
    override suspend fun resolve(
        relayUrl: String,
        request: TrustedSessionResolveRequest,
    ): TrustedSessionResolveResponse {
        error("Trusted session resolution should not be used in this test.")
    }
}

class ThrowingTrustedSessionResolver(
    private val error: TrustedSessionResolveException,
) : TrustedSessionResolver {
    override suspend fun resolve(
        relayUrl: String,
        request: TrustedSessionResolveRequest,
    ): TrustedSessionResolveResponse {
        throw error
    }
}

class UnexpectedRelayWebSocketFactory : RelayWebSocketFactory {
    override fun open(
        url: String,
        headers: Map<String, String>,
        events: Channel<RelayWireEvent>,
    ): RelayWebSocket {
        error("WebSocket connection should not have been opened in this test.")
    }
}

class SuccessfulQrBootstrapRelayWebSocketFactory(
    private val macDeviceId: String,
    private val macIdentity: TestMacIdentity,
) : RelayWebSocketFactory {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun open(
        url: String,
        headers: Map<String, String>,
        events: Channel<RelayWireEvent>,
    ): RelayWebSocket {
        events.trySend(RelayWireEvent.Opened)

        return object : RelayWebSocket {
            override fun send(text: String): Boolean {
                when (wireKind(text)) {
                    "clientHello" -> respondWithServerHello(text, events)
                    "clientAuth" -> respondWithSecureReady(text, events)
                    "resumeState" -> events.close()
                }
                return true
            }

            override fun close(code: Int, reason: String): Boolean {
                events.close()
                return true
            }
        }
    }

    private fun respondWithServerHello(
        rawText: String,
        events: Channel<RelayWireEvent>,
    ) {
        val clientHello = json.decodeFromString(SecureClientHello.serializer(), rawText)
        val (_, macEphemeralPublicKey) = SecureCrypto.generateX25519KeyPair()
        val serverNonce = ByteArray(32) { 9 }
        val expiresAt = 4_102_444_800_000
        val transcript = SecureCrypto.secureTranscriptBytes(
            sessionId = clientHello.sessionId,
            protocolVersion = remodexSecureProtocolVersion,
            handshakeMode = SecureHandshakeMode.QR_BOOTSTRAP,
            keyEpoch = 1,
            macDeviceId = macDeviceId,
            phoneDeviceId = clientHello.phoneDeviceId,
            macIdentityPublicKey = macIdentity.publicKeyBase64,
            phoneIdentityPublicKey = clientHello.phoneIdentityPublicKey,
            macEphemeralPublicKey = SecureCrypto.encodeBase64(macEphemeralPublicKey),
            phoneEphemeralPublicKey = clientHello.phoneEphemeralPublicKey,
            clientNonce = SecureCrypto.decodeBase64(clientHello.clientNonce),
            serverNonce = serverNonce,
            expiresAtForTranscript = expiresAt,
        )
        val serverHello = SecureServerHello(
            kind = "serverHello",
            protocolVersion = remodexSecureProtocolVersion,
            sessionId = clientHello.sessionId,
            handshakeMode = clientHello.handshakeMode,
            macDeviceId = macDeviceId,
            macIdentityPublicKey = macIdentity.publicKeyBase64,
            macEphemeralPublicKey = SecureCrypto.encodeBase64(macEphemeralPublicKey),
            serverNonce = SecureCrypto.encodeBase64(serverNonce),
            keyEpoch = 1,
            expiresAtForTranscript = expiresAt,
            macSignature = SecureCrypto.signEd25519(
                privateKeyBase64 = macIdentity.privateKeyBase64,
                payload = transcript,
            ),
            clientNonce = clientHello.clientNonce,
        )
        events.trySend(
            RelayWireEvent.Message(
                json.encodeToString(SecureServerHello.serializer(), serverHello),
            ),
        )
    }

    private fun respondWithSecureReady(
        rawText: String,
        events: Channel<RelayWireEvent>,
    ) {
        val clientAuth = json.decodeFromString(SecureClientAuth.serializer(), rawText)
        val secureReady = SecureReadyMessage(
            kind = "secureReady",
            sessionId = clientAuth.sessionId,
            keyEpoch = clientAuth.keyEpoch,
            macDeviceId = macDeviceId,
        )
        events.trySend(
            RelayWireEvent.Message(
                json.encodeToString(SecureReadyMessage.serializer(), secureReady),
            ),
        )
    }

    private fun wireKind(rawText: String): String? {
        return runCatching {
            json.parseToJsonElement(rawText)
                .jsonObject["kind"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    }
}

class ScriptedRpcRelayWebSocketFactory(
    private val macDeviceId: String,
    private val macIdentity: TestMacIdentity,
    private val requestHandlers: Map<String, (RpcMessage) -> JsonElement> = emptyMap(),
) : RelayWebSocketFactory {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    val receivedRequests = mutableListOf<RpcMessage>()
    private var serverContext: ScriptedServerContext? = null

    override fun open(
        url: String,
        headers: Map<String, String>,
        events: Channel<RelayWireEvent>,
    ): RelayWebSocket {
        events.trySend(RelayWireEvent.Opened)

        return object : RelayWebSocket {
            override fun send(text: String): Boolean {
                when (wireKind(text)) {
                    "clientHello" -> handleClientHello(text, events)
                    "clientAuth" -> handleClientAuth(text, events)
                    "resumeState" -> Unit
                    "encryptedEnvelope" -> handleEncryptedEnvelope(text, events)
                }
                return true
            }

            override fun close(code: Int, reason: String): Boolean {
                events.close()
                return true
            }
        }
    }

    private fun handleClientHello(
        rawText: String,
        events: Channel<RelayWireEvent>,
    ) {
        val clientHello = json.decodeFromString(SecureClientHello.serializer(), rawText)
        val macEphemeralPrivateKey = X25519PrivateKeyParameters(SecureRandom())
        val macEphemeralPublicKey = ByteArray(32)
        macEphemeralPrivateKey.generatePublicKey().encode(macEphemeralPublicKey, 0)
        val serverNonce = ByteArray(32) { 9 }
        val expiresAt = 4_102_444_800_000
        val transcript = SecureCrypto.secureTranscriptBytes(
            sessionId = clientHello.sessionId,
            protocolVersion = remodexSecureProtocolVersion,
            handshakeMode = SecureHandshakeMode.QR_BOOTSTRAP,
            keyEpoch = 1,
            macDeviceId = macDeviceId,
            phoneDeviceId = clientHello.phoneDeviceId,
            macIdentityPublicKey = macIdentity.publicKeyBase64,
            phoneIdentityPublicKey = clientHello.phoneIdentityPublicKey,
            macEphemeralPublicKey = SecureCrypto.encodeBase64(macEphemeralPublicKey),
            phoneEphemeralPublicKey = clientHello.phoneEphemeralPublicKey,
            clientNonce = SecureCrypto.decodeBase64(clientHello.clientNonce),
            serverNonce = serverNonce,
            expiresAtForTranscript = expiresAt,
        )
        serverContext = ScriptedServerContext(
            clientHello = clientHello,
            macEphemeralPrivateKeyBase64 = SecureCrypto.encodeBase64(macEphemeralPrivateKey.encoded),
            transcript = transcript,
        )
        val serverHello = SecureServerHello(
            kind = "serverHello",
            protocolVersion = remodexSecureProtocolVersion,
            sessionId = clientHello.sessionId,
            handshakeMode = clientHello.handshakeMode,
            macDeviceId = macDeviceId,
            macIdentityPublicKey = macIdentity.publicKeyBase64,
            macEphemeralPublicKey = SecureCrypto.encodeBase64(macEphemeralPublicKey),
            serverNonce = SecureCrypto.encodeBase64(serverNonce),
            keyEpoch = 1,
            expiresAtForTranscript = expiresAt,
            macSignature = SecureCrypto.signEd25519(
                privateKeyBase64 = macIdentity.privateKeyBase64,
                payload = transcript,
            ),
            clientNonce = clientHello.clientNonce,
        )
        events.trySend(RelayWireEvent.Message(json.encodeToString(SecureServerHello.serializer(), serverHello)))
    }

    private fun handleClientAuth(
        rawText: String,
        events: Channel<RelayWireEvent>,
    ) {
        val clientAuth = json.decodeFromString(SecureClientAuth.serializer(), rawText)
        val context = serverContext ?: error("clientAuth arrived before clientHello")
        val sharedSecret = SecureCrypto.sharedSecret(
            privateKeyBase64 = context.macEphemeralPrivateKeyBase64,
            publicKeyBase64 = context.clientHello.phoneEphemeralPublicKey,
        )
        val salt = java.security.MessageDigest.getInstance("SHA-256").digest(context.transcript)
        val infoPrefix = listOf(
            remodexSecureHandshakeTag,
            context.clientHello.sessionId,
            macDeviceId,
            context.clientHello.phoneDeviceId,
            clientAuth.keyEpoch.toString(),
        ).joinToString("|")
        context.macSession = ScriptedMacSecureSession(
            sessionId = context.clientHello.sessionId,
            keyEpoch = clientAuth.keyEpoch,
            phoneToMacKey = SecureCrypto.hkdfSha256(sharedSecret, salt, "$infoPrefix|phoneToMac"),
            macToPhoneKey = SecureCrypto.hkdfSha256(sharedSecret, salt, "$infoPrefix|macToPhone"),
        )
        val secureReady = SecureReadyMessage(
            kind = "secureReady",
            sessionId = clientAuth.sessionId,
            keyEpoch = clientAuth.keyEpoch,
            macDeviceId = macDeviceId,
        )
        events.trySend(RelayWireEvent.Message(json.encodeToString(SecureReadyMessage.serializer(), secureReady)))
    }

    private fun handleEncryptedEnvelope(
        rawText: String,
        events: Channel<RelayWireEvent>,
    ) {
        val context = serverContext ?: error("encryptedEnvelope arrived before handshake")
        val session = context.macSession ?: error("secure session missing")
        val envelope = json.decodeFromString(SecureEnvelope.serializer(), rawText)
        val plaintext = SecureCrypto.decryptAesGcm(
            ciphertextBase64 = envelope.ciphertext,
            tagBase64 = envelope.tag,
            key = session.phoneToMacKey,
            nonce = SecureCrypto.secureNonce(sender = envelope.sender, counter = envelope.counter),
        ) ?: error("Unable to decrypt test envelope")
        val payload = json.decodeFromString(SecureApplicationPayload.serializer(), plaintext.decodeToString())
        val message = json.decodeFromString(RpcMessage.serializer(), payload.payloadText)
        receivedRequests += message

        if (message.method == null || message.id == null) {
            return
        }

        val response = try {
            val result = requestHandlers[message.method]?.invoke(message) ?: buildJsonObject { }
            RpcMessage.response(id = message.id, result = result)
        } catch (error: RpcError) {
            RpcMessage.error(
                id = message.id,
                code = error.code,
                message = error.message,
                data = error.data,
            )
        }
        val responsePayload = SecureApplicationPayload(
            bridgeOutboundSeq = ++session.bridgeOutboundSeq,
            payloadText = json.encodeToString(RpcMessage.serializer(), response),
        )
        val responseBody = json.encodeToString(SecureApplicationPayload.serializer(), responsePayload).encodeToByteArray()
        val encrypted = SecureCrypto.encryptAesGcm(
            plaintext = responseBody,
            key = session.macToPhoneKey,
            nonce = SecureCrypto.secureNonce(sender = "mac", counter = session.nextMacCounter),
        )
        val responseEnvelope = SecureEnvelope(
            kind = "encryptedEnvelope",
            v = remodexSecureProtocolVersion,
            sessionId = session.sessionId,
            keyEpoch = session.keyEpoch,
            sender = "mac",
            counter = session.nextMacCounter,
            ciphertext = encrypted.first,
            tag = encrypted.second,
        )
        session.nextMacCounter += 1
        events.trySend(RelayWireEvent.Message(json.encodeToString(SecureEnvelope.serializer(), responseEnvelope)))
    }

    private fun wireKind(rawText: String): String? {
        return runCatching {
            json.parseToJsonElement(rawText)
                .jsonObject["kind"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    }
}

private data class ScriptedServerContext(
    val clientHello: SecureClientHello,
    val macEphemeralPrivateKeyBase64: String,
    val transcript: ByteArray,
    var macSession: ScriptedMacSecureSession? = null,
)

private data class ScriptedMacSecureSession(
    val sessionId: String,
    val keyEpoch: Int,
    val phoneToMacKey: ByteArray,
    val macToPhoneKey: ByteArray,
    var nextMacCounter: Int = 0,
    var bridgeOutboundSeq: Int = 0,
)

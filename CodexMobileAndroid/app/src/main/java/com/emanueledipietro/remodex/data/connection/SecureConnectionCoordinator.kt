package com.emanueledipietro.remodex.data.connection

import android.util.Log
import com.emanueledipietro.remodex.model.remodexBridgeUpdateCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SecureConnectionCoordinator(
    private val store: SecureStore,
    private val trustedSessionResolver: TrustedSessionResolver,
    private val relayWebSocketFactory: RelayWebSocketFactory,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val logTag = "RemodexSecureConn"
        private const val maxTrustedReconnectFailures = 3
        private val permanentRelayCloseCodes = setOf(4000, 4001, 4003)
        private const val retryableSessionUnavailableCloseCode = 4002
        private const val explicitRelayDropCloseCode = 4004
        private const val trustedReconnectRecoveryMessage =
            "Secure reconnect could not be restored from the saved session. Try reconnecting again."
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val phoneIdentityState = SecureCrypto.phoneIdentityStateFromStore(store)
    private var trustedMacRegistry = SecureCrypto.trustedMacRegistryFromStore(store)
    private var relayProfileRegistry = SecureCrypto.relayProfileRegistryFromStore(store)
    private var pairingState = SecureCrypto.relayPairingStateFromStore(store)
    private var secureSession: SecureSession? = null
    private var pendingHandshake: SecurePendingHandshake? = null
    private var socket: RelayWebSocket? = null
    private var socketEvents: Channel<RelayWireEvent>? = null
    private var inboundListenerJob: Job? = null
    private var connectionJob: Job? = null
    private var currentAttempt = 0
    private var trustedReconnectFailureCount = 0
    private var autoReconnectAllowed = true
    private val pendingRequests = LinkedHashMap<String, kotlinx.coroutines.CancellableContinuation<RpcMessage>>()
    private val outboundMutex = Mutex()

    private val connectionState = MutableStateFlow(initialSnapshot())
    private val bridgeProfilesState = MutableStateFlow(currentBridgeProfilesSnapshot())
    private val notificationsChannel = Channel<RpcMessage>(Channel.UNLIMITED)
    private val requestsChannel = Channel<RpcMessage>(Channel.UNLIMITED)
    val state: StateFlow<SecureConnectionSnapshot> = connectionState.asStateFlow()
    val bridgeProfiles: StateFlow<BridgeProfilesSnapshot> = bridgeProfilesState.asStateFlow()
    val notifications: Flow<RpcMessage> = notificationsChannel.receiveAsFlow()
    val requests: Flow<RpcMessage> = requestsChannel.receiveAsFlow()

    suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
        timeoutMs: Long = 15_000L,
    ): RpcMessage {
        requireEncryptedSession()
        val requestId = JsonPrimitive(java.util.UUID.randomUUID().toString())
        val requestKey = rpcIdKey(requestId)
            ?: throw SecureTransportException("The secure Android request ID could not be created.")
        val request = RpcMessage.request(
            id = requestId,
            method = method,
            params = params,
        )

        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests[requestKey] = continuation
                continuation.invokeOnCancellation {
                    pendingRequests.remove(requestKey)
                }
                scope.launch {
                    try {
                        sendSecureApplicationRpc(request)
                    } catch (error: Exception) {
                        val pending = pendingRequests.remove(requestKey)
                        pending?.resumeWithException(error)
                    }
                }
            }
        }
    }

    suspend fun sendNotification(
        method: String,
        params: JsonElement? = null,
    ) {
        requireEncryptedSession()
        sendSecureApplicationRpc(
            RpcMessage.notification(
                method = method,
                params = params,
            ),
        )
    }

    suspend fun sendResponse(
        id: JsonElement?,
        result: JsonElement,
    ) {
        sendSecureApplicationRpc(RpcMessage.response(id = id, result = result))
    }

    suspend fun sendErrorResponse(
        id: JsonElement?,
        code: Int,
        message: String,
        data: JsonElement? = null,
    ) {
        sendSecureApplicationRpc(
            RpcMessage.error(
                id = id,
                code = code,
                message = message,
                data = data,
            ),
        )
    }

    fun rememberRelayPairing(payload: PairingQrPayload) {
        autoReconnectAllowed = true
        logConnection(
            event = "rememberRelayPairing",
            extra = "macDeviceId=${payload.macDeviceId} trusted=${trustedMacRegistry.records[payload.macDeviceId] != null}",
        )
        val profileId = relayProfileRegistry.profiles.values
            .firstOrNull { it.macDeviceId == payload.macDeviceId }
            ?.profileId
            ?: java.util.UUID.randomUUID().toString()
        SecureCrypto.rememberRelayPairing(store, payload, profileId)
        refreshProfileState()
        val trustedMac = trustedMacRegistry.records[payload.macDeviceId]
        updateState(
            phaseMessage = if (trustedMac == null) {
                "QR pairing saved for a new Mac. Open the local bridge to start the secure handshake."
            } else {
                "QR pairing refreshed for your trusted Mac. Retry reconnect to re-open the secure session."
            },
            secureState = if (trustedMac == null) SecureConnectionState.HANDSHAKING else SecureConnectionState.TRUSTED_MAC,
            activeProfileId = pairingState?.profileId,
            relayUrl = payload.relay,
            macDeviceId = payload.macDeviceId,
            macFingerprint = SecureCrypto.fingerprint(payload.macIdentityPublicKey),
            bridgeUpdateCommand = null,
        )
        publishBridgeProfiles()
    }

    fun retryConnection() {
        if (connectionJob?.isActive == true) {
            logConnection(event = "retryConnectionSkipped", extra = "reason=connectionJobActive")
            return
        }
        autoReconnectAllowed = true
        logConnection(
            event = "retryConnectionRequested",
            extra = "hasPairing=${pairingState != null} trustedMac=${pairingState?.macDeviceId?.let(trustedMacRegistry.records::containsKey) == true}",
        )
        if (pairingState == null) {
            updateState(
                phaseMessage = "Run remodex up on your Mac, then scan a fresh pairing QR code before retrying.",
                secureState = SecureConnectionState.NOT_PAIRED,
            )
            return
        }

        connectionJob = scope.launch {
            performRetryConnection()
        }.also { job ->
            job.invokeOnCompletion {
                if (connectionJob === job) {
                    connectionJob = null
                }
            }
        }
    }

    fun disconnect() {
        logConnection(event = "disconnect")
        connectionJob?.cancel()
        connectionJob = null
        disconnectCurrentSocket(
            pendingRequestError = CancellationException(
                "The secure Android connection was closed before the request completed.",
            ),
        )
        connectionState.value = initialSnapshot().copy(attempt = currentAttempt)
    }

    fun activateBridgeProfile(profileId: String): Boolean {
        if (!relayProfileRegistry.profiles.containsKey(profileId)) {
            return false
        }
        disconnect()
        relayProfileRegistry = SecureCrypto.activateRelayProfile(store, relayProfileRegistry, profileId)
        refreshProfileState()
        connectionState.value = initialSnapshot().copy(attempt = currentAttempt)
        publishBridgeProfiles()
        return true
    }

    fun removeBridgeProfile(profileId: String): String? {
        val removedProfile = relayProfileRegistry.profiles[profileId] ?: return relayProfileRegistry.activeProfileId
        if (relayProfileRegistry.activeProfileId == profileId) {
            disconnect()
        }
        trustedMacRegistry = TrustedMacRegistry(
            records = trustedMacRegistry.records.toMutableMap().apply {
                remove(removedProfile.macDeviceId)
            },
        )
        SecureCrypto.writeTrustedMacRegistry(
            store = store,
            registry = trustedMacRegistry,
            lastTrustedMacDeviceId = trustedMacRegistry.records.keys.firstOrNull(),
        )
        relayProfileRegistry = SecureCrypto.removeRelayProfile(
            store = store,
            registry = relayProfileRegistry,
            profileId = profileId,
            fallbackProfileId = relayProfileRegistry.profiles.values
                .filterNot { it.profileId == profileId }
                .maxByOrNull { it.lastUsedAtEpochMs ?: it.createdAtEpochMs }
                ?.profileId,
        )
        refreshProfileState()
        connectionState.value = initialSnapshot().copy(attempt = currentAttempt)
        publishBridgeProfiles()
        return relayProfileRegistry.activeProfileId
    }

    fun forgetTrustedMac() {
        pairingState?.profileId?.let(::removeBridgeProfile)
    }

    private suspend fun performRetryConnection() {
        val currentPairingState = pairingState ?: return
        disconnectCurrentSocket()
        currentAttempt += 1

        try {
            val trustedMac = trustedMacRegistry.records[currentPairingState.macDeviceId]
            val handshakeMode = if (!currentPairingState.shouldForceQrBootstrap && trustedMac != null) {
                SecureHandshakeMode.TRUSTED_RECONNECT
            } else {
                SecureHandshakeMode.QR_BOOTSTRAP
            }
            logConnection(
                event = "performRetryConnection",
                extra = "attempt=$currentAttempt handshakeMode=${handshakeMode.wireValue} macDeviceId=${currentPairingState.macDeviceId}",
            )

            val resolvedPairingState = if (handshakeMode == SecureHandshakeMode.TRUSTED_RECONNECT) {
                updateState(
                    phaseMessage = "Resolving the live session for your trusted Mac before reconnecting the Android socket.",
                    secureState = SecureConnectionState.RECONNECTING,
                    relayUrl = trustedMac?.relayUrl ?: currentPairingState.relayUrl,
                    macDeviceId = currentPairingState.macDeviceId,
                    macFingerprint = trustedMac?.macIdentityPublicKey?.let(SecureCrypto::fingerprint)
                        ?: SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
                    bridgeUpdateCommand = null,
                )
                resolveTrustedSession(currentPairingState, trustedMac)
            } else {
                updateState(
                    phaseMessage = "Opening the relay socket and starting secure QR bootstrap with your Mac.",
                    secureState = SecureConnectionState.HANDSHAKING,
                    relayUrl = currentPairingState.relayUrl,
                    macDeviceId = currentPairingState.macDeviceId,
                    macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
                    bridgeUpdateCommand = null,
                )
                currentPairingState
            }

            connectAndHandshake(resolvedPairingState, handshakeMode)
        } catch (error: TrustedSessionResolveException) {
            handleResolveError(currentPairingState, error)
        } catch (error: SecureTransportException) {
            handleTransportError(currentPairingState, error)
        } catch (error: Exception) {
            updateState(
                phaseMessage = error.message ?: "The secure Android connection failed before the bridge finished the handshake.",
                secureState = SecureConnectionState.REPAIR_REQUIRED,
                relayUrl = currentPairingState.relayUrl,
                macDeviceId = currentPairingState.macDeviceId,
                macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
                bridgeUpdateCommand = null,
            )
        }
    }

    private suspend fun resolveTrustedSession(
        currentPairingState: RelayPairingState,
        trustedMac: TrustedMacRecord?,
    ): RelayPairingState {
        val trustedRelayUrl = trustedMac?.relayUrl?.trim().takeUnless { it.isNullOrEmpty() }
            ?: currentPairingState.relayUrl
        val nonce = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val signature = SecureCrypto.signEd25519(
            phoneIdentityState.phoneIdentityPrivateKey,
            SecureCrypto.trustedSessionResolveTranscriptBytes(
                macDeviceId = currentPairingState.macDeviceId,
                phoneDeviceId = phoneIdentityState.phoneDeviceId,
                phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
                nonce = nonce,
                timestamp = timestamp,
            ),
        )

        val response = trustedSessionResolver.resolve(
            relayUrl = trustedRelayUrl,
            request = TrustedSessionResolveRequest(
                macDeviceId = currentPairingState.macDeviceId,
                phoneDeviceId = phoneIdentityState.phoneDeviceId,
                phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
                nonce = nonce,
                timestamp = timestamp,
                signature = signature,
            ),
        )

        SecureCrypto.rememberResolvedTrustedSession(
            store = store,
            profileId = currentPairingState.profileId,
            response = response,
            relayUrl = trustedRelayUrl,
            resetReplayCursor = currentPairingState.sessionId != response.sessionId,
        )
        refreshProfileState()
        publishBridgeProfiles()
        updateTrustedMacAfterResolve(response, trustedRelayUrl)
        return pairingState ?: currentPairingState
    }

    private suspend fun connectAndHandshake(
        currentPairingState: RelayPairingState,
        handshakeMode: SecureHandshakeMode,
    ) {
        val sessionUrl = relaySessionUrl(currentPairingState.relayUrl, currentPairingState.sessionId)
            ?: throw SecureTransportException("The relay URL is invalid. Scan a fresh QR code from the bridge.")
        val events = Channel<RelayWireEvent>(Channel.UNLIMITED)
        socketEvents = events
        socket = relayWebSocketFactory.open(
            url = sessionUrl,
            headers = mapOf("x-role" to "iphone"),
            events = events,
        )

        waitForSocketOpen(events)

        val expectedMacIdentityPublicKey = when (handshakeMode) {
            SecureHandshakeMode.TRUSTED_RECONNECT -> {
                trustedMacRegistry.records[currentPairingState.macDeviceId]?.macIdentityPublicKey
                    ?: throw SecureTransportException("The trusted Mac record is missing. Scan a fresh QR code to reconnect.")
            }

            SecureHandshakeMode.QR_BOOTSTRAP -> currentPairingState.macIdentityPublicKey
        }

        val (phoneEphemeralPrivateKey, phoneEphemeralPublicKey) = SecureCrypto.generateX25519KeyPair()
        val clientNonce = SecureCrypto.randomNonce()
        val clientHello = SecureClientHello(
            protocolVersion = remodexSecureProtocolVersion,
            sessionId = currentPairingState.sessionId,
            handshakeMode = handshakeMode.wireValue,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
            phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
            phoneEphemeralPublicKey = SecureCrypto.encodeBase64(phoneEphemeralPublicKey),
            clientNonce = SecureCrypto.encodeBase64(clientNonce),
        )
        pendingHandshake = SecurePendingHandshake(
            mode = handshakeMode,
            transcriptBytes = ByteArray(0),
            phoneEphemeralPrivateKey = phoneEphemeralPrivateKey,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
        )
        sendWireControlMessage(clientHello)

        val serverHello = waitForServerHello(
            events = events,
            expectedSessionId = currentPairingState.sessionId,
            expectedMacDeviceId = currentPairingState.macDeviceId,
            expectedMacIdentityPublicKey = expectedMacIdentityPublicKey,
            expectedClientNonce = clientHello.clientNonce,
        )

        if (serverHello.protocolVersion != remodexSecureProtocolVersion) {
            throw SecureTransportException(
                "This bridge is using a different secure transport version. Update Remodex on your Mac and try again.",
            )
        }

        val transcriptBytes = SecureCrypto.secureTranscriptBytes(
            sessionId = currentPairingState.sessionId,
            protocolVersion = serverHello.protocolVersion,
            handshakeMode = handshakeMode,
            keyEpoch = serverHello.keyEpoch,
            macDeviceId = serverHello.macDeviceId,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
            macIdentityPublicKey = serverHello.macIdentityPublicKey,
            phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
            macEphemeralPublicKey = serverHello.macEphemeralPublicKey,
            phoneEphemeralPublicKey = clientHello.phoneEphemeralPublicKey,
            clientNonce = clientNonce,
            serverNonce = SecureCrypto.decodeBase64(serverHello.serverNonce),
            expiresAtForTranscript = serverHello.expiresAtForTranscript,
        )

        val signatureValid = SecureCrypto.verifyEd25519(
            publicKeyBase64 = serverHello.macIdentityPublicKey,
            payload = transcriptBytes,
            signatureBase64 = serverHello.macSignature,
        )
        if (!signatureValid) {
            throw SecureTransportException("The secure Mac signature could not be verified.")
        }

        pendingHandshake = SecurePendingHandshake(
            mode = handshakeMode,
            transcriptBytes = transcriptBytes,
            phoneEphemeralPrivateKey = phoneEphemeralPrivateKey,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
        )

        sendWireControlMessage(
            SecureClientAuth(
                sessionId = currentPairingState.sessionId,
                phoneDeviceId = phoneIdentityState.phoneDeviceId,
                keyEpoch = serverHello.keyEpoch,
                phoneSignature = SecureCrypto.signEd25519(
                    privateKeyBase64 = phoneIdentityState.phoneIdentityPrivateKey,
                    payload = SecureCrypto.clientAuthTranscript(transcriptBytes),
                ),
            ),
        )

        val secureReady = waitForSecureReady(
            events = events,
            expectedSessionId = currentPairingState.sessionId,
            expectedMacDeviceId = currentPairingState.macDeviceId,
            expectedKeyEpoch = serverHello.keyEpoch,
        )

        val sharedSecret = SecureCrypto.sharedSecret(
            privateKeyBase64 = SecureCrypto.encodeBase64(phoneEphemeralPrivateKey),
            publicKeyBase64 = serverHello.macEphemeralPublicKey,
        )
        val salt = java.security.MessageDigest.getInstance("SHA-256").digest(transcriptBytes)
        val infoPrefix = listOf(
            remodexSecureHandshakeTag,
            currentPairingState.sessionId,
            currentPairingState.macDeviceId,
            phoneIdentityState.phoneDeviceId,
            serverHello.keyEpoch.toString(),
        ).joinToString("|")
        secureSession = SecureSession(
            sessionId = currentPairingState.sessionId,
            keyEpoch = serverHello.keyEpoch,
            macDeviceId = currentPairingState.macDeviceId,
            macIdentityPublicKey = serverHello.macIdentityPublicKey,
            phoneToMacKey = SecureCrypto.hkdfSha256(sharedSecret, salt, "$infoPrefix|phoneToMac"),
            macToPhoneKey = SecureCrypto.hkdfSha256(sharedSecret, salt, "$infoPrefix|macToPhone"),
            lastInboundBridgeOutboundSeq = currentPairingState.lastAppliedBridgeOutboundSeq,
            lastInboundCounter = -1,
            nextOutboundCounter = 0,
        )
        pendingHandshake = null
        trustedReconnectFailureCount = 0
        autoReconnectAllowed = true
        logConnection(
            event = "secureHandshakeComplete",
            extra = "handshakeMode=${handshakeMode.wireValue} macDeviceId=${currentPairingState.macDeviceId}",
        )
        relayProfileRegistry = pairingState?.profileId?.let { profileId ->
            SecureCrypto.updateRelayProfile(store, relayProfileRegistry, profileId) { existing ->
                existing.copy(
                    shouldForceQrBootstrap = false,
                    macIdentityPublicKey = serverHello.macIdentityPublicKey,
                    lastUsedAtEpochMs = System.currentTimeMillis(),
                )
            }
        } ?: relayProfileRegistry
        refreshProfileState()
        publishBridgeProfiles()

        if (handshakeMode == SecureHandshakeMode.QR_BOOTSTRAP) {
            trustMac(
                deviceId = currentPairingState.macDeviceId,
                publicKey = serverHello.macIdentityPublicKey,
                relayUrl = currentPairingState.relayUrl,
                displayName = trustedMacRegistry.records[currentPairingState.macDeviceId]?.displayName,
            )
        }

        sendWireControlMessage(
            SecureResumeState(
                sessionId = currentPairingState.sessionId,
                keyEpoch = secureReady.keyEpoch,
                lastAppliedBridgeOutboundSeq = pairingState?.lastAppliedBridgeOutboundSeq ?: 0,
            ),
        )

        updateState(
            phaseMessage = "Secure handshake complete. Android is connected to your trusted Remodex session.",
            secureState = SecureConnectionState.ENCRYPTED,
            relayUrl = currentPairingState.relayUrl,
            macDeviceId = currentPairingState.macDeviceId,
            macFingerprint = SecureCrypto.fingerprint(serverHello.macIdentityPublicKey),
            bridgeUpdateCommand = null,
        )

        inboundListenerJob?.cancel()
        inboundListenerJob = scope.launch {
            listenForEncryptedMessages(events, currentPairingState)
        }
    }

    private suspend fun waitForSocketOpen(events: Channel<RelayWireEvent>) {
        when (val event = events.receive()) {
            RelayWireEvent.Opened -> return
            is RelayWireEvent.Failure -> throw SecureTransportException(
                event.throwable.message ?: "Could not open the relay socket for secure pairing.",
            )
            is RelayWireEvent.Closed -> throw SecureTransportException(
                "The relay socket closed before the secure handshake could start.",
                relayCloseCode = event.code,
            )
            is RelayWireEvent.Message -> throw SecureTransportException(
                "The relay sent unexpected data before the secure socket opened.",
            )
        }
    }

    private suspend fun waitForServerHello(
        events: Channel<RelayWireEvent>,
        expectedSessionId: String,
        expectedMacDeviceId: String,
        expectedMacIdentityPublicKey: String,
        expectedClientNonce: String,
    ): SecureServerHello {
        while (true) {
            val text = waitForControlMessage(events, "serverHello")
            val hello = json.decodeFromString(SecureServerHello.serializer(), text)
            if (hello.sessionId != expectedSessionId || hello.macDeviceId != expectedMacDeviceId) {
                continue
            }
            if (hello.macIdentityPublicKey != expectedMacIdentityPublicKey) {
                throw SecureTransportException("The secure Mac identity key did not match the paired device.")
            }
            if (hello.clientNonce != null && hello.clientNonce != expectedClientNonce) {
                continue
            }
            return hello
        }
    }

    private suspend fun waitForSecureReady(
        events: Channel<RelayWireEvent>,
        expectedSessionId: String,
        expectedMacDeviceId: String,
        expectedKeyEpoch: Int,
    ): SecureReadyMessage {
        while (true) {
            val text = waitForControlMessage(events, "secureReady")
            val ready = json.decodeFromString(SecureReadyMessage.serializer(), text)
            if (ready.sessionId == expectedSessionId
                && ready.macDeviceId == expectedMacDeviceId
                && ready.keyEpoch == expectedKeyEpoch
            ) {
                return ready
            }
        }
    }

    private suspend fun waitForControlMessage(
        events: Channel<RelayWireEvent>,
        expectedKind: String,
    ): String {
        while (true) {
            when (val event = events.receive()) {
                RelayWireEvent.Opened -> continue
                is RelayWireEvent.Failure -> {
                    throw SecureTransportException(
                        event.throwable.message ?: "The relay socket failed during secure pairing.",
                    )
                }

                is RelayWireEvent.Closed -> {
                    throw SecureTransportException(
                        "The relay socket closed before the secure handshake finished.",
                        relayCloseCode = event.code,
                    )
                }

                is RelayWireEvent.Message -> {
                    val kind = wireMessageKind(event.text)
                    when (kind) {
                        "secureError" -> {
                            val secureError = json.decodeFromString(SecureErrorMessage.serializer(), event.text)
                            throw secureError.toException()
                        }

                        "encryptedEnvelope" -> {
                            processEncryptedEnvelope(event.text)
                        }

                        expectedKind -> return event.text
                    }
                }
            }
        }
    }

    private suspend fun listenForEncryptedMessages(
        events: Channel<RelayWireEvent>,
        currentPairingState: RelayPairingState,
    ) {
        for (event in events) {
            when (event) {
                RelayWireEvent.Opened -> Unit
                is RelayWireEvent.Message -> {
                    when (wireMessageKind(event.text)) {
                        "encryptedEnvelope" -> processEncryptedEnvelope(event.text)
                        "secureError" -> {
                            val secureError = json.decodeFromString(SecureErrorMessage.serializer(), event.text)
                            handleTransportError(currentPairingState, secureError.toException())
                        }
                    }
                }

                is RelayWireEvent.Failure -> {
                    handleTransportError(
                        currentPairingState,
                        SecureTransportException(
                            event.throwable.message ?: "The relay socket dropped during secure reconnect.",
                        ),
                    )
                    return
                }

                is RelayWireEvent.Closed -> {
                    handleTransportError(
                        currentPairingState,
                        SecureTransportException(
                            "The relay socket closed during secure reconnect.",
                            relayCloseCode = event.code,
                        ),
                    )
                    return
                }
            }
        }
    }

    private fun processEncryptedEnvelope(rawText: String) {
        val activeSecureSession = secureSession ?: return
        val envelope = runCatching {
            json.decodeFromString(SecureEnvelope.serializer(), rawText)
        }.getOrNull() ?: return
        if (envelope.sessionId != activeSecureSession.sessionId
            || envelope.keyEpoch != activeSecureSession.keyEpoch
            || envelope.sender != "mac"
            || envelope.counter <= activeSecureSession.lastInboundCounter
        ) {
            return
        }

        val plaintext = SecureCrypto.decryptAesGcm(
            ciphertextBase64 = envelope.ciphertext,
            tagBase64 = envelope.tag,
            key = activeSecureSession.macToPhoneKey,
            nonce = SecureCrypto.secureNonce(sender = envelope.sender, counter = envelope.counter),
        ) ?: return

        val payload = runCatching {
            json.decodeFromString(SecureApplicationPayload.serializer(), plaintext.decodeToString())
        }.getOrNull() ?: return

        activeSecureSession.lastInboundCounter = envelope.counter
        payload.bridgeOutboundSeq?.let { bridgeOutboundSeq ->
            if (bridgeOutboundSeq > activeSecureSession.lastInboundBridgeOutboundSeq) {
                activeSecureSession.lastInboundBridgeOutboundSeq = bridgeOutboundSeq
                relayProfileRegistry = pairingState?.profileId?.let { profileId ->
                    SecureCrypto.updateRelayProfile(store, relayProfileRegistry, profileId) { existing ->
                        existing.copy(
                            lastAppliedBridgeOutboundSeq = bridgeOutboundSeq,
                            lastUsedAtEpochMs = System.currentTimeMillis(),
                        )
                    }
                } ?: relayProfileRegistry
                refreshProfileState()
                publishBridgeProfiles()
            }
        }

        routeIncomingPayload(payload.payloadText)
    }

    private fun routeIncomingPayload(payloadText: String) {
        val rpcMessage = runCatching {
            json.decodeFromString(RpcMessage.serializer(), payloadText)
        }.getOrNull() ?: return

        val requestKey = rpcIdKey(rpcMessage.id)
        when {
            rpcMessage.method != null && rpcMessage.id == null -> {
                notificationsChannel.trySend(rpcMessage)
            }

            rpcMessage.method != null && rpcMessage.id != null -> {
                requestsChannel.trySend(rpcMessage)
            }

            requestKey != null -> {
                val continuation = pendingRequests.remove(requestKey) ?: return
                if (rpcMessage.error != null) {
                    continuation.resumeWithException(rpcMessage.error)
                } else {
                    continuation.resume(rpcMessage)
                }
            }
        }
    }

    private suspend fun sendSecureApplicationRpc(message: RpcMessage) {
        val payload = json.encodeToString(RpcMessage.serializer(), message)
        outboundMutex.withLock {
            val wireText = secureWireText(payload)
            val sent = socket?.send(wireText) == true
            if (!sent) {
                throw SecureTransportException("Unable to send the secure Android RPC payload.")
            }
        }
    }

    private fun secureWireText(plaintext: String): String {
        val activeSecureSession = secureSession
            ?: throw SecureTransportException("The secure Android session is not ready yet. Retry reconnect.")
        val payload = SecureApplicationPayload(
            bridgeOutboundSeq = null,
            payloadText = plaintext,
        )
        val payloadData = json.encodeToString(SecureApplicationPayload.serializer(), payload).encodeToByteArray()
        val counter = activeSecureSession.nextOutboundCounter
        val encrypted = SecureCrypto.encryptAesGcm(
            plaintext = payloadData,
            key = activeSecureSession.phoneToMacKey,
            nonce = SecureCrypto.secureNonce(sender = "iphone", counter = counter),
        )
        val envelope = SecureEnvelope(
            kind = "encryptedEnvelope",
            v = remodexSecureProtocolVersion,
            sessionId = activeSecureSession.sessionId,
            keyEpoch = activeSecureSession.keyEpoch,
            sender = "iphone",
            counter = counter,
            ciphertext = encrypted.first,
            tag = encrypted.second,
        )
        activeSecureSession.nextOutboundCounter = counter + 1
        secureSession = activeSecureSession
        return json.encodeToString(SecureEnvelope.serializer(), envelope)
    }

    private fun requireEncryptedSession() {
        if (state.value.secureState != SecureConnectionState.ENCRYPTED || secureSession == null) {
            throw SecureTransportException("The secure Android session is not connected yet.")
        }
    }

    private suspend fun sendWireControlMessage(value: Any) {
        val payload = when (value) {
            is SecureClientHello -> json.encodeToString(SecureClientHello.serializer(), value)
            is SecureClientAuth -> json.encodeToString(SecureClientAuth.serializer(), value)
            is SecureResumeState -> json.encodeToString(SecureResumeState.serializer(), value)
            else -> throw IllegalArgumentException("Unsupported secure control payload: $value")
        }

        val sent = socket?.send(payload) == true
        if (!sent) {
            throw SecureTransportException("Unable to send the secure Remodex control payload.")
        }
    }

    private fun trustMac(
        deviceId: String,
        publicKey: String,
        relayUrl: String?,
        displayName: String?,
    ) {
        val existing = trustedMacRegistry.records[deviceId]
        val updatedRecord = TrustedMacRecord(
            macDeviceId = deviceId,
            macIdentityPublicKey = publicKey,
            lastPairedAtEpochMs = System.currentTimeMillis(),
            relayUrl = relayUrl ?: existing?.relayUrl,
            displayName = displayName ?: existing?.displayName,
            lastResolvedSessionId = existing?.lastResolvedSessionId,
            lastResolvedAtEpochMs = existing?.lastResolvedAtEpochMs,
            lastUsedAtEpochMs = System.currentTimeMillis(),
        )
        trustedMacRegistry = TrustedMacRegistry(
            records = trustedMacRegistry.records + (deviceId to updatedRecord),
        )
        SecureCrypto.writeTrustedMacRegistry(store, trustedMacRegistry, deviceId)
        publishBridgeProfiles()
    }

    private fun updateTrustedMacAfterResolve(
        response: TrustedSessionResolveResponse,
        relayUrl: String,
    ) {
        val existing = trustedMacRegistry.records[response.macDeviceId]
        val updatedRecord = TrustedMacRecord(
            macDeviceId = response.macDeviceId,
            macIdentityPublicKey = response.macIdentityPublicKey,
            lastPairedAtEpochMs = existing?.lastPairedAtEpochMs ?: System.currentTimeMillis(),
            relayUrl = relayUrl,
            displayName = response.displayName ?: existing?.displayName,
            lastResolvedSessionId = response.sessionId,
            lastResolvedAtEpochMs = System.currentTimeMillis(),
            lastUsedAtEpochMs = System.currentTimeMillis(),
        )
        trustedMacRegistry = TrustedMacRegistry(
            records = trustedMacRegistry.records + (response.macDeviceId to updatedRecord),
        )
        SecureCrypto.writeTrustedMacRegistry(store, trustedMacRegistry, response.macDeviceId)
        publishBridgeProfiles()
    }

    private fun permanentRelayDisconnectMessage(relayCloseCode: Int?): String? {
        return when (relayCloseCode) {
            4001 -> "This relay session was replaced by another Mac connection. Scan a new QR code to reconnect."
            4003 -> "This device was replaced by a newer connection. Scan a new QR code to reconnect."
            4000 -> "This relay pairing is no longer valid. Scan a new QR code to reconnect."
            else -> null
        }
    }

    private fun retryableSessionUnavailableMessage(relayCloseCode: Int?): String? {
        if (relayCloseCode != retryableSessionUnavailableCloseCode) {
            return null
        }
        return "The trusted Mac session is temporarily unavailable. Remodex will keep retrying. If you restarted the bridge on your Mac, scan the new QR code."
    }

    private fun explicitRelayDropMessage(relayCloseCode: Int?): String? {
        if (relayCloseCode != explicitRelayDropCloseCode) {
            return null
        }
        return "The Mac was temporarily unavailable and this message could not be delivered. Wait a moment, then try again."
    }

    private fun isTrustedReconnectAttempt(): Boolean {
        return pendingHandshake?.mode == SecureHandshakeMode.TRUSTED_RECONNECT
            || connectionState.value.secureState == SecureConnectionState.RECONNECTING
    }

    private fun isPermanentRelayClose(relayCloseCode: Int?): Boolean {
        return relayCloseCode != null && permanentRelayCloseCodes.contains(relayCloseCode)
    }

    private fun recoverTrustedReconnectCandidate(
        currentPairingState: RelayPairingState,
    ) {
        trustedReconnectFailureCount = 0
        autoReconnectAllowed = false
        logConnection(
            event = "trustedReconnectRecoveryRequired",
            extra = "macDeviceId=${currentPairingState.macDeviceId}",
        )
        updateState(
            phaseMessage = trustedReconnectRecoveryMessage,
            secureState = if (trustedMacRegistry.records[currentPairingState.macDeviceId] != null) {
                SecureConnectionState.LIVE_SESSION_UNRESOLVED
            } else {
                SecureConnectionState.REPAIR_REQUIRED
            },
            relayUrl = currentPairingState.relayUrl,
            macDeviceId = currentPairingState.macDeviceId,
            macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
            bridgeUpdateCommand = null,
        )
    }

    private fun handleResolveError(
        currentPairingState: RelayPairingState,
        error: TrustedSessionResolveException,
    ) {
        autoReconnectAllowed = error.code == "session_unavailable"
        if (error.code != "session_unavailable") {
            trustedReconnectFailureCount = 0
        }
        logConnection(
            event = "trustedSessionResolveError",
            extra = "code=${error.code} macDeviceId=${currentPairingState.macDeviceId} autoReconnectAllowed=$autoReconnectAllowed message=${error.message.orEmpty()}",
        )
        val nextState = when (error.code) {
            "session_unavailable" -> SecureConnectionState.LIVE_SESSION_UNRESOLVED
            "phone_not_trusted", "invalid_signature" -> SecureConnectionState.REPAIR_REQUIRED
            else -> SecureConnectionState.TRUSTED_MAC
        }
        updateState(
            phaseMessage = when (error.code) {
                "session_unavailable" -> "Your trusted Mac is offline right now."
                "phone_not_trusted", "invalid_signature" -> "This Android device is no longer trusted by the Mac. Scan a new QR code to reconnect."
                "resolve_request_expired", "resolve_request_replayed" -> "The trusted reconnect request expired. Try reconnecting again."
                else -> error.message ?: "The trusted Mac relay could not resolve the current bridge session."
            },
            secureState = nextState,
            relayUrl = currentPairingState.relayUrl,
            macDeviceId = currentPairingState.macDeviceId,
            macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
            bridgeUpdateCommand = null,
        )
    }

    private fun handleTransportError(
        currentPairingState: RelayPairingState,
        error: SecureTransportException,
    ) {
        val wasTrustedReconnectAttempt = isTrustedReconnectAttempt()
        disconnectCurrentSocket()

        val relayCloseCode = error.relayCloseCode
        logConnection(
            event = "transportError",
            extra = "secureErrorCode=${error.code.orEmpty()} relayCloseCode=${relayCloseCode ?: -1} trustedReconnect=$wasTrustedReconnectAttempt macDeviceId=${currentPairingState.macDeviceId} message=${error.message.orEmpty()}",
        )
        if (isPermanentRelayClose(relayCloseCode)) {
            trustedReconnectFailureCount = 0
            autoReconnectAllowed = false
            updateState(
                phaseMessage = permanentRelayDisconnectMessage(relayCloseCode)
                    ?: "This relay pairing is no longer valid. Scan a new QR code to reconnect.",
                secureState = if (trustedMacRegistry.records[currentPairingState.macDeviceId] != null) {
                    SecureConnectionState.LIVE_SESSION_UNRESOLVED
                } else {
                    SecureConnectionState.REPAIR_REQUIRED
                },
                relayUrl = currentPairingState.relayUrl,
                macDeviceId = currentPairingState.macDeviceId,
                macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
                bridgeUpdateCommand = null,
            )
            return
        }

        if (wasTrustedReconnectAttempt) {
            trustedReconnectFailureCount += 1
            logConnection(
                event = "trustedReconnectFailureRecorded",
                extra = "failureCount=$trustedReconnectFailureCount macDeviceId=${currentPairingState.macDeviceId}",
            )
            if (trustedReconnectFailureCount >= maxTrustedReconnectFailures) {
                recoverTrustedReconnectCandidate(currentPairingState)
                return
            }
        } else {
            trustedReconnectFailureCount = 0
        }

        val secureState = when (error.code) {
            "update_required" -> SecureConnectionState.UPDATE_REQUIRED
            "pairing_expired",
            "phone_not_trusted",
            "phone_identity_changed",
            "phone_replacement_required",
            "invalid_signature" -> SecureConnectionState.REPAIR_REQUIRED

            else -> when {
                error.message?.contains("different secure transport version", ignoreCase = true) == true -> {
                    SecureConnectionState.UPDATE_REQUIRED
                }

                error.message?.contains("expired", ignoreCase = true) == true
                    || error.message?.contains("not trusted", ignoreCase = true) == true
                    || error.message?.contains("identity", ignoreCase = true) == true -> {
                    SecureConnectionState.REPAIR_REQUIRED
                }

                trustedMacRegistry.records[currentPairingState.macDeviceId] != null -> {
                    SecureConnectionState.TRUSTED_MAC
                }

                else -> {
                    SecureConnectionState.REPAIR_REQUIRED
                }
            }
        }

        autoReconnectAllowed = when {
            error.code == "update_required" -> false
            secureState == SecureConnectionState.REPAIR_REQUIRED -> false
            retryableSessionUnavailableMessage(relayCloseCode) != null -> true
            explicitRelayDropMessage(relayCloseCode) != null -> true
            relayCloseCode == null && secureState == SecureConnectionState.TRUSTED_MAC -> true
            else -> false
        }

        updateState(
            phaseMessage = explicitRelayDropMessage(relayCloseCode)
                ?: retryableSessionUnavailableMessage(relayCloseCode)
                ?: error.message
                ?: "The secure Android transport failed.",
            secureState = secureState,
            relayUrl = currentPairingState.relayUrl,
            macDeviceId = currentPairingState.macDeviceId,
            macFingerprint = SecureCrypto.fingerprint(currentPairingState.macIdentityPublicKey),
            bridgeUpdateCommand = if (secureState == SecureConnectionState.UPDATE_REQUIRED) {
                remodexBridgeUpdateCommand
            } else {
                null
            },
        )
    }

    private fun initialSnapshot(): SecureConnectionSnapshot {
        val currentPairingState = pairingState
        val macDeviceId = currentPairingState?.macDeviceId
        val secureState = when {
            currentPairingState?.shouldForceQrBootstrap == true -> SecureConnectionState.HANDSHAKING
            currentPairingState == null -> SecureConnectionState.NOT_PAIRED
            trustedMacRegistry.records[currentPairingState.macDeviceId] != null -> SecureConnectionState.TRUSTED_MAC
            else -> SecureConnectionState.NOT_PAIRED
        }
        return SecureConnectionSnapshot(
            phaseMessage = when (secureState) {
                SecureConnectionState.NOT_PAIRED -> "Run remodex up on your Mac, then scan a pairing QR to trust this Android device."
                SecureConnectionState.TRUSTED_MAC -> "A trusted Mac pairing is saved locally. Retry reconnect to resume the secure session."
                SecureConnectionState.HANDSHAKING -> "A pairing QR is saved locally. Retry reconnect to finish the secure Android bootstrap."
                else -> "Run remodex up on your Mac, then scan a pairing QR to trust this Android device."
            },
            secureState = secureState,
            activeProfileId = currentPairingState?.profileId ?: relayProfileRegistry.activeProfileId,
            relayUrl = currentPairingState?.relayUrl,
            macDeviceId = macDeviceId,
            macDisplayName = trustedDisplayName(macDeviceId),
            macFingerprint = currentPairingState?.macIdentityPublicKey?.let(SecureCrypto::fingerprint),
            bridgeUpdateCommand = null,
            autoReconnectAllowed = autoReconnectAllowed,
        )
    }

    private fun disconnectCurrentSocket(
        pendingRequestError: Exception = SecureTransportException(
            "The secure Android connection is not currently available.",
        ),
    ) {
        failPendingRequests(pendingRequestError)
        inboundListenerJob?.cancel()
        inboundListenerJob = null
        socket?.close(1000, "reconnect")
        socket = null
        socketEvents?.close()
        socketEvents = null
        secureSession = null
        pendingHandshake = null
    }

    private fun updateState(
        phaseMessage: String,
        secureState: SecureConnectionState,
        activeProfileId: String? = pairingState?.profileId,
        relayUrl: String? = pairingState?.relayUrl,
        macDeviceId: String? = pairingState?.macDeviceId,
        macDisplayName: String? = trustedDisplayName(macDeviceId),
        macFingerprint: String? = pairingState?.macIdentityPublicKey?.let(SecureCrypto::fingerprint),
        bridgeUpdateCommand: String? = null,
    ) {
        connectionState.value = SecureConnectionSnapshot(
            phaseMessage = phaseMessage,
            secureState = secureState,
            activeProfileId = activeProfileId,
            relayUrl = relayUrl,
            macDeviceId = macDeviceId,
            macDisplayName = macDisplayName,
            macFingerprint = macFingerprint,
            attempt = currentAttempt,
            bridgeUpdateCommand = bridgeUpdateCommand,
            autoReconnectAllowed = autoReconnectAllowed,
        )
        logConnection(
            event = "stateUpdated",
            extra = "nextSecureState=$secureState macDeviceId=${macDeviceId.orEmpty()} autoReconnectAllowed=$autoReconnectAllowed",
        )
    }

    private fun logConnection(
        event: String,
        extra: String = "",
    ) {
        val suffix = extra.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        runCatching {
            Log.d(
                logTag,
                "event=$event secureState=${connectionState.value.secureState} attempt=$currentAttempt autoReconnectAllowed=$autoReconnectAllowed$suffix",
            )
        }
    }

    private fun trustedDisplayName(macDeviceId: String?): String? {
        if (macDeviceId.isNullOrBlank()) {
            return null
        }
        return trustedMacRegistry.records[macDeviceId]?.displayName
    }

    private fun refreshProfileState() {
        relayProfileRegistry = SecureCrypto.relayProfileRegistryFromStore(store)
        pairingState = SecureCrypto.relayPairingStateFromStore(store)
    }

    private fun currentBridgeProfilesSnapshot(): BridgeProfilesSnapshot {
        return SecureCrypto.bridgeProfilesSnapshot(relayProfileRegistry, trustedMacRegistry)
    }

    private fun publishBridgeProfiles() {
        bridgeProfilesState.value = currentBridgeProfilesSnapshot()
    }

    private fun relaySessionUrl(
        relayUrl: String,
        sessionId: String,
    ): String? {
        val normalizedRelayUrl = relayUrl.trim().trimEnd('/')
        return if (normalizedRelayUrl.startsWith("ws://") || normalizedRelayUrl.startsWith("wss://")) {
            "$normalizedRelayUrl/$sessionId"
        } else {
            null
        }
    }

    private fun wireMessageKind(rawText: String): String? {
        return runCatching {
            json.parseToJsonElement(rawText)
                .jsonObject["kind"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    }

    private fun SecureErrorMessage.toException(): SecureTransportException {
        return SecureTransportException(code = code, message = message)
    }

    private fun failPendingRequests(error: Exception) {
        if (pendingRequests.isEmpty()) {
            return
        }
        val pending = pendingRequests.values.toList()
        pendingRequests.clear()
        val cancellation = error as? CancellationException
            ?: CancellationException(error.message ?: "The secure Android connection ended.").also { cause ->
                cause.initCause(error)
            }
        pending.forEach { continuation ->
            continuation.cancel(cancellation)
        }
    }
}

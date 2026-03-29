package com.emanueledipietro.remodex.data.connection

import kotlinx.serialization.Serializable

const val remodexSecureProtocolVersion = 1
const val remodexSecureHandshakeTag = "remodex-e2ee-v1"
const val remodexSecureHandshakeLabel = "client-auth"
const val remodexTrustedSessionResolveTag = "remodex-trusted-session-resolve-v1"

enum class SecureHandshakeMode(
    val wireValue: String,
) {
    QR_BOOTSTRAP("qr_bootstrap"),
    TRUSTED_RECONNECT("trusted_reconnect"),
}

enum class SecureConnectionState {
    NOT_PAIRED,
    TRUSTED_MAC,
    LIVE_SESSION_UNRESOLVED,
    HANDSHAKING,
    ENCRYPTED,
    RECONNECTING,
    REPAIR_REQUIRED,
    UPDATE_REQUIRED,
}

val SecureConnectionState.statusLabel: String
    get() = when (this) {
        SecureConnectionState.NOT_PAIRED -> "Not paired"
        SecureConnectionState.TRUSTED_MAC -> "Trusted Mac"
        SecureConnectionState.LIVE_SESSION_UNRESOLVED -> "Trusted Mac ready"
        SecureConnectionState.HANDSHAKING -> "Secure handshake in progress"
        SecureConnectionState.ENCRYPTED -> "End-to-end encrypted"
        SecureConnectionState.RECONNECTING -> "Reconnecting securely"
        SecureConnectionState.REPAIR_REQUIRED -> "Re-pair required"
        SecureConnectionState.UPDATE_REQUIRED -> "Update required"
    }

@Serializable
data class PhoneIdentityState(
    val phoneDeviceId: String,
    val phoneIdentityPrivateKey: String,
    val phoneIdentityPublicKey: String,
)

@Serializable
data class TrustedMacRecord(
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val lastPairedAtEpochMs: Long,
    val relayUrl: String? = null,
    val displayName: String? = null,
    val lastResolvedSessionId: String? = null,
    val lastResolvedAtEpochMs: Long? = null,
    val lastUsedAtEpochMs: Long? = null,
)

@Serializable
data class TrustedMacRegistry(
    val records: Map<String, TrustedMacRecord> = emptyMap(),
)

@Serializable
data class RelayProfileRecord(
    val profileId: String,
    val sessionId: String,
    val relayUrl: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val protocolVersion: Int = remodexSecureProtocolVersion,
    val lastAppliedBridgeOutboundSeq: Int = 0,
    val shouldForceQrBootstrap: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val lastUsedAtEpochMs: Long? = null,
)

@Serializable
data class RelayProfileRegistry(
    val activeProfileId: String? = null,
    val profiles: Map<String, RelayProfileRecord> = emptyMap(),
)

data class RelayPairingState(
    val profileId: String,
    val sessionId: String,
    val relayUrl: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val protocolVersion: Int,
    val lastAppliedBridgeOutboundSeq: Int,
    val shouldForceQrBootstrap: Boolean,
)

@Serializable
data class SecureClientHello(
    val kind: String = "clientHello",
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: String,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val phoneEphemeralPublicKey: String,
    val clientNonce: String,
)

@Serializable
data class SecureServerHello(
    val kind: String,
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val macEphemeralPublicKey: String,
    val serverNonce: String,
    val keyEpoch: Int,
    val expiresAtForTranscript: Long,
    val macSignature: String,
    val clientNonce: String? = null,
)

@Serializable
data class SecureClientAuth(
    val kind: String = "clientAuth",
    val sessionId: String,
    val phoneDeviceId: String,
    val keyEpoch: Int,
    val phoneSignature: String,
)

@Serializable
data class SecureReadyMessage(
    val kind: String,
    val sessionId: String,
    val keyEpoch: Int,
    val macDeviceId: String,
)

@Serializable
data class SecureResumeState(
    val kind: String = "resumeState",
    val sessionId: String,
    val keyEpoch: Int,
    val lastAppliedBridgeOutboundSeq: Int,
)

@Serializable
data class SecureErrorMessage(
    val kind: String,
    val code: String,
    val message: String,
)

@Serializable
data class SecureEnvelope(
    val kind: String,
    val v: Int,
    val sessionId: String,
    val keyEpoch: Int,
    val sender: String,
    val counter: Int,
    val ciphertext: String,
    val tag: String,
)

@Serializable
data class SecureApplicationPayload(
    val bridgeOutboundSeq: Int? = null,
    val payloadText: String,
)

@Serializable
data class TrustedSessionResolveRequest(
    val macDeviceId: String,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val nonce: String,
    val timestamp: Long,
    val signature: String,
)

@Serializable
data class TrustedSessionResolveResponse(
    val ok: Boolean,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val displayName: String? = null,
    val sessionId: String,
)

@Serializable
data class RelayErrorResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val code: String? = null,
)

data class SecureSession(
    val sessionId: String,
    val keyEpoch: Int,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val phoneToMacKey: ByteArray,
    val macToPhoneKey: ByteArray,
    var lastInboundBridgeOutboundSeq: Int,
    var lastInboundCounter: Int,
    var nextOutboundCounter: Int,
)

data class SecurePendingHandshake(
    val mode: SecureHandshakeMode,
    val transcriptBytes: ByteArray,
    val phoneEphemeralPrivateKey: ByteArray,
    val phoneDeviceId: String,
)

class SecureTransportException(
    message: String,
    val code: String? = null,
) : Exception(message)

class TrustedSessionResolveException(
    val code: String,
    message: String,
) : Exception(message)

data class SecureConnectionSnapshot(
    val phaseMessage: String = "Run remodex up on your Mac, then scan a pairing QR to trust this Android device.",
    val secureState: SecureConnectionState = SecureConnectionState.NOT_PAIRED,
    val activeProfileId: String? = null,
    val relayUrl: String? = null,
    val macDeviceId: String? = null,
    val macDisplayName: String? = null,
    val macFingerprint: String? = null,
    val attempt: Int = 0,
    val bridgeUpdateCommand: String? = null,
)

data class BridgeProfileSnapshot(
    val profileId: String,
    val relayUrl: String,
    val macDeviceId: String,
    val macDisplayName: String? = null,
    val macFingerprint: String,
    val isActive: Boolean,
    val isTrusted: Boolean,
    val needsQrBootstrap: Boolean,
    val lastUsedAtEpochMs: Long? = null,
)

data class BridgeProfilesSnapshot(
    val activeProfileId: String? = null,
    val profiles: List<BridgeProfileSnapshot> = emptyList(),
)

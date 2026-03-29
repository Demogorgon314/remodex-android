package com.emanueledipietro.remodex.platform.notifications

import android.content.Context
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.SecureStore
import com.emanueledipietro.remodex.data.connection.SecureStoreKeys
import com.emanueledipietro.remodex.model.RemodexManagedPushPlatform
import com.emanueledipietro.remodex.model.RemodexManagedPushProvider
import com.emanueledipietro.remodex.model.RemodexNotificationAuthorizationStatus
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ManagedPushStatusProvider {
    val refreshEvents: Flow<Unit>

    fun authorizationStatus(): RemodexNotificationAuthorizationStatus

    fun alertsEnabled(): Boolean
}

interface ManagedPushTokenProvider {
    fun isSupported(): Boolean

    suspend fun currentToken(): String?
}

class FirebaseManagedPushTokenProvider(
    private val context: Context,
) : ManagedPushTokenProvider {
    override fun isSupported(): Boolean {
        return FirebaseApp.getApps(context).isNotEmpty()
    }

    override suspend fun currentToken(): String? {
        if (!isSupported()) {
            return null
        }
        return FirebaseMessaging.getInstance()
            .token
            .awaitValue()
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }
}

class AndroidManagedPushRegistrationCoordinator(
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val secureStore: SecureStore,
    private val statusProvider: ManagedPushStatusProvider,
    private val tokenProvider: ManagedPushTokenProvider,
    private val scope: CoroutineScope,
    private val appEnvironment: String = "production",
) {
    @Serializable
    private data class PushRegistrationSignatures(
        val profiles: Map<String, String> = emptyMap(),
    )

    private val stateFlow = MutableStateFlow(buildState(lastErrorMessage = null))

    val state: StateFlow<RemodexNotificationRegistrationState> = stateFlow.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            secureConnectionCoordinator.state.collectLatest {
                syncRegistration(force = false)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            statusProvider.refreshEvents.collectLatest {
                syncRegistration(force = true)
            }
        }
    }

    fun refresh(force: Boolean = true) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            syncRegistration(force = force)
        }
    }

    fun handleTokenUpdated(token: String?) {
        val normalizedToken = token?.trim().orEmpty()
        if (normalizedToken.isBlank()) {
            secureStore.deleteValue(SecureStoreKeys.PUSH_FCM_TOKEN)
        } else {
            secureStore.writeString(SecureStoreKeys.PUSH_FCM_TOKEN, normalizedToken)
        }
        secureStore.deleteValue(SecureStoreKeys.PUSH_REGISTRATION_SIGNATURE)
        secureStore.deleteValue(SecureStoreKeys.PUSH_REGISTRATION_SIGNATURES)
        refresh(force = true)
    }

    private suspend fun syncRegistration(force: Boolean) {
        val authorizationStatus = statusProvider.authorizationStatus()
        val managedPushSupported = tokenProvider.isSupported()
        val cachedToken = secureStore.readString(SecureStoreKeys.PUSH_FCM_TOKEN)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (!managedPushSupported) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = cachedToken,
                managedPushSupported = false,
                lastErrorMessage = "Managed push is unavailable until Firebase is configured for this Android build.",
            )
            return
        }

        val resolvedToken = try {
            tokenProvider.currentToken()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: cachedToken
        } catch (error: Exception) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = cachedToken,
                managedPushSupported = true,
                lastErrorMessage = error.message ?: "Unable to read the Android FCM token.",
            )
            return
        }

        if (!resolvedToken.isNullOrBlank() && resolvedToken != cachedToken) {
            secureStore.writeString(SecureStoreKeys.PUSH_FCM_TOKEN, resolvedToken)
        }

        if (secureConnectionCoordinator.state.value.secureState != SecureConnectionState.ENCRYPTED) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = resolvedToken,
                managedPushSupported = true,
                lastErrorMessage = null,
            )
            return
        }

        if (resolvedToken.isNullOrBlank()) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = null,
                managedPushSupported = true,
                lastErrorMessage = null,
            )
            return
        }

        val alertsEnabled = statusProvider.alertsEnabled()
        val activeProfileId = secureStore.readString(SecureStoreKeys.ACTIVE_RELAY_PROFILE_ID)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return
        val signature = buildSignature(
            sessionId = secureStore.readString(SecureStoreKeys.RELAY_SESSION_ID).orEmpty(),
            token = resolvedToken,
            alertsEnabled = alertsEnabled,
            authorizationStatus = authorizationStatus,
        )
        val signatures = secureStore.readSerializable(
            SecureStoreKeys.PUSH_REGISTRATION_SIGNATURES,
            PushRegistrationSignatures.serializer(),
        ) ?: PushRegistrationSignatures()
        val previousSignature = signatures.profiles[activeProfileId]
        if (!force && signature == previousSignature) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = resolvedToken,
                managedPushSupported = true,
                lastErrorMessage = null,
            )
            return
        }

        try {
            secureConnectionCoordinator.sendRequest(
                method = "notifications/push/register",
                params = buildJsonObject {
                    put("deviceToken", JsonPrimitive(resolvedToken))
                    put("alertsEnabled", JsonPrimitive(alertsEnabled))
                    put("authorizationStatus", JsonPrimitive(authorizationStatus.wireValue))
                    put("appEnvironment", JsonPrimitive(appEnvironment))
                    put("platform", JsonPrimitive(RemodexManagedPushPlatform.ANDROID.wireValue))
                    put("pushProvider", JsonPrimitive(RemodexManagedPushProvider.FCM.wireValue))
                },
            )
            secureStore.writeString(SecureStoreKeys.PUSH_REGISTRATION_SIGNATURE, signature)
            secureStore.writeSerializable(
                SecureStoreKeys.PUSH_REGISTRATION_SIGNATURES,
                PushRegistrationSignatures.serializer(),
                PushRegistrationSignatures(
                    profiles = signatures.profiles + (activeProfileId to signature),
                ),
            )
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = resolvedToken,
                managedPushSupported = true,
                lastErrorMessage = null,
            )
        } catch (error: RpcError) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = resolvedToken,
                managedPushSupported = true,
                lastErrorMessage = error.message,
            )
        } catch (error: Exception) {
            stateFlow.value = buildState(
                authorizationStatus = authorizationStatus,
                cachedToken = resolvedToken,
                managedPushSupported = true,
                lastErrorMessage = error.message ?: "Managed push registration failed.",
            )
        }
    }

    private fun buildState(
        authorizationStatus: RemodexNotificationAuthorizationStatus = statusProvider.authorizationStatus(),
        cachedToken: String? = secureStore.readString(SecureStoreKeys.PUSH_FCM_TOKEN),
        managedPushSupported: Boolean = tokenProvider.isSupported(),
        lastErrorMessage: String?,
    ): RemodexNotificationRegistrationState {
        return RemodexNotificationRegistrationState(
            authorizationStatus = authorizationStatus,
            managedPushSupported = managedPushSupported,
            platform = RemodexManagedPushPlatform.ANDROID,
            pushProvider = RemodexManagedPushProvider.FCM,
            deviceTokenPreview = cachedToken?.takeIf(String::isNotBlank)?.take(10),
            lastErrorMessage = lastErrorMessage,
        )
    }

    private fun buildSignature(
        sessionId: String,
        token: String,
        alertsEnabled: Boolean,
        authorizationStatus: RemodexNotificationAuthorizationStatus,
    ): String {
        return listOf(
            sessionId,
            token,
            if (alertsEnabled) "1" else "0",
            authorizationStatus.wireValue,
            appEnvironment,
            RemodexManagedPushPlatform.ANDROID.wireValue,
            RemodexManagedPushProvider.FCM.wireValue,
        ).joinToString("|")
    }
}

private val RemodexNotificationAuthorizationStatus.wireValue: String
    get() = when (this) {
        RemodexNotificationAuthorizationStatus.UNKNOWN -> "unknown"
        RemodexNotificationAuthorizationStatus.NOT_DETERMINED -> "notDetermined"
        RemodexNotificationAuthorizationStatus.DENIED -> "denied"
        RemodexNotificationAuthorizationStatus.AUTHORIZED -> "authorized"
    }

private suspend fun Task<String>.awaitValue(): String? {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            continuation.resume(value)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}

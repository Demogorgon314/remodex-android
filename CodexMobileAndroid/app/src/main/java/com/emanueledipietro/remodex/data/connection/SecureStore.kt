package com.emanueledipietro.remodex.data.connection

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

object SecureStoreKeys {
    const val RELAY_PROFILE_REGISTRY = "codex.relay.profileRegistry"
    const val ACTIVE_RELAY_PROFILE_ID = "codex.relay.activeProfileId"
    const val LEGACY_THREAD_CACHE_PROFILE_ID = "codex.relay.legacyThreadCacheProfileId"
    const val RELAY_SESSION_ID = "codex.relay.sessionId"
    const val RELAY_URL = "codex.relay.url"
    const val RELAY_MAC_DEVICE_ID = "codex.relay.macDeviceId"
    const val RELAY_MAC_IDENTITY_PUBLIC_KEY = "codex.relay.macIdentityPublicKey"
    const val RELAY_PROTOCOL_VERSION = "codex.relay.protocolVersion"
    const val RELAY_LAST_APPLIED_BRIDGE_OUTBOUND_SEQ = "codex.relay.lastAppliedBridgeOutboundSeq"
    const val TRUSTED_MAC_REGISTRY = "codex.secure.trustedMacRegistry"
    const val LAST_TRUSTED_MAC_DEVICE_ID = "codex.secure.lastTrustedMacDeviceId"
    const val PHONE_IDENTITY_STATE = "codex.secure.phoneIdentityState"
    const val SHOULD_FORCE_QR_BOOTSTRAP = "codex.relay.shouldForceQrBootstrap"
    const val PUSH_FCM_TOKEN = "codex.push.fcmToken"
    const val PUSH_REGISTRATION_SIGNATURE = "codex.push.registrationSignature"
    const val PUSH_REGISTRATION_SIGNATURES = "codex.push.registrationSignatures"
}

interface SecureStore {
    fun readString(key: String): String?
    fun writeString(key: String, value: String?)
    fun deleteValue(key: String)

    fun <T> readSerializable(
        key: String,
        serializer: KSerializer<T>,
    ): T?

    fun <T> writeSerializable(
        key: String,
        serializer: KSerializer<T>,
        value: T,
    )
}

class EncryptedPrefsSecureStore(
    context: Context,
) : SecureStore {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "remodex_secure_store",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun readString(key: String): String? = prefs.getString(key, null)

    override fun writeString(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrEmpty()) {
                remove(key)
            } else {
                putString(key, value)
            }
        }.apply()
    }

    override fun deleteValue(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun <T> readSerializable(
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        val rawValue = readString(key) ?: return null
        return runCatching { json.decodeFromString(serializer, rawValue) }.getOrNull()
    }

    override fun <T> writeSerializable(
        key: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        writeString(key, json.encodeToString(serializer, value))
    }
}

class InMemorySecureStore : SecureStore {
    private val backingMap = mutableMapOf<String, String>()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun readString(key: String): String? = backingMap[key]

    override fun writeString(key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            backingMap.remove(key)
        } else {
            backingMap[key] = value
        }
    }

    override fun deleteValue(key: String) {
        backingMap.remove(key)
    }

    override fun <T> readSerializable(
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        val rawValue = backingMap[key] ?: return null
        return runCatching { json.decodeFromString(serializer, rawValue) }.getOrNull()
    }

    override fun <T> writeSerializable(
        key: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        backingMap[key] = json.encodeToString(serializer, value)
    }
}

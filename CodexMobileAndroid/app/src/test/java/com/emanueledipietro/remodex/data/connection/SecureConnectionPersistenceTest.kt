package com.emanueledipietro.remodex.data.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureConnectionPersistenceTest {
    @Test
    fun `remember relay pairing persists reconnect prerequisites`() {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-remembered",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
            sessionId = "session-persisted",
        )

        SecureCrypto.rememberRelayPairing(store, payload, profileId = "profile-remembered")

        val pairingState = SecureCrypto.relayPairingStateFromStore(store)
        requireNotNull(pairingState)
        assertEquals("profile-remembered", pairingState.profileId)
        assertEquals(payload.sessionId, pairingState.sessionId)
        assertEquals(payload.relay, pairingState.relayUrl)
        assertEquals(payload.macDeviceId, pairingState.macDeviceId)
        assertEquals(payload.macIdentityPublicKey, pairingState.macIdentityPublicKey)
        assertTrue(pairingState.shouldForceQrBootstrap)
    }

    @Test
    fun `trusted mac registry round trips through secure storage`() {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val registry = TrustedMacRegistry(
            records = mapOf(
                "mac-known" to TrustedMacRecord(
                    macDeviceId = "mac-known",
                    macIdentityPublicKey = macIdentity.publicKeyBase64,
                    lastPairedAtEpochMs = 1_713_000_000_000,
                    relayUrl = "ws://127.0.0.1:7777/relay",
                    displayName = "Work Mac",
                ),
            ),
        )

        SecureCrypto.writeTrustedMacRegistry(store, registry, "mac-known")

        assertEquals(registry, SecureCrypto.trustedMacRegistryFromStore(store))
        assertEquals("mac-known", store.readString(SecureStoreKeys.LAST_TRUSTED_MAC_DEVICE_ID))
    }

    @Test
    fun `legacy pairing data migrates into an active relay profile`() {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        store.writeString(SecureStoreKeys.RELAY_SESSION_ID, "legacy-session")
        store.writeString(SecureStoreKeys.RELAY_URL, "ws://127.0.0.1:7777/relay")
        store.writeString(SecureStoreKeys.RELAY_MAC_DEVICE_ID, "legacy-mac")
        store.writeString(SecureStoreKeys.RELAY_MAC_IDENTITY_PUBLIC_KEY, macIdentity.publicKeyBase64)
        store.writeString(SecureStoreKeys.RELAY_PROTOCOL_VERSION, remodexSecureProtocolVersion.toString())
        store.writeString(SecureStoreKeys.RELAY_LAST_APPLIED_BRIDGE_OUTBOUND_SEQ, "7")
        store.writeString(SecureStoreKeys.SHOULD_FORCE_QR_BOOTSTRAP, "false")

        val registry = SecureCrypto.relayProfileRegistryFromStore(store)

        assertEquals(1, registry.profiles.size)
        val activeProfileId = registry.activeProfileId
        requireNotNull(activeProfileId)
        assertEquals(activeProfileId, store.readString(SecureStoreKeys.ACTIVE_RELAY_PROFILE_ID))
        assertEquals(activeProfileId, store.readString(SecureStoreKeys.LEGACY_THREAD_CACHE_PROFILE_ID))
        val pairingState = SecureCrypto.relayPairingStateFromStore(store)
        assertNotNull(pairingState)
        assertEquals(activeProfileId, pairingState?.profileId)
        assertEquals("legacy-session", pairingState?.sessionId)
        assertEquals("legacy-mac", pairingState?.macDeviceId)
        assertEquals(7, pairingState?.lastAppliedBridgeOutboundSeq)
    }
}

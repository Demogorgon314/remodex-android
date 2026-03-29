package com.emanueledipietro.remodex.data.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecureConnectionCoordinatorTest {
    @Test
    fun `qr bootstrap handshake reaches encrypted state and trusts the mac`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-bootstrap",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = SuccessfulQrBootstrapRelayWebSocketFactory(
                macDeviceId = payload.macDeviceId,
                macIdentity = macIdentity,
            ),
            scope = this,
        )

        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)

        assertEquals(SecureConnectionState.ENCRYPTED, coordinator.state.value.secureState)
        assertEquals(payload.macDeviceId, coordinator.state.value.macDeviceId)
        assertNotNull(SecureCrypto.trustedMacRegistryFromStore(store).records[payload.macDeviceId])
        assertEquals("false", store.readString(SecureStoreKeys.SHOULD_FORCE_QR_BOOTSTRAP))
    }

    @Test
    fun `trusted reconnect resolve failure marks session unavailable`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        seedTrustedMacState(
            store = store,
            macDeviceId = "mac-offline",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = ThrowingTrustedSessionResolver(
                TrustedSessionResolveException(
                    code = "session_unavailable",
                    message = "Your trusted Mac is offline right now.",
                ),
            ),
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )

        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.LIVE_SESSION_UNRESOLVED)

        assertEquals(SecureConnectionState.LIVE_SESSION_UNRESOLVED, coordinator.state.value.secureState)
        assertEquals("Your trusted Mac is offline right now.", coordinator.state.value.phaseMessage)
    }

    @Test
    fun `trusted reconnect invalid trust falls back to repair required`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        seedTrustedMacState(
            store = store,
            macDeviceId = "mac-repair",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = ThrowingTrustedSessionResolver(
                TrustedSessionResolveException(
                    code = "phone_not_trusted",
                    message = "This Android device is no longer trusted by the Mac. Scan a new QR code to reconnect.",
                ),
            ),
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )

        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.REPAIR_REQUIRED)

        assertEquals(SecureConnectionState.REPAIR_REQUIRED, coordinator.state.value.secureState)
        assertEquals(
            "This Android device is no longer trusted by the Mac. Scan a new QR code to reconnect.",
            coordinator.state.value.phaseMessage,
        )
    }

    @Test
    fun `saved bridge profiles can be activated and removed independently`() = runTest {
        val store = InMemorySecureStore()
        val firstMacIdentity = createTestMacIdentity()
        val secondMacIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )

        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-one",
                macIdentityPublicKey = firstMacIdentity.publicKeyBase64,
                sessionId = "session-one",
            ),
        )
        val firstProfileId = coordinator.bridgeProfiles.value.activeProfileId
        requireNotNull(firstProfileId)

        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-two",
                macIdentityPublicKey = secondMacIdentity.publicKeyBase64,
                sessionId = "session-two",
            ),
        )
        val secondProfileId = coordinator.bridgeProfiles.value.activeProfileId
        requireNotNull(secondProfileId)

        assertEquals(2, coordinator.bridgeProfiles.value.profiles.size)
        assertEquals("mac-two", coordinator.state.value.macDeviceId)
        val originalOrder = coordinator.bridgeProfiles.value.profiles.map { it.profileId }
        assertTrue(coordinator.activateBridgeProfile(firstProfileId))
        assertEquals(firstProfileId, coordinator.bridgeProfiles.value.activeProfileId)
        assertEquals("mac-one", coordinator.state.value.macDeviceId)
        assertEquals(originalOrder, coordinator.bridgeProfiles.value.profiles.map { it.profileId })

        val nextActiveProfileId = coordinator.removeBridgeProfile(firstProfileId)
        assertEquals(secondProfileId, nextActiveProfileId)
        assertEquals(1, coordinator.bridgeProfiles.value.profiles.size)
        assertEquals(secondProfileId, coordinator.bridgeProfiles.value.activeProfileId)
        assertEquals("mac-two", coordinator.state.value.macDeviceId)
        assertFalse(coordinator.bridgeProfiles.value.profiles.any { it.profileId == firstProfileId })
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

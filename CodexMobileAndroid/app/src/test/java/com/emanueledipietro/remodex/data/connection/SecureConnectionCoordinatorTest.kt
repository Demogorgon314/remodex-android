package com.emanueledipietro.remodex.data.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
    fun `trusted reconnect close code 4002 stays auto-retryable`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        seedTrustedMacState(
            store = store,
            macDeviceId = "mac-retryable-close",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = StaticTrustedSessionResolver(
                TrustedSessionResolveResponse(
                    ok = true,
                    macDeviceId = "mac-retryable-close",
                    macIdentityPublicKey = macIdentity.publicKeyBase64,
                    displayName = "Desk Mac",
                    sessionId = "session-retryable-close",
                ),
            ),
            relayWebSocketFactory = ClosingRelayWebSocketFactory(closeCode = 4002),
            scope = this,
        )

        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.TRUSTED_MAC)

        assertEquals(SecureConnectionState.TRUSTED_MAC, coordinator.state.value.secureState)
        assertTrue(coordinator.state.value.autoReconnectAllowed)
        assertEquals(
            "The trusted Mac session is temporarily unavailable. Remodex will keep retrying. If you restarted the bridge on your Mac, scan the new QR code.",
            coordinator.state.value.phaseMessage,
        )
    }

    @Test
    fun `trusted reconnect permanent close disables auto reconnect and keeps saved pair presentation`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        seedTrustedMacState(
            store = store,
            macDeviceId = "mac-permanent-close",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = StaticTrustedSessionResolver(
                TrustedSessionResolveResponse(
                    ok = true,
                    macDeviceId = "mac-permanent-close",
                    macIdentityPublicKey = macIdentity.publicKeyBase64,
                    displayName = "Desk Mac",
                    sessionId = "session-permanent-close",
                ),
            ),
            relayWebSocketFactory = ClosingRelayWebSocketFactory(closeCode = 4001),
            scope = this,
        )

        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.LIVE_SESSION_UNRESOLVED)

        assertEquals(SecureConnectionState.LIVE_SESSION_UNRESOLVED, coordinator.state.value.secureState)
        assertFalse(coordinator.state.value.autoReconnectAllowed)
        assertEquals(
            "This relay session was replaced by another Mac connection. Scan a new QR code to reconnect.",
            coordinator.state.value.phaseMessage,
        )
    }

    @Test
    fun `trusted reconnect failures stop auto reconnect after three attempts`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        seedTrustedMacState(
            store = store,
            macDeviceId = "mac-stale-session",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = StaticTrustedSessionResolver(
                TrustedSessionResolveResponse(
                    ok = true,
                    macDeviceId = "mac-stale-session",
                    macIdentityPublicKey = macIdentity.publicKeyBase64,
                    displayName = "Desk Mac",
                    sessionId = "session-stale-session",
                ),
            ),
            relayWebSocketFactory = ClosingRelayWebSocketFactory(closeCode = 4002),
            scope = this,
        )

        repeat(3) {
            coordinator.retryConnection()
            advanceUntilIdle()
        }

        assertEquals(SecureConnectionState.LIVE_SESSION_UNRESOLVED, coordinator.state.value.secureState)
        assertFalse(coordinator.state.value.autoReconnectAllowed)
        assertEquals(
            "Secure reconnect could not be restored from the saved session. Try reconnecting again.",
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

    @Test
    fun `activating a saved bridge cancels pending requests instead of crashing`() = runTest {
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
        val pendingRequest = launch {
            suspendCancellableCoroutine<RpcMessage> { continuation ->
                pendingRequests(coordinator)["request-1"] = continuation
            }
        }
        advanceUntilIdle()

        assertTrue(coordinator.activateBridgeProfile(firstProfileId))
        advanceUntilIdle()

        assertTrue(pendingRequest.isCancelled)
        assertEquals(firstProfileId, coordinator.bridgeProfiles.value.activeProfileId)
    }

    @Test
    fun `transport teardown cancels pending requests instead of throwing`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )
        val pendingRequest = launch {
            suspendCancellableCoroutine<RpcMessage> { continuation ->
                pendingRequests(coordinator)["request-transport-error"] = continuation
            }
        }
        advanceUntilIdle()

        invokePrivateMethod<Unit>(
            coordinator,
            "failPendingRequests",
            SecureTransportException("The secure Android connection is not currently available."),
        )
        advanceUntilIdle()

        assertTrue(pendingRequest.isCancelled)
    }

    @Test
    fun `incoming notifications preserve turn completion after many streaming deltas`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )

        repeat(80) { index ->
            invokePrivateMethod<Unit>(
                coordinator,
                "routeIncomingPayload",
                """{"method":"item/agentMessage/delta","params":{"delta":"chunk-$index"}}""",
            )
        }
        invokePrivateMethod<Unit>(
            coordinator,
            "routeIncomingPayload",
            """{"method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed","items":[]}}}""",
        )

        val queuedMethods = mutableListOf<String>()
        val notifications = notificationsChannel(coordinator)
        while (true) {
            val result = notifications.tryReceive()
            if (result.isFailure) {
                break
            }
            queuedMethods += result.getOrThrow().method.orEmpty()
        }

        assertEquals(81, queuedMethods.size)
        assertEquals("turn/completed", queuedMethods.last())
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

    @Suppress("UNCHECKED_CAST")
    private fun pendingRequests(
        coordinator: SecureConnectionCoordinator,
    ): LinkedHashMap<String, kotlinx.coroutines.CancellableContinuation<RpcMessage>> {
        val field = SecureConnectionCoordinator::class.java.getDeclaredField("pendingRequests")
        field.isAccessible = true
        return field.get(coordinator) as LinkedHashMap<String, kotlinx.coroutines.CancellableContinuation<RpcMessage>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun notificationsChannel(
        coordinator: SecureConnectionCoordinator,
    ): Channel<RpcMessage> {
        val field = SecureConnectionCoordinator::class.java.getDeclaredField("notificationsChannel")
        field.isAccessible = true
        return field.get(coordinator) as Channel<RpcMessage>
    }

    private fun <T> invokePrivateMethod(
        target: Any,
        methodName: String,
        vararg args: Any,
    ): T {
        val method = target::class.java.declaredMethods.first { candidate ->
            candidate.name == methodName && candidate.parameterTypes.size == args.size
        }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(target, *args) as T
    }
}

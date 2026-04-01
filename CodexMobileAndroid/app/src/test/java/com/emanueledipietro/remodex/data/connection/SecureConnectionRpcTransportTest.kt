package com.emanueledipietro.remodex.data.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecureConnectionRpcTransportTest {
    @Test
    fun `secure coordinator can roundtrip a JSON RPC request after handshake`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-rpc",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "thread/list" to { _ ->
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-rpc"))
                                        put("title", JsonPrimitive("RPC thread"))
                                    },
                                )
                            },
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

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)

            val response = coordinator.sendRequest(
                method = "thread/list",
                params = buildJsonObject { put("cursor", JsonPrimitive("")) },
            )

            assertNotNull(response.result)
            assertEquals("thread/list", relayFactory.receivedRequests.single().method)
            assertEquals("thread-rpc", response.result?.jsonObjectOrNull?.firstArray("data")?.firstOrNull()?.jsonObjectOrNull?.firstString("id"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `relay disconnect during pending request cancels request without crashing`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-rpc-disconnect",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            closeAfterRequest = RelayWireEvent.Closed(4002, "relay dropped after request"),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)

            val request = async {
                coordinator.sendRequest(method = "thread/list")
            }

            advanceUntilIdle()

            assertTrue(request.isCancelled)
            assertEquals(SecureConnectionState.TRUSTED_MAC, coordinator.state.value.secureState)
            assertEquals("thread/list", relayFactory.receivedRequests.single().method)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
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

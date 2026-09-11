package cash.w.sdk

import cash.w.sdk.internal.jni.JniWcashAddress
import cash.w.sdk.internal.jni.JniWcashTransparentP2pkhAddress
import cash.w.sdk.internal.jni.WcashNativeBackend
import cash.w.sdk.model.WcashAccountIndex
import cash.w.sdk.model.WcashNetwork
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WcashWalletToolTest {
    @Test
    fun semantic_inputs_reach_the_expected_native_domain() {
        val backend = RecordingBackend(JniWcashAddress(TESTNET_ADDRESS, TESTNET_CODE))
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64) { 7 }) { true }

        val address =
            tool.deriveIronwoodAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.from(9)
            )

        assertEquals(TESTNET_ADDRESS, address.encoded)
        assertEquals(WcashNetwork.Testnet, address.network)
        assertEquals(TESTNET_CODE, backend.observedNetworkCode)
        assertEquals(9, backend.observedAccountIndex)
        assertContentEquals(ByteArray(64) { 7 }, backend.observedSeedCopy)
        assertContentEquals(ByteArray(64), backend.seedReference)
        seed.close()
    }

    @Test
    fun native_result_must_attest_the_requested_network() {
        val backend = RecordingBackend(JniWcashAddress(REGTEST_ADDRESS, REGTEST_CODE))
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64)) { true }

        assertFailsWith<WcashWalletDerivationException> {
            tool.deriveIronwoodAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.from(0)
            )
        }
        seed.close()
    }

    @Test
    fun transparent_coinbase_result_must_be_reparsed_and_attest_the_network() {
        val backend =
            RecordingBackend(
                response = JniWcashAddress(TESTNET_ADDRESS, TESTNET_CODE),
                transparentResponse =
                    JniWcashTransparentP2pkhAddress(REGTEST_TRANSPARENT_ADDRESS, REGTEST_CODE)
            )
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64)) { true }

        assertFailsWith<WcashWalletDerivationException> {
            tool.deriveTransparentCoinbaseAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.from(0)
            )
        }
        seed.close()
    }

    @Test
    fun transparent_coinbase_derivation_uses_semantic_inputs_and_wipes_its_copy() {
        val backend = RecordingBackend(JniWcashAddress(TESTNET_ADDRESS, TESTNET_CODE))
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64) { 11 }) { true }

        val address =
            tool.deriveTransparentCoinbaseAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.from(4)
            )

        assertEquals(TESTNET_TRANSPARENT_ADDRESS, address.encoded)
        assertEquals(WcashNetwork.Testnet, address.network)
        assertEquals(TESTNET_CODE, backend.observedNetworkCode)
        assertEquals(4, backend.observedAccountIndex)
        assertContentEquals(ByteArray(64) { 11 }, backend.observedSeedCopy)
        assertContentEquals(ByteArray(64), backend.seedReference)
        seed.close()
    }

    @Test
    fun native_errors_are_redacted_at_the_public_boundary() {
        val marker = "native-accidentally-echoed-secret"
        val backend =
            object : WcashNativeBackend {
                override fun validateSeed(seed: ByteArray): Boolean = error("unused")

                override fun deriveIronwoodAddress(
                    seed: ByteArray,
                    networkCode: Int,
                    accountIndex: Long
                ): JniWcashAddress = throw IllegalStateException(marker)

                override fun parseIronwoodAddress(encoded: String): JniWcashAddress = error("unused")

                override fun deriveTransparentCoinbaseAddress(
                    seed: ByteArray,
                    networkCode: Int,
                    accountIndex: Long
                ): JniWcashTransparentP2pkhAddress = throw IllegalStateException(marker)

                override fun parseTransparentP2pkhAddress(
                    encoded: String
                ): JniWcashTransparentP2pkhAddress = error("unused")

                override fun networkIdentity(networkCode: Int): String = error("unused")
            }
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64)) { true }

        val failure =
            assertFailsWith<WcashWalletDerivationException> {
                tool.deriveIronwoodAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.from(0)
                )
            }
        val transparentFailure =
            assertFailsWith<WcashWalletDerivationException> {
                tool.deriveTransparentCoinbaseAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.from(0)
                )
            }

        assertEquals("Wcash address derivation failed", failure.message)
        assertEquals("Wcash address derivation failed", transparentFailure.message)
        assertFalse(failure.toString().contains(marker))
        assertFalse(transparentFailure.toString().contains(marker))
        assertEquals(null, failure.cause)
        assertEquals(null, transparentFailure.cause)
        seed.close()
    }

    @Test
    fun destroyed_seed_failures_are_redacted_at_the_public_boundary() {
        val backend = RecordingBackend(JniWcashAddress(TESTNET_ADDRESS, TESTNET_CODE))
        val tool = WcashWalletTool.createForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64)) { true }
        seed.close()

        val ironwoodFailure =
            assertFailsWith<WcashWalletDerivationException> {
                tool.deriveIronwoodAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.from(0)
                )
            }
        val transparentFailure =
            assertFailsWith<WcashWalletDerivationException> {
                tool.deriveTransparentCoinbaseAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.from(0)
                )
            }

        assertEquals("Wcash address derivation failed", ironwoodFailure.message)
        assertEquals("Wcash address derivation failed", transparentFailure.message)
        assertEquals(null, ironwoodFailure.cause)
        assertEquals(null, transparentFailure.cause)
    }

    private class RecordingBackend(
        private val response: JniWcashAddress,
        private val transparentResponse: JniWcashTransparentP2pkhAddress =
            JniWcashTransparentP2pkhAddress(TESTNET_TRANSPARENT_ADDRESS, TESTNET_CODE)
    ) : WcashNativeBackend {
        var observedSeedCopy: ByteArray? = null
        var seedReference: ByteArray? = null
        var observedNetworkCode: Int? = null
        var observedAccountIndex: Long? = null

        override fun validateSeed(seed: ByteArray): Boolean = true

        override fun deriveIronwoodAddress(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashAddress {
            observedSeedCopy = seed.copyOf()
            seedReference = seed
            observedNetworkCode = networkCode
            observedAccountIndex = accountIndex
            return response
        }

        override fun parseIronwoodAddress(encoded: String): JniWcashAddress = response

        override fun deriveTransparentCoinbaseAddress(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashTransparentP2pkhAddress {
            observedSeedCopy = seed.copyOf()
            seedReference = seed
            observedNetworkCode = networkCode
            observedAccountIndex = accountIndex
            return transparentResponse
        }

        override fun parseTransparentP2pkhAddress(
            encoded: String
        ): JniWcashTransparentP2pkhAddress = transparentResponse

        override fun networkIdentity(networkCode: Int): String = error("unused")
    }

    private companion object {
        const val TESTNET_CODE = 1
        const val REGTEST_CODE = 2
        const val TESTNET_ADDRESS =
            "wutest17mvne4ygv9v8rkjf6yxnrveceejh8nutee8svp8swkgj7s7ac9ga36u2av8" +
                "hgpc28cc42u474ypjq2jsdt64utcxtztm2jr6guvaryhh"
        const val TESTNET_TRANSPARENT_ADDRESS = "WTNjqDPXEGEgKHS1YPtfEgdrqk6egFRULDz"
        const val REGTEST_TRANSPARENT_ADDRESS = "WRHUq9CTLZFa52NmAyHH5usN21Q8jZVskN1"
        const val REGTEST_ADDRESS = "wuregtest1fake-address-used-only-by-the-test-backend"
    }
}

package cash.z.ecc.android.sdk.tool

import cash.z.ecc.android.sdk.model.WcashAccountIndex
import cash.z.ecc.android.sdk.model.WcashNetwork
import cash.z.ecc.android.sdk.model.WcashWalletSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WcashWalletToolTest {
    @Test
    fun semantic_inputs_reach_the_expected_native_domain() {
        val backend = RecordingBackend(TESTNET_ADDRESS, TESTNET_ID)
        val tool = WcashWalletTool.newForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(32) { 7 }) { true }

        val address =
            tool.deriveIronwoodAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.new(9),
            )

        assertEquals(TESTNET_ADDRESS, address.value)
        assertEquals(WcashNetwork.Testnet, address.network)
        assertEquals(TESTNET_ID, backend.networkId)
        assertEquals(9, backend.accountIndex)
        assertEquals(ByteArray(32) { 7 }.toList(), backend.observedSeed?.toList())
        assertEquals(ByteArray(32).toList(), backend.seedReference?.toList())
    }

    @Test
    fun derived_address_must_attest_the_requested_network() {
        val tool = WcashWalletTool.newForTests(RecordingBackend(REGTEST_ADDRESS, REGTEST_ID))
        val seed = WcashWalletSeed.fromCandidate(ByteArray(32)) { true }

        assertFailsWith<WcashWalletDerivationException> {
            tool.deriveIronwoodAddress(
                seed,
                WcashNetwork.Testnet,
                WcashAccountIndex.new(0),
            )
        }
    }

    @Test
    fun backend_errors_are_redacted_at_the_public_boundary() {
        val marker = "caller-secret-was-echoed"
        val backend =
            object : WcashWalletNative {
                override fun deriveIronwoodAddress(
                    seed: ByteArray,
                    networkId: Int,
                    accountIndex: Long,
                ): String = throw IllegalStateException(marker)

                override fun parseIronwoodAddressNetwork(address: String): Int = error("unused")
            }
        val tool = WcashWalletTool.newForTests(backend)
        val seed = WcashWalletSeed.fromCandidate(ByteArray(32)) { true }

        val error =
            assertFailsWith<WcashWalletDerivationException> {
                tool.deriveIronwoodAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.new(0),
                )
            }

        assertEquals("Could not derive Wcash Ironwood receiving address", error.message)
        assertEquals(false, error.toString().contains(marker))
    }

    private class RecordingBackend(
        private val address: String,
        private val parsedNetworkId: Int,
    ) : WcashWalletNative {
        var observedSeed: ByteArray? = null
        var seedReference: ByteArray? = null
        var networkId: Int? = null
        var accountIndex: Long? = null

        override fun deriveIronwoodAddress(
            seed: ByteArray,
            networkId: Int,
            accountIndex: Long,
        ): String {
            observedSeed = seed.copyOf()
            seedReference = seed
            this.networkId = networkId
            this.accountIndex = accountIndex
            return address
        }

        override fun parseIronwoodAddressNetwork(address: String): Int = parsedNetworkId
    }

    private companion object {
        const val TESTNET_ID = 1
        const val REGTEST_ID = 2
        const val TESTNET_ADDRESS =
            "wutest17mvne4ygv9v8rkjf6yxnrveceejh8nutee8svp8swkgj7s7ac9ga36u2av8" +
                "hgpc28cc42u474ypjq2jsdt64utcxtztm2jr6guvaryhh"
        const val REGTEST_ADDRESS =
            "uregtest1placeholder-used-only-by-the-fake-backend"
    }
}

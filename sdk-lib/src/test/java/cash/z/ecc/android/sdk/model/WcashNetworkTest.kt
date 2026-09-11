package cash.z.ecc.android.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WcashNetworkTest {
    @Test
    fun only_frozen_non_mainnet_networks_are_exposed() {
        assertEquals(listOf(WcashNetwork.Testnet, WcashNetwork.Regtest), WcashNetwork.entries)
    }

    @Test
    fun testnet_identity_matches_wcash_node() {
        with(WcashNetwork.Testnet) {
            assertEquals("WcashTestnet", nodeNetworkName)
            assertEquals("test", compactServerNetworkName)
            assertEquals("wcashtestnet-v5", networkNamespace)
            assertEquals("TWC", currencyTicker)
            assertEquals(
                "0271b5b0a10b2838f43cccdec9ca2f72aa72a7c103830082bac8f82f47f0593a",
                genesisBlockHash,
            )
            assertEquals("b3cfd27e", consensusBranchIdHex)
            assertEquals(BlockHeight.new(1), ironwoodActivationHeight)
            assertEquals("wutest", unifiedAddressHrp)
            assertEquals("wtextest", texAddressHrp)
            assertEquals("1095", transparentP2pkhVersionHex)
            assertEquals("1098", transparentP2shVersionHex)
        }
    }

    @Test
    fun regtest_identity_matches_wcash_node() {
        with(WcashNetwork.Regtest) {
            assertEquals("WcashRegtest", nodeNetworkName)
            assertEquals("test", compactServerNetworkName)
            assertEquals("wcashregtest-v5", networkNamespace)
            assertEquals("TWC", currencyTicker)
            assertEquals(
                "70bf0bab17eff361a6331bb825b3b7253c8c96ff96407f948161d2912658bb1c",
                genesisBlockHash,
            )
            assertEquals("c3a6678a", consensusBranchIdHex)
            assertEquals(BlockHeight.new(1), ironwoodActivationHeight)
            assertEquals("wuregtest", unifiedAddressHrp)
            assertEquals("wtexregtest", texAddressHrp)
            assertEquals("1090", transparentP2pkhVersionHex)
            assertEquals("1093", transparentP2shVersionHex)
        }
    }

    @Test
    fun supported_networks_have_disjoint_consensus_and_address_identity() {
        assertNotEquals(WcashNetwork.Testnet.nodeNetworkName, WcashNetwork.Regtest.nodeNetworkName)
        assertNotEquals(WcashNetwork.Testnet.networkNamespace, WcashNetwork.Regtest.networkNamespace)
        assertNotEquals(WcashNetwork.Testnet.genesisBlockHash, WcashNetwork.Regtest.genesisBlockHash)
        assertNotEquals(
            WcashNetwork.Testnet.consensusBranchIdHex,
            WcashNetwork.Regtest.consensusBranchIdHex,
        )
        assertNotEquals(WcashNetwork.Testnet.unifiedAddressHrp, WcashNetwork.Regtest.unifiedAddressHrp)
        assertNotEquals(WcashNetwork.Testnet.texAddressHrp, WcashNetwork.Regtest.texAddressHrp)
        assertNotEquals(
            WcashNetwork.Testnet.transparentP2pkhVersionHex,
            WcashNetwork.Regtest.transparentP2pkhVersionHex,
        )
        assertNotEquals(
            WcashNetwork.Testnet.transparentP2shVersionHex,
            WcashNetwork.Regtest.transparentP2shVersionHex,
        )
    }

    @Test
    fun address_namespaces_do_not_collide_with_zcash() {
        val zcashUnifiedAddressHrps = setOf("u", "utest", "uregtest")
        val zcashTexAddressHrps = setOf("tex", "textest", "texregtest")
        val zcashTransparentVersions = setOf("1cb8", "1cbd", "1d25", "1cba")

        WcashNetwork.entries.forEach { network ->
            assertNotEquals(true, network.unifiedAddressHrp in zcashUnifiedAddressHrps)
            assertNotEquals(true, network.texAddressHrp in zcashTexAddressHrps)
            assertNotEquals(true, network.transparentP2pkhVersionHex in zcashTransparentVersions)
            assertNotEquals(true, network.transparentP2shVersionHex in zcashTransparentVersions)
        }
    }
}

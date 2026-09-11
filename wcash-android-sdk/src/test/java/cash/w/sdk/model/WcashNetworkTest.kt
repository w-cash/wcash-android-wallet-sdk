package cash.w.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WcashNetworkTest {
    @Test
    fun only_frozen_testing_networks_are_exposed() {
        assertEquals(listOf(WcashNetwork.Testnet, WcashNetwork.Regtest), WcashNetwork.entries)
    }

    @Test
    fun testnet_identity_matches_the_wcash_node() {
        with(WcashNetwork.Testnet) {
            assertEquals("WcashTestnet", nodeNetworkName)
            assertEquals("test", compactServerNetworkName)
            assertEquals("wcashtestnet-v5", networkNamespace)
            assertEquals("TWC", currencyTicker)
            assertEquals(
                "0271b5b0a10b2838f43cccdec9ca2f72aa72a7c103830082bac8f82f47f0593a",
                genesisBlockHash
            )
            assertEquals("b3cfd27e", consensusBranchIdHex)
            assertEquals(1, ironwoodActivationHeight)
            assertEquals("wutest", unifiedAddressHrp)
            assertEquals("wtextest", texAddressHrp)
            assertEquals("1095", transparentP2pkhVersionHex)
            assertEquals("1098", transparentP2shVersionHex)
        }
    }

    @Test
    fun regtest_identity_matches_the_wcash_node() {
        with(WcashNetwork.Regtest) {
            assertEquals("WcashRegtest", nodeNetworkName)
            assertEquals("test", compactServerNetworkName)
            assertEquals("wcashregtest-v5", networkNamespace)
            assertEquals("TWC", currencyTicker)
            assertEquals(
                "70bf0bab17eff361a6331bb825b3b7253c8c96ff96407f948161d2912658bb1c",
                genesisBlockHash
            )
            assertEquals("c3a6678a", consensusBranchIdHex)
            assertEquals(1, ironwoodActivationHeight)
            assertEquals("wuregtest", unifiedAddressHrp)
            assertEquals("wtexregtest", texAddressHrp)
            assertEquals("1090", transparentP2pkhVersionHex)
            assertEquals("1093", transparentP2shVersionHex)
        }
    }

    @Test
    fun supported_networks_have_disjoint_consensus_and_address_identity() {
        val testnet = WcashNetwork.Testnet
        val regtest = WcashNetwork.Regtest

        assertNotEquals(testnet.nodeNetworkName, regtest.nodeNetworkName)
        assertNotEquals(testnet.networkNamespace, regtest.networkNamespace)
        assertNotEquals(testnet.genesisBlockHash, regtest.genesisBlockHash)
        assertNotEquals(testnet.consensusBranchIdHex, regtest.consensusBranchIdHex)
        assertNotEquals(testnet.unifiedAddressHrp, regtest.unifiedAddressHrp)
        assertNotEquals(testnet.texAddressHrp, regtest.texAddressHrp)
        assertNotEquals(testnet.transparentP2pkhVersionHex, regtest.transparentP2pkhVersionHex)
        assertNotEquals(testnet.transparentP2shVersionHex, regtest.transparentP2shVersionHex)
    }
}

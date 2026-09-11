package cash.w.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import cash.w.sdk.internal.jni.JniWcashBackend
import cash.w.sdk.model.WcashAccountIndex
import cash.w.sdk.model.WcashIronwoodAddress
import cash.w.sdk.model.WcashNetwork
import cash.w.sdk.model.WcashTransparentP2pkhAddress
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class WcashNativeIntegrationTest {
    @Test
    fun native_seed_policy_accepts_only_bip39_pbkdf2_output() {
        listOf(32, 63, 65).forEach { rejectedLength ->
            assertEquals(false, JniWcashBackend.validateSeed(ByteArray(rejectedLength)))
        }
        assertEquals(true, JniWcashBackend.validateSeed(BIP39_SEED))
    }

    @Test
    fun seed_validation_and_frozen_derivation_cross_the_real_jni_boundary() {
        assertFailsWith<IllegalArgumentException> {
            WcashWalletSeed.fromBip39SeedBytes(ByteArray(32))
        }
        assertFailsWith<IllegalArgumentException> {
            WcashWalletSeed.fromBip39SeedBytes(ByteArray(65))
        }

        val seed = WcashWalletSeed.fromBip39SeedBytes(BIP39_SEED)
        try {
            val address =
                WcashWalletTool.create().deriveIronwoodAddress(
                    seed,
                    WcashNetwork.Testnet,
                    WcashAccountIndex.from(0)
                )

            assertEquals(TESTNET_ADDRESS, address.encoded)
            assertEquals(WcashNetwork.Testnet, address.network)
        } finally {
            seed.close()
        }
    }

    @Test
    fun uppercase_wcash_address_is_canonicalized_by_rust() {
        val parsed = WcashIronwoodAddress.parse(TESTNET_ADDRESS.uppercase(Locale.US))

        assertEquals(TESTNET_ADDRESS, parsed.encoded)
        assertEquals(WcashNetwork.Testnet, parsed.network)
    }

    @Test
    fun transparent_coinbase_vectors_are_derived_by_the_real_native_core() {
        val seed = WcashWalletSeed.fromBip39SeedBytes(BIP39_SEED)
        try {
            val tool = WcashWalletTool.create()
            val account = WcashAccountIndex.from(0)

            val testnet =
                tool.deriveTransparentCoinbaseAddress(seed, WcashNetwork.Testnet, account)
            val regtest =
                tool.deriveTransparentCoinbaseAddress(seed, WcashNetwork.Regtest, account)

            assertEquals(TESTNET_TRANSPARENT_P2PKH, testnet.encoded)
            assertEquals(WcashNetwork.Testnet, testnet.network)
            assertEquals(REGTEST_TRANSPARENT_P2PKH, regtest.encoded)
            assertEquals(WcashNetwork.Regtest, regtest.network)
        } finally {
            seed.close()
        }
    }

    @Test
    fun transparent_parser_accepts_only_exact_wcash_p2pkh_base58() {
        assertEquals(
            TESTNET_TRANSPARENT_P2PKH,
            WcashTransparentP2pkhAddress.parse(TESTNET_TRANSPARENT_P2PKH).encoded
        )
        assertEquals(
            WcashNetwork.Regtest,
            WcashTransparentP2pkhAddress.parse(REGTEST_TRANSPARENT_P2PKH).network
        )

        listOf(
            TESTNET_ADDRESS,
            TESTNET_P2SH,
            TESTNET_TEX,
            ZCASH_TESTNET_TRANSPARENT,
            " $TESTNET_TRANSPARENT_P2PKH",
            "$TESTNET_TRANSPARENT_P2PKH ",
            TESTNET_TRANSPARENT_P2PKH.lowercase(Locale.US),
            TESTNET_TRANSPARENT_P2PKH.uppercase(Locale.US),
            TESTNET_TRANSPARENT_BAD_CHECKSUM
        ).forEach { rejected ->
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    WcashTransparentP2pkhAddress.parse(rejected)
                }
            assertEquals("Invalid Wcash transparent P2PKH address", failure.message)
            assertEquals(null, failure.cause)
        }
    }

    @Test
    fun zcash_and_malformed_addresses_fail_closed_through_jni() {
        assertFailsWith<IllegalArgumentException> {
            WcashIronwoodAddress.parse(ZCASH_TESTNET_ADDRESS)
        }
        assertFailsWith<IllegalArgumentException> {
            WcashIronwoodAddress.parse("wutest1malformed")
        }
    }

    @Test
    fun kotlin_network_metadata_is_attested_by_the_pinned_rust_core() {
        WcashNetwork.entries.forEach { network ->
            val expected =
                listOf(
                    network.nodeNetworkName,
                    network.compactServerNetworkName,
                    network.networkNamespace,
                    network.currencyTicker,
                    network.genesisBlockHash,
                    network.consensusBranchIdHex,
                    network.ironwoodActivationHeight.toString(),
                    network.unifiedAddressHrp,
                    network.texAddressHrp,
                    network.transparentP2pkhVersionHex,
                    network.transparentP2shVersionHex
                ).joinToString(IDENTITY_SEPARATOR)

            assertEquals(expected, JniWcashBackend.networkIdentity(network.nativeCode))
        }
    }

    private companion object {
        const val IDENTITY_SEPARATOR = "\u001f"
        val BIP39_SEED =
            byteArrayOf(
                0x40,
                0x8b.toByte(),
                0x28,
                0x5c,
                0x12,
                0x38,
                0x36,
                0x00,
                0x4f,
                0x4b,
                0x88.toByte(),
                0x42,
                0xc8.toByte(),
                0x93.toByte(),
                0x24,
                0xc1.toByte(),
                0xf0.toByte(),
                0x13,
                0x82.toByte(),
                0x45,
                0x0c,
                0x0d,
                0x43,
                0x9a.toByte(),
                0xf3.toByte(),
                0x45,
                0xba.toByte(),
                0x7f,
                0xc4.toByte(),
                0x9a.toByte(),
                0xcf.toByte(),
                0x70,
                0x54,
                0x89.toByte(),
                0xc6.toByte(),
                0xfc.toByte(),
                0x77,
                0xdb.toByte(),
                0xd4.toByte(),
                0xe3.toByte(),
                0xdc.toByte(),
                0x1d,
                0xd8.toByte(),
                0xcc.toByte(),
                0x6b,
                0xc9.toByte(),
                0xf0.toByte(),
                0x43,
                0xdb.toByte(),
                0x8a.toByte(),
                0xda.toByte(),
                0x1e,
                0x24,
                0x3c,
                0x4a,
                0x0e,
                0xaf.toByte(),
                0xb2.toByte(),
                0x90.toByte(),
                0xd3.toByte(),
                0x99.toByte(),
                0x48,
                0x08,
                0x40
            )
        const val TESTNET_ADDRESS =
            "wutest18rmpm4xcm2d54xg5mg00lac9pg4txaladyp6pacqhm355n5scpn5gja6hy43" +
                "uqassvr63g6xuephu8r0qju92778lg4v5nkxfu7j3la6"
        const val ZCASH_TESTNET_ADDRESS =
            "utest10c5kutapazdnf8ztl3pu43nkfsjx89fy3uuff8tsmxm6s86j37pe7uz94z5jhkl" +
                "49pqe8yz75rlsaygexk6jpaxwx0esjr8wm5ut7d5s"
        const val TESTNET_TRANSPARENT_P2PKH = "WTMMWgVvepdG58zdNjePbtyoh4aSwb4kP3E"
        const val REGTEST_TRANSPARENT_P2PKH = "WRSJjaJAZ75QkqbJoa244F21QmkPHEqhYu8"
        const val TESTNET_P2SH = "WUJmKiHCs7MSy6FGzyBvrwdExdsU75uiFgz"
        const val TESTNET_TEX = "wtextest1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqf4k4nr"
        const val ZCASH_TESTNET_TRANSPARENT = "tmQvJu83NwioWyV852dPCzNXhzdtXVJwMAJ"
        const val TESTNET_TRANSPARENT_BAD_CHECKSUM = "WTNjqDPXEGEgKHS1YPtfEgdrqk6egFRULDy"
    }
}

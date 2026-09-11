package cash.w.sdk.consumertest

import android.app.Application
import cash.w.sdk.WcashWalletSeed
import cash.w.sdk.WcashWalletTool
import cash.w.sdk.model.WcashAccountIndex
import cash.w.sdk.model.WcashNetwork

/** Executes a real JNI derivation from the separately minified consumer process. */
class WcashConsumerProbeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val bip39Seed = canonicalBip39Seed()
        val seed = WcashWalletSeed.fromBip39SeedBytes(bip39Seed)
        val address =
            try {
                WcashWalletTool
                    .create()
                    .deriveIronwoodAddress(
                        seed,
                        WcashNetwork.Testnet,
                        WcashAccountIndex.from(0)
                    ).encoded
            } finally {
                seed.close()
                bip39Seed.fill(0)
            }

        check(address == EXPECTED_TESTNET_ADDRESS) {
            "Minified Wcash consumer derived an unexpected address"
        }
        getSharedPreferences(PROBE_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(PROBE_ADDRESS, address)
            .commit()
    }

    private companion object {
        const val PROBE_PREFERENCES = "wcash-sdk-consumer-probe"
        const val PROBE_ADDRESS = "derived-address"
        const val EXPECTED_TESTNET_ADDRESS =
            "wutest18rmpm4xcm2d54xg5mg00lac9pg4txaladyp6pacqhm355n5scpn5gja6hy43" +
                "uqassvr63g6xuephu8r0qju92778lg4v5nkxfu7j3la6"
    }
}

@Suppress("LongMethod", "MagicNumber")
internal fun canonicalBip39Seed(): ByteArray =
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

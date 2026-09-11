package cash.w.sdk.consumertest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cash.w.sdk.WcashWalletSeed
import cash.w.sdk.WcashWalletTool
import cash.w.sdk.model.WcashAccountIndex
import cash.w.sdk.model.WcashNetwork
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WcashMinifiedConsumerTest {
    @Test
    fun consumer_rules_preserve_seed_and_real_jni_derivation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationAddress =
            context
                .getSharedPreferences(PROBE_PREFERENCES, Context.MODE_PRIVATE)
                .getString(PROBE_ADDRESS, null)
        assertEquals(EXPECTED_TESTNET_ADDRESS, applicationAddress)

        val bip39Seed = canonicalBip39Seed()
        val seed = WcashWalletSeed.fromBip39SeedBytes(bip39Seed)
        try {
            val derived =
                WcashWalletTool
                    .create()
                    .deriveIronwoodAddress(
                        seed,
                        WcashNetwork.Testnet,
                        WcashAccountIndex.from(0)
                    )
            assertEquals(EXPECTED_TESTNET_ADDRESS, derived.encoded)
        } finally {
            seed.close()
            bip39Seed.fill(0)
        }
    }

    private companion object {
        const val PROBE_PREFERENCES = "wcash-sdk-consumer-probe"
        const val PROBE_ADDRESS = "derived-address"
        const val EXPECTED_TESTNET_ADDRESS =
            "wutest18rmpm4xcm2d54xg5mg00lac9pg4txaladyp6pacqhm355n5scpn5gja6hy43" +
                "uqassvr63g6xuephu8r0qju92778lg4v5nkxfu7j3la6"
    }
}

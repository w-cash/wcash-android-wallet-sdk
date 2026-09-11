package cash.z.ecc.android.sdk.internal.jni

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RustWcashWalletTest {
    @Test
    fun derives_and_parses_the_frozen_testnet_address_through_jni() =
        runTest {
            val backend = RustWcashWallet.new()
            val address = backend.deriveIronwoodAddress(VECTOR_SEED, TESTNET_ID, ACCOUNT_ZERO)

            assertEquals(TESTNET_ADDRESS, address)
            assertEquals(TESTNET_ID, backend.parseIronwoodAddressNetwork(address))
        }

    @Test
    fun unsupported_networks_and_non_wcash_addresses_fail_closed_through_jni() =
        runTest {
            val backend = RustWcashWallet.new()

            assertFailsWith<RuntimeException> {
                backend.deriveIronwoodAddress(VECTOR_SEED, UNSUPPORTED_NETWORK_ID, ACCOUNT_ZERO)
            }
            assertFailsWith<RuntimeException> {
                backend.parseIronwoodAddressNetwork(ZCASH_TESTNET_ADDRESS)
            }
        }

    private companion object {
        const val TESTNET_ID = 1
        const val UNSUPPORTED_NETWORK_ID = 3
        const val ACCOUNT_ZERO = 0L
        val VECTOR_SEED = ByteArray(32) { it.toByte() }
        const val TESTNET_ADDRESS =
            "wutest17mvne4ygv9v8rkjf6yxnrveceejh8nutee8svp8swkgj7s7ac9ga36u2av8" +
                "hgpc28cc42u474ypjq2jsdt64utcxtztm2jr6guvaryhh"
        const val ZCASH_TESTNET_ADDRESS =
            "utest10c5kutapazdnf8ztl3pu43nkfsjx89fy3uuff8tsmxm6s86j37pe7uz94z5jhkl" +
                "49pqe8yz75rlsaygexk6jpaxwx0esjr8wm5ut7d5s"
    }
}

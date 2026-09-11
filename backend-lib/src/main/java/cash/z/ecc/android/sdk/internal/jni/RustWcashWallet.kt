package cash.z.ecc.android.sdk.internal.jni

/**
 * Minimal native boundary for Wcash wallet-domain primitives.
 *
 * This class deliberately exposes no Zcash network IDs, persistent wallet state, imported keys,
 * transaction APIs, or Wcash Mainnet selector. Public callers should use `WcashWalletTool` from
 * `sdk-lib`, which wraps all domain values in semantic types.
 */
class RustWcashWallet private constructor() {
    fun isValidSeed(seed: ByteArray): Boolean = isValidSeedNative(seed)

    fun deriveIronwoodAddress(
        seed: ByteArray,
        networkId: Int,
        accountIndex: Long,
    ): String = deriveIronwoodAddressNative(seed, networkId, accountIndex)

    fun parseIronwoodAddressNetwork(address: String): Int = parseIronwoodAddressNetworkNative(address)

    companion object {
        suspend fun new(): RustWcashWallet {
            RustBackend.loadLibrary()
            return RustWcashWallet()
        }

        @JvmStatic
        private external fun isValidSeedNative(seed: ByteArray): Boolean

        @JvmStatic
        private external fun deriveIronwoodAddressNative(
            seed: ByteArray,
            networkId: Int,
            accountIndex: Long,
        ): String

        @JvmStatic
        private external fun parseIronwoodAddressNetworkNative(address: String): Int
    }
}

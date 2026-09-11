package cash.z.ecc.android.sdk.tool

import cash.z.ecc.android.sdk.internal.jni.RustWcashWallet
import cash.z.ecc.android.sdk.model.WcashAccountIndex
import cash.z.ecc.android.sdk.model.WcashIronwoodAddress
import cash.z.ecc.android.sdk.model.WcashNetwork
import cash.z.ecc.android.sdk.model.WcashWalletSeed
import cash.z.ecc.android.sdk.model.fromNativeId
import cash.z.ecc.android.sdk.model.nativeId

/**
 * Minimal Wcash-only derivation surface.
 *
 * The tool supports Wcash Testnet and Regtest only. It does not expose Wcash Mainnet, Zcash key
 * formats, viewing/spending-key import or export, synchronization, persistence, or transactions.
 */
class WcashWalletTool private constructor(
    private val backend: WcashWalletNative,
) {
    /**
     * Derives the account's default Ironwood-only receiving address.
     *
     * The native implementation applies the Wcash seed KDF before ZIP 32 derivation. It then
     * validates the derived address and its network before returning a semantic address value.
     */
    @Throws(WcashWalletDerivationException::class)
    fun deriveIronwoodAddress(
        seed: WcashWalletSeed,
        network: WcashNetwork,
        accountIndex: WcashAccountIndex,
    ): WcashIronwoodAddress {
        val seedBytes = seed.copyBytes()
        val address =
            try {
                nativeDerivation {
                    backend.deriveIronwoodAddress(
                        seed = seedBytes,
                        networkId = network.nativeId(),
                        accountIndex = accountIndex.index,
                    )
                }
            } finally {
                seedBytes.fill(0)
            }

        val encodedNetwork =
            nativeDerivation {
                WcashNetwork.fromNativeId(backend.parseIronwoodAddressNetwork(address))
            }
        if (encodedNetwork != network) {
            throw WcashWalletDerivationException()
        }

        return WcashIronwoodAddress.fromValidated(address, encodedNetwork)
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> nativeDerivation(block: () -> T): T =
        try {
            block()
        } catch (cause: RuntimeException) {
            throw WcashWalletDerivationException(cause)
        }

    companion object {
        /** Loads the native library and returns a Wcash-only derivation tool. */
        suspend fun new(): WcashWalletTool = WcashWalletTool(RustWcashWalletNative(RustWcashWallet.new()))

        internal fun newForTests(backend: WcashWalletNative): WcashWalletTool = WcashWalletTool(backend)
    }
}

/** A fixed-message error returned when Wcash account or address derivation fails. */
class WcashWalletDerivationException internal constructor(
    cause: Throwable? = null,
) : Exception("Could not derive Wcash Ironwood receiving address", cause)

internal interface WcashWalletNative {
    fun deriveIronwoodAddress(
        seed: ByteArray,
        networkId: Int,
        accountIndex: Long,
    ): String

    fun parseIronwoodAddressNetwork(address: String): Int
}

private class RustWcashWalletNative(
    private val backend: RustWcashWallet,
) : WcashWalletNative {
    override fun deriveIronwoodAddress(
        seed: ByteArray,
        networkId: Int,
        accountIndex: Long,
    ): String = backend.deriveIronwoodAddress(seed, networkId, accountIndex)

    override fun parseIronwoodAddressNetwork(address: String): Int =
        backend.parseIronwoodAddressNetwork(address)
}

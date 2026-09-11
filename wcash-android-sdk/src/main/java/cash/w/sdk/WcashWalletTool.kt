package cash.w.sdk

import cash.w.sdk.internal.jni.JniWcashAddress
import cash.w.sdk.internal.jni.JniWcashBackend
import cash.w.sdk.internal.jni.WcashNativeBackend
import cash.w.sdk.model.WcashAccountIndex
import cash.w.sdk.model.WcashIronwoodAddress
import cash.w.sdk.model.WcashNetwork
import cash.w.sdk.model.WcashTransparentP2pkhAddress

/**
 * Minimal Wcash-only wallet derivation surface.
 *
 * This version derives and validates addresses for Wcash Testnet and Regtest. It deliberately
 * exposes no Mainnet selector, synchronization, persistence, transaction, or Zcash API.
 */
class WcashWalletTool private constructor(
    private val backend: WcashNativeBackend
) {
    /**
     * Derives [accountIndex]'s default Ironwood-only receiving address on [network].
     *
     * Seed bytes are copied only for the duration of the native call and that copy is overwritten
     * before this function returns. The native result is independently parsed and must attest the
     * requested network.
     */
    @Throws(WcashWalletDerivationException::class)
    fun deriveIronwoodAddress(
        seed: WcashWalletSeed,
        network: WcashNetwork,
        accountIndex: WcashAccountIndex
    ): WcashIronwoodAddress {
        val derived =
            nativeCall {
                seed.useCopy { seedCopy ->
                    backend.deriveIronwoodAddress(
                        seed = seedCopy,
                        networkCode = network.nativeCode,
                        accountIndex = accountIndex.nativeValue
                    )
                }
            }
        val parsed = nativeCall { backend.parseIronwoodAddress(derived.encoded) }

        if (derived != parsed || parsed.networkCode != network.nativeCode) {
            throw WcashWalletDerivationException()
        }

        return WcashIronwoodAddress.fromNative(parsed)
    }

    /**
     * Derives [accountIndex]'s default transparent P2PKH coinbase address on [network].
     *
     * The native result is independently reparsed as an exact Base58Check encoding and must
     * attest the requested network. This method does not derive P2SH or TEX addresses.
     */
    @Throws(WcashWalletDerivationException::class)
    fun deriveTransparentCoinbaseAddress(
        seed: WcashWalletSeed,
        network: WcashNetwork,
        accountIndex: WcashAccountIndex
    ): WcashTransparentP2pkhAddress {
        val derived =
            nativeCall {
                seed.useCopy { seedCopy ->
                    backend.deriveTransparentCoinbaseAddress(
                        seed = seedCopy,
                        networkCode = network.nativeCode,
                        accountIndex = accountIndex.nativeValue
                    )
                }
            }
        val parsed = nativeCall { backend.parseTransparentP2pkhAddress(derived.encoded) }

        if (derived != parsed || parsed.networkCode != network.nativeCode) {
            throw WcashWalletDerivationException()
        }

        return WcashTransparentP2pkhAddress.fromNative(parsed)
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> nativeCall(block: () -> T): T =
        try {
            block()
        } catch (_: RuntimeException) {
            throw WcashWalletDerivationException()
        }

    companion object {
        /** Loads the isolated Wcash native library and returns a derivation tool. */
        @JvmStatic
        fun create(): WcashWalletTool = WcashWalletTool(JniWcashBackend)

        internal fun createForTests(backend: WcashNativeBackend): WcashWalletTool =
            WcashWalletTool(backend)
    }
}

/** Fixed-message failure from Wcash key or address derivation. */
class WcashWalletDerivationException internal constructor() : Exception("Wcash address derivation failed")

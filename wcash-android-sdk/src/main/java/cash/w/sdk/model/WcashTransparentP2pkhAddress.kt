package cash.w.sdk.model

import cash.w.sdk.internal.jni.JniWcashBackend
import cash.w.sdk.internal.jni.JniWcashTransparentP2pkhAddress

/** A canonical Wcash transparent P2PKH address bound to its encoded network. */
class WcashTransparentP2pkhAddress private constructor(
    /** Canonical, case-sensitive Base58Check payment-address encoding. */
    val encoded: String,
    /** Network selected by the version bytes and checksum. */
    val network: WcashNetwork
) {
    override fun equals(other: Any?): Boolean =
        other is WcashTransparentP2pkhAddress && encoded == other.encoded && network == other.network

    override fun hashCode(): Int = 31 * encoded.hashCode() + network.hashCode()

    override fun toString(): String = encoded

    companion object {
        /**
         * Parses an exact Wcash P2PKH Base58Check address through the Rust library.
         *
         * Unified, P2SH, TEX, Zcash, non-canonical, and malformed encodings are rejected.
         */
        @JvmStatic
        @Suppress("TooGenericExceptionCaught")
        fun parse(encoded: String): WcashTransparentP2pkhAddress =
            try {
                fromNative(JniWcashBackend.parseTransparentP2pkhAddress(encoded))
            } catch (_: RuntimeException) {
                throw IllegalArgumentException("Invalid Wcash transparent P2PKH address")
            }

        internal fun fromNative(
            address: JniWcashTransparentP2pkhAddress
        ): WcashTransparentP2pkhAddress =
            WcashTransparentP2pkhAddress(
                encoded = address.encoded,
                network = WcashNetwork.fromNativeCode(address.networkCode)
            )
    }
}

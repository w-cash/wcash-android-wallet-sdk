package cash.w.sdk.model

import cash.w.sdk.internal.jni.JniWcashAddress
import cash.w.sdk.internal.jni.JniWcashBackend

/** A canonical Ironwood-only Wcash Unified Address bound to its encoded network. */
class WcashIronwoodAddress private constructor(
    /** Canonical public payment-address encoding. */
    val encoded: String,
    /** Network selected by the address encoding and checksum. */
    val network: WcashNetwork
) {
    override fun equals(other: Any?): Boolean =
        other is WcashIronwoodAddress && encoded == other.encoded && network == other.network

    override fun hashCode(): Int = 31 * encoded.hashCode() + network.hashCode()

    override fun toString(): String = encoded

    companion object {
        /**
         * Parses and canonicalizes an app-provided Wcash address through the Rust library.
         *
         * Zcash addresses, transparent-only Wcash addresses, Unified Addresses that also expose a
         * transparent receiver, unsupported receiver combinations, and malformed encodings are
         * rejected.
         */
        @JvmStatic
        @Suppress("TooGenericExceptionCaught")
        fun parse(encoded: String): WcashIronwoodAddress =
            try {
                fromNative(JniWcashBackend.parseIronwoodAddress(encoded))
            } catch (_: RuntimeException) {
                throw IllegalArgumentException("Invalid Wcash Ironwood address")
            }

        internal fun fromNative(address: JniWcashAddress): WcashIronwoodAddress =
            WcashIronwoodAddress(
                encoded = address.encoded,
                network = WcashNetwork.fromNativeCode(address.networkCode)
            )
    }
}

package cash.z.ecc.android.sdk.model

import cash.z.ecc.android.sdk.internal.jni.RustWcashWallet

/** A validated Ironwood-capable Wcash Unified Address and its encoded network identity. */
class WcashIronwoodAddress private constructor(
    /** Canonical Wcash address encoding. This is public recipient data, not key material. */
    val value: String,
    /** Network encoded by [value]. */
    val network: WcashNetwork,
) {
    override fun equals(other: Any?): Boolean =
        other is WcashIronwoodAddress && value == other.value && network == other.network

    override fun hashCode(): Int = 31 * value.hashCode() + network.hashCode()

    override fun toString(): String = value

    companion object {
        /**
         * Parses [encoded] through the Wcash Rust backend.
         *
         * Zcash encodings, transparent-only Wcash addresses, malformed payloads, and unsupported
         * receiver combinations are rejected with a fixed error message.
         */
        suspend fun new(encoded: String): WcashIronwoodAddress {
            val backend = RustWcashWallet.new()
            val networkId = parseNetwork(encoded, backend)
            return fromValidated(encoded, WcashNetwork.fromNativeId(networkId))
        }

        @Suppress("TooGenericExceptionCaught")
        private fun parseNetwork(
            encoded: String,
            backend: RustWcashWallet,
        ): Int =
            try {
                backend.parseIronwoodAddressNetwork(encoded)
            } catch (cause: RuntimeException) {
                throw IllegalArgumentException("Invalid Wcash Ironwood address", cause)
            }

        internal fun fromValidated(
            encoded: String,
            network: WcashNetwork,
        ): WcashIronwoodAddress = WcashIronwoodAddress(encoded, network)
    }
}

internal fun WcashNetwork.nativeId(): Int =
    when (this) {
        WcashNetwork.Testnet -> 1
        WcashNetwork.Regtest -> 2
    }

internal fun WcashNetwork.Companion.fromNativeId(networkId: Int): WcashNetwork =
    when (networkId) {
        1 -> WcashNetwork.Testnet
        2 -> WcashNetwork.Regtest
        else -> throw IllegalArgumentException("Unsupported Wcash network")
    }

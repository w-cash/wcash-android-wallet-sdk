package cash.w.sdk.internal.jni

import androidx.annotation.Keep

/** Raw JNI boundary; all public entry points wrap these values in Wcash semantic types. */
@Keep
internal class WcashNativeBridge private constructor() {
    @Suppress("TooManyFunctions")
    companion object {
        init {
            System.loadLibrary("wcashwalletsdk")
        }

        @Keep
        @JvmStatic
        private external fun validateSeedNative(seed: ByteArray): Boolean

        @Keep
        @JvmStatic
        private external fun deriveIronwoodAddressNative(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashAddress

        @Keep
        @JvmStatic
        private external fun parseIronwoodAddressNative(encoded: String): JniWcashAddress

        @Keep
        @JvmStatic
        private external fun deriveTransparentCoinbaseAddressNative(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashTransparentP2pkhAddress

        @Keep
        @JvmStatic
        private external fun parseTransparentP2pkhAddressNative(
            encoded: String
        ): JniWcashTransparentP2pkhAddress

        @Keep
        @JvmStatic
        private external fun networkIdentityNative(networkCode: Int): String

        @JvmSynthetic
        internal fun validateSeed(seed: ByteArray): Boolean = validateSeedNative(seed)

        @JvmSynthetic
        internal fun deriveIronwoodAddress(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashAddress = deriveIronwoodAddressNative(seed, networkCode, accountIndex)

        @JvmSynthetic
        internal fun parseIronwoodAddress(encoded: String): JniWcashAddress =
            parseIronwoodAddressNative(encoded)

        @JvmSynthetic
        internal fun deriveTransparentCoinbaseAddress(
            seed: ByteArray,
            networkCode: Int,
            accountIndex: Long
        ): JniWcashTransparentP2pkhAddress =
            deriveTransparentCoinbaseAddressNative(seed, networkCode, accountIndex)

        @JvmSynthetic
        internal fun parseTransparentP2pkhAddress(encoded: String): JniWcashTransparentP2pkhAddress =
            parseTransparentP2pkhAddressNative(encoded)

        @JvmSynthetic
        internal fun networkIdentity(networkCode: Int): String = networkIdentityNative(networkCode)
    }
}

internal interface WcashNativeBackend {
    @JvmSynthetic
    fun validateSeed(seed: ByteArray): Boolean

    @JvmSynthetic
    fun deriveIronwoodAddress(
        seed: ByteArray,
        networkCode: Int,
        accountIndex: Long
    ): JniWcashAddress

    @JvmSynthetic
    fun parseIronwoodAddress(encoded: String): JniWcashAddress

    @JvmSynthetic
    fun deriveTransparentCoinbaseAddress(
        seed: ByteArray,
        networkCode: Int,
        accountIndex: Long
    ): JniWcashTransparentP2pkhAddress

    @JvmSynthetic
    fun parseTransparentP2pkhAddress(encoded: String): JniWcashTransparentP2pkhAddress

    @JvmSynthetic
    fun networkIdentity(networkCode: Int): String
}

internal object JniWcashBackend : WcashNativeBackend {
    override fun validateSeed(seed: ByteArray): Boolean =
        WcashNativeBridge.validateSeed(seed)

    override fun deriveIronwoodAddress(
        seed: ByteArray,
        networkCode: Int,
        accountIndex: Long
    ): JniWcashAddress =
        WcashNativeBridge.deriveIronwoodAddress(seed, networkCode, accountIndex)

    override fun parseIronwoodAddress(encoded: String): JniWcashAddress =
        WcashNativeBridge.parseIronwoodAddress(encoded)

    override fun deriveTransparentCoinbaseAddress(
        seed: ByteArray,
        networkCode: Int,
        accountIndex: Long
    ): JniWcashTransparentP2pkhAddress =
        WcashNativeBridge.deriveTransparentCoinbaseAddress(seed, networkCode, accountIndex)

    override fun parseTransparentP2pkhAddress(encoded: String): JniWcashTransparentP2pkhAddress =
        WcashNativeBridge.parseTransparentP2pkhAddress(encoded)

    override fun networkIdentity(networkCode: Int): String =
        WcashNativeBridge.networkIdentity(networkCode)
}

package cash.w.sdk.internal.jni

import androidx.annotation.Keep

/** Rust-owned transparent P2PKH transport value. */
@Keep
internal class JniWcashTransparentP2pkhAddress
    @Keep
    constructor(
        @get:JvmSynthetic internal val encoded: String,
        @get:JvmSynthetic internal val networkCode: Int
    ) {
        override fun equals(other: Any?): Boolean =
            other is JniWcashTransparentP2pkhAddress &&
                encoded == other.encoded &&
                networkCode == other.networkCode

        override fun hashCode(): Int = 31 * encoded.hashCode() + networkCode

        override fun toString(): String = "JniWcashTransparentP2pkhAddress([INTERNAL])"
    }

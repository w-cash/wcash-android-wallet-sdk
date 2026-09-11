package cash.w.sdk.internal.jni

import androidx.annotation.Keep

/** Rust-owned transport value. Public callers receive `WcashIronwoodAddress` instead. */
@Keep
internal class JniWcashAddress
    @Keep
    constructor(
        @get:JvmSynthetic internal val encoded: String,
        @get:JvmSynthetic internal val networkCode: Int
    ) {
        override fun equals(other: Any?): Boolean =
            other is JniWcashAddress && encoded == other.encoded && networkCode == other.networkCode

        override fun hashCode(): Int = 31 * encoded.hashCode() + networkCode

        override fun toString(): String = "JniWcashAddress([INTERNAL])"
    }

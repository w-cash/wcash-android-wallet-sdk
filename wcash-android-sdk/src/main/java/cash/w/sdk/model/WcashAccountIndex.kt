package cash.w.sdk.model

/** A validated 31-bit account identifier for Wcash ZIP 32 derivation. */
class WcashAccountIndex private constructor(
    @get:JvmSynthetic
    internal val nativeValue: Long
) {
    override fun equals(other: Any?): Boolean =
        other is WcashAccountIndex && nativeValue == other.nativeValue

    override fun hashCode(): Int = nativeValue.hashCode()

    override fun toString(): String = "WcashAccountIndex($nativeValue)"

    companion object {
        private const val MIN_VALUE = 0L
        private const val MAX_VALUE = 0x7FFF_FFFFL

        /**
         * Converts an app-provided account number into a Wcash account identifier.
         *
         * @throws IllegalArgumentException if [value] is outside the ZIP 32 account range.
         */
        @JvmStatic
        fun from(value: Long): WcashAccountIndex {
            require(value in MIN_VALUE..MAX_VALUE) {
                "Wcash account index is outside the allowed 31-bit range"
            }
            return WcashAccountIndex(value)
        }
    }
}

package cash.z.ecc.android.sdk.model

/**
 * A validated 31-bit account index for Wcash ZIP 32 derivation.
 *
 * Wcash account indices are always hardened by the native derivation path, so values at or above
 * 2^31 are not valid account identifiers.
 */
@ConsistentCopyVisibility
data class WcashAccountIndex internal constructor(
    val index: Long,
) {
    init {
        require(index in MIN_VALUE..MAX_VALUE) {
            "Wcash account index is outside the allowed 31-bit range"
        }
    }

    companion object {
        private const val MIN_VALUE = 0L
        private const val MAX_VALUE = 0x7FFF_FFFFL

        fun new(index: Long): WcashAccountIndex = WcashAccountIndex(index)
    }
}

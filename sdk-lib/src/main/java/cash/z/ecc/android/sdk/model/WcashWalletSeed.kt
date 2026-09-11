package cash.z.ecc.android.sdk.model

import cash.z.ecc.android.sdk.internal.jni.RustWcashWallet

/**
 * Validated caller-owned entropy for Wcash wallet derivation.
 *
 * This type is intentionally not interchangeable with a Zcash seed or derived key. Construction
 * validates the seed through the Wcash Rust backend and retains a private copy. The copy is never
 * included in string rendering.
 */
class WcashWalletSeed private constructor(
    private val bytes: FirstClassByteArray,
) {
    internal fun copyBytes(): ByteArray = bytes.byteArray.copyOf()

    override fun toString(): String = "WcashWalletSeed(bytes=***)"

    companion object {
        /**
         * Copies and validates [bytes] as Wcash wallet seed entropy.
         *
         * This is the app-facing conversion boundary from raw entropy to a semantic seed type.
         * Invalid material is rejected without placing its contents in an exception message.
         */
        suspend fun new(bytes: ByteArray): WcashWalletSeed {
            val backend = RustWcashWallet.new()
            return fromCandidate(bytes, backend::isValidSeed)
        }

        @Suppress("TooGenericExceptionCaught")
        internal fun fromCandidate(
            bytes: ByteArray,
            validator: (ByteArray) -> Boolean,
        ): WcashWalletSeed {
            val copy = bytes.copyOf()
            val validationCopy = copy.copyOf()
            try {
                require(validateCandidate(validationCopy, validator)) {
                    "Invalid Wcash wallet seed"
                }
                return WcashWalletSeed(FirstClassByteArray(copy))
            } catch (failure: Throwable) {
                copy.fill(0)
                throw failure
            } finally {
                validationCopy.fill(0)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun validateCandidate(
            candidate: ByteArray,
            validator: (ByteArray) -> Boolean,
        ): Boolean =
            try {
                validator(candidate)
            } catch (cause: RuntimeException) {
                throw IllegalStateException("Could not validate Wcash wallet seed", cause)
            }
    }
}

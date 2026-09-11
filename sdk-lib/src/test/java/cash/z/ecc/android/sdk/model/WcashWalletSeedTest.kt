package cash.z.ecc.android.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WcashWalletSeedTest {
    @Test
    fun candidate_is_copied_and_rendering_is_redacted() {
        val candidate = ByteArray(32) { it.toByte() }
        var validationCopy: ByteArray? = null
        val validated =
            WcashWalletSeed.fromCandidate(candidate) {
                validationCopy = it
                true
            }

        candidate.fill(0)

        assertEquals((0 until 32).map(Int::toByte), validated.copyBytes().toList())
        assertEquals(ByteArray(32).toList(), validationCopy?.toList())
        assertEquals("WcashWalletSeed(bytes=***)", validated.toString())
    }

    @Test
    fun rejected_candidate_is_not_embedded_in_the_error() {
        val marker = "private-seed-marker"
        val error =
            assertFailsWith<IllegalArgumentException> {
                WcashWalletSeed.fromCandidate(marker.encodeToByteArray()) { false }
            }

        assertEquals("Invalid Wcash wallet seed", error.message)
        assertEquals(false, error.toString().contains(marker))
    }

    @Test
    fun validator_failure_is_collapsed_to_the_fixed_error() {
        val backendMessage = "backend accidentally included sensitive input"
        var validationCopy: ByteArray? = null
        val error =
            assertFailsWith<IllegalStateException> {
                WcashWalletSeed.fromCandidate(ByteArray(32) { 3 }) {
                    validationCopy = it
                    throw IllegalStateException(backendMessage)
                }
            }

        assertEquals("Could not validate Wcash wallet seed", error.message)
        assertEquals(false, error.toString().contains(backendMessage))
        assertEquals(ByteArray(32).toList(), validationCopy?.toList())
    }
}

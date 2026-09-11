package cash.w.sdk

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WcashWalletSeedTest {
    @Test
    fun caller_input_is_copied_and_rendering_is_redacted() {
        val input = ByteArray(64) { it.toByte() }
        val expected = input.copyOf()
        val seed = WcashWalletSeed.fromCandidate(input) { true }

        input.fill(0)

        seed.useCopy { assertContentEquals(expected, it) }
        assertEquals("WcashWalletSeed([REDACTED])", seed.toString())
        assertFalse(seed.toString().contains(expected.joinToString()))
        seed.close()
    }

    @Test
    fun validation_and_derivation_copies_are_overwritten() {
        var validationReference: ByteArray? = null
        val seed =
            WcashWalletSeed.fromCandidate(ByteArray(64) { 7 }) {
                validationReference = it
                true
            }
        var derivationReference: ByteArray? = null

        seed.useCopy { derivationReference = it }

        assertContentEquals(ByteArray(64), validationReference)
        assertContentEquals(ByteArray(64), derivationReference)
        seed.close()
    }

    @Test
    fun close_overwrites_retained_entropy_and_prevents_reuse() {
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64) { 9 }) { true }
        val holderField =
            WcashWalletSeed::class.java.getDeclaredField("secret").apply { isAccessible = true }
        val holder = holderField.get(seed)
        val secretField = holder.javaClass.getDeclaredField("secret").apply { isAccessible = true }

        seed.close()
        seed.close()

        assertContentEquals(ByteArray(64), secretField.get(holder) as ByteArray)
        val failure = assertFailsWith<IllegalStateException> { seed.useCopy { error("unused") } }
        assertEquals("Wcash wallet seed has been destroyed", failure.message)
    }

    @Test
    fun derivation_copy_is_overwritten_when_the_operation_fails() {
        val seed = WcashWalletSeed.fromCandidate(ByteArray(64) { 5 }) { true }
        var derivationReference: ByteArray? = null

        assertFailsWith<IllegalArgumentException> {
            seed.useCopy {
                derivationReference = it
                throw IllegalArgumentException("fixed failure")
            }
        }

        assertContentEquals(ByteArray(64), derivationReference)
        seed.close()
    }

    @Test
    fun source_level_public_surface_is_semantic() {
        val publicMethods =
            WcashWalletSeed::class.java.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .map { it.name }
                .toSet()

        assertEquals(setOf("close", "fromBip39SeedBytes", "toString"), publicMethods)
        assertTrue(WcashWalletSeed::class.java.declaredConstructors.none { Modifier.isPublic(it.modifiers) })
        assertTrue(
            WcashWalletSeed::class.java.declaredFields.none {
                Modifier.isPublic(it.modifiers) && it.type == ByteArray::class.java
            }
        )
    }

    @Test
    fun mnemonic_entropy_is_rejected_before_native_validation() {
        listOf(16, 20, 24, 28, 32, 63, 65).forEach { length ->
            var validatorWasCalled = false
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    WcashWalletSeed.fromCandidate(ByteArray(length)) {
                        validatorWasCalled = true
                        true
                    }
                }

            assertFalse(validatorWasCalled)
            assertEquals("Invalid Wcash BIP-39 seed", failure.message)
        }
    }

    @Test
    fun validation_failures_have_fixed_non_secret_messages() {
        val marker = "private-seed-marker"
        val invalid =
            assertFailsWith<IllegalArgumentException> {
                WcashWalletSeed.fromCandidate(ByteArray(64) { 3 }) { false }
            }
        val backendFailure =
            assertFailsWith<IllegalStateException> {
                WcashWalletSeed.fromCandidate(ByteArray(64)) {
                    throw IllegalStateException(marker)
                }
            }

        assertEquals("Invalid Wcash BIP-39 seed", invalid.message)
        assertEquals("Could not validate Wcash BIP-39 seed", backendFailure.message)
        assertFalse(invalid.toString().contains(marker))
        assertFalse(backendFailure.toString().contains(marker))
        assertEquals(null, backendFailure.cause)
    }
}

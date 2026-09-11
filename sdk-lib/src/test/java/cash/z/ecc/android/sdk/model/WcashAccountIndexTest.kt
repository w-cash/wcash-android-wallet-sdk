package cash.z.ecc.android.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WcashAccountIndexTest {
    @Test
    fun accepts_the_complete_native_account_range() {
        assertEquals(0L, WcashAccountIndex.new(0).index)
        assertEquals(0x7FFF_FFFFL, WcashAccountIndex.new(0x7FFF_FFFFL).index)
    }

    @Test
    fun rejects_values_the_native_account_type_cannot_represent() {
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.new(-1) }
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.new(0x8000_0000L) }
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.new(Long.MAX_VALUE) }
    }
}

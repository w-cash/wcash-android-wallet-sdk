package cash.w.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WcashAccountIndexTest {
    @Test
    fun complete_zip32_account_range_is_accepted() {
        assertEquals(WcashAccountIndex.from(0), WcashAccountIndex.from(0))
        assertEquals(WcashAccountIndex.from(0x7FFF_FFFFL), WcashAccountIndex.from(0x7FFF_FFFFL))
    }

    @Test
    fun values_outside_zip32_account_range_are_rejected() {
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.from(-1) }
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.from(0x8000_0000L) }
        assertFailsWith<IllegalArgumentException> { WcashAccountIndex.from(Long.MAX_VALUE) }
    }
}

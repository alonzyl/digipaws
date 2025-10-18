package nethical.digipaws

import nethical.digipaws.utils.NFCFocusToggle
import org.junit.Assert.*
import org.junit.Test

class NFCFocusStartMethodTest {

    @Test
    fun testStartMethodFromString_tagToggle() {
        val method = NFCFocusToggle.NFCFocusStartMethod.fromString("TAG_TOGGLE")
        assertEquals(NFCFocusToggle.NFCFocusStartMethod.TAG_TOGGLE, method)
    }

    @Test
    fun testStartMethodFromString_manualButton() {
        val method = NFCFocusToggle.NFCFocusStartMethod.fromString("MANUAL_BUTTON")
        assertEquals(NFCFocusToggle.NFCFocusStartMethod.MANUAL_BUTTON, method)
    }

    @Test
    fun testStartMethodFromString_autoSchedule() {
        val method = NFCFocusToggle.NFCFocusStartMethod.fromString("AUTO_SCHEDULE")
        assertEquals(NFCFocusToggle.NFCFocusStartMethod.AUTO_SCHEDULE, method)
    }

    @Test
    fun testStartMethodFromString_null() {
        val method = NFCFocusToggle.NFCFocusStartMethod.fromString(null)
        assertEquals(NFCFocusToggle.NFCFocusStartMethod.TAG_TOGGLE, method)
    }

    @Test
    fun testStartMethodFromString_invalid() {
        val method = NFCFocusToggle.NFCFocusStartMethod.fromString("INVALID")
        assertEquals(NFCFocusToggle.NFCFocusStartMethod.TAG_TOGGLE, method)
    }

    @Test
    fun testStartMethodToString() {
        assertEquals("TAG_TOGGLE", NFCFocusToggle.NFCFocusStartMethod.TAG_TOGGLE.toString())
        assertEquals("MANUAL_BUTTON", NFCFocusToggle.NFCFocusStartMethod.MANUAL_BUTTON.toString())
        assertEquals("AUTO_SCHEDULE", NFCFocusToggle.NFCFocusStartMethod.AUTO_SCHEDULE.toString())
    }
}

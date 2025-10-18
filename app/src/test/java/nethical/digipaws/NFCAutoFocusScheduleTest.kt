package nethical.digipaws

import nethical.digipaws.ui.activity.TimedActionActivity
import org.junit.Assert.*
import org.junit.Test

class NFCAutoFocusScheduleTest {

    @Test
    fun testAutoTimedActionItemCreation() {
        val packages = arrayListOf("com.example.app1", "com.example.app2")
        val item = TimedActionActivity.AutoTimedActionItem(
            title = "Morning Focus",
            startTimeInMins = 480, // 8:00 AM
            endTimeInMins = 600,    // 10:00 AM
            packages = packages
        )

        assertEquals("Morning Focus", item.title)
        assertEquals(480, item.startTimeInMins)
        assertEquals(600, item.endTimeInMins)
        assertEquals(2, item.packages.size)
        assertTrue(item.packages.contains("com.example.app1"))
        assertTrue(item.packages.contains("com.example.app2"))
    }

    @Test
    fun testModeConstants() {
        assertEquals(1, TimedActionActivity.MODE_APP_BLOCKER_CHEAT_HOURS)
        assertEquals(2, TimedActionActivity.MODE_AUTO_FOCUS)
        assertEquals(3, TimedActionActivity.MODE_NFC_AUTO_FOCUS)
    }
}

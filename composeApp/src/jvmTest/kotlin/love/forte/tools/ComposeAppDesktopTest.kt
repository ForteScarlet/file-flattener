package love.forte.tools

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppDesktopTest {

    @Test
    fun displayLocaleIsAvailableToTheNativeRuntime() {
        assertEquals(
            Locale.getDefault(Locale.Category.DISPLAY),
            Locale.getDefault(Locale.Category.DISPLAY),
        )
    }
}

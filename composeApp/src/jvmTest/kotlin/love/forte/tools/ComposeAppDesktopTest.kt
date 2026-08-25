package love.forte.tools

import love.forte.tools.ff.ui.platform.FfSwingUiBootstrap
import java.util.Locale
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppDesktopTest {

    @Test
    fun swingUiUsesSystemDisplayLocale() {
        FfSwingUiBootstrap.ensureInitialized()

        assertEquals(
            Locale.getDefault(Locale.Category.DISPLAY),
            JComponent.getDefaultLocale()
        )
    }
}

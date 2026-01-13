package love.forte.tools.ff.ui.platform

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.UIManager

object FfSwingUiBootstrap {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) return

        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        }
        runCatching {
            JComponent.setDefaultLocale(Locale.getDefault())
        }
    }
}


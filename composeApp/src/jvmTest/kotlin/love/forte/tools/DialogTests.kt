package love.forte.tools

import org.jetbrains.skiko.setSystemLookAndFeel
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.test.Test


/**
 * 
 * @author ForteScarlet 
 */
class DialogTests {

    @Test
    fun test() {
        val chooser = JFileChooser().apply {
            dialogTitle = "TITLE"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = true
        }
        val result = chooser.showOpenDialog(null)
        val list = if (result != JFileChooser.APPROVE_OPTION) emptyList() else chooser.selectedFiles.orEmpty().map { it.toPath() }
        println(list)
    }

    private inline fun <T> withDirectoryDialogProperty(block: () -> T): T {
        val keys = listOf(
            "apple.awt.fileDialogForDirectories",
            "java.awt.fileDialogForDirectories",
        )
        val old = keys.associateWith { System.getProperty(it) }
        keys.forEach { System.setProperty(it, "true") }
        return try {
            block()
        } finally {
            keys.forEach { key ->
                val value = old[key]
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }
}

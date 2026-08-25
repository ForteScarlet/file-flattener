package love.forte.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import dev.nucleusframework.window.material.rememberMaterialTitleBarStyle
import love.forte.tools.ff.storage.FfAppTheme
import love.forte.tools.ff.ui.FfApp
import love.forte.tools.ff.ui.theme.FfTheme
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

@Composable
@Preview
fun App(
    onExit: () -> Unit = {},
    onThemeChanged: (FfAppTheme) -> Unit = {},
) {
    FfApp(onExit = onExit, onThemeChanged = onThemeChanged)
}

@Composable
fun NucleusApplicationScope.FfWindow(onExit: () -> Unit) {
    var theme by remember { mutableStateOf(FfAppTheme.CherryRed) }

    FfTheme(theme = theme) {
        MaterialDecoratedWindow(
            onCloseRequest = onExit,
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            minimumSize = DpSize(900.dp, 600.dp),
            title = "File Flattener",
            icon = painterResource(Res.drawable.icon),
            titleBarStyle = rememberMaterialTitleBarStyle(MaterialTheme.colorScheme),
        ) {
            MaterialTitleBar { Text("File Flattener") }
            App(
                onExit = onExit,
                onThemeChanged = { selectedTheme -> theme = selectedTheme },
            )
        }
    }
}

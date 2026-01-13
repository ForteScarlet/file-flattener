package love.forte.tools.ff.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val FfButtonShape = RoundedCornerShape(10.dp)
private val FfButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
private val FfButtonMinHeight = 46.dp
private val FfIconSpacer = 8.dp

@Composable
fun FfPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = FfButtonMinHeight),
        enabled = enabled,
        shape = FfButtonShape,
        contentPadding = FfButtonPadding,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(FfIconSpacer))
        }
        Text(text)
    }
}

@Composable
fun FfOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = FfButtonMinHeight),
        enabled = enabled,
        shape = FfButtonShape,
        contentPadding = FfButtonPadding,
        colors = ButtonDefaults.outlinedButtonColors(),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(FfIconSpacer))
        }
        Text(text)
    }
}

/**
 * 再次要按钮：灰色（用于“全选/全取消”等不关键操作）。
 */
@Composable
fun FfTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = FfButtonMinHeight),
        enabled = enabled,
        shape = FfButtonShape,
        contentPadding = FfButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(FfIconSpacer))
        }
        Text(text)
    }
}

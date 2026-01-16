package love.forte.tools.ff.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 响应式居中内容布局
 *
 * 当窗口宽度足够时，内容会居中并与边框保持距离；
 * 当窗口宽度较小时，内容会填满可用空间。
 */
@Composable
fun FfCenteredContentLayout(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 800.dp,
    minWidthForPadding: Dp = 600.dp,
    horizontalPadding: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shouldApplyPadding = maxWidth >= minWidthForPadding

        Box(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxSize()
                .then(
                    if (shouldApplyPadding) {
                        Modifier.padding(horizontal = horizontalPadding)
                    } else {
                        Modifier
                    }
                ),
        ) {
            content()
        }
    }
}

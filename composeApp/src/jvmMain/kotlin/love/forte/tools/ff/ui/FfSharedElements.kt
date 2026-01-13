package love.forte.tools.ff.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object FfSharedKeys {
    const val NavWorkspace: String = "nav-workspace"
    const val NavConfig: String = "nav-config"
    const val NavAbout: String = "nav-about"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ffSharedNavModifier(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val state = rememberSharedContentState(key = key)
    return Modifier.sharedElement(sharedContentState = state, animatedVisibilityScope = animatedVisibilityScope)
}

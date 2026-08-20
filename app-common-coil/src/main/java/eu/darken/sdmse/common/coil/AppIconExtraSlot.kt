package eu.darken.sdmse.common.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.io.R as IoR

/**
 * Shared `ProgressOverlay` extra slot: renders a [Pkg] progress payload as its launcher icon.
 *
 * `Progress.Data.extra` is `Any?` because `app-common` sits below every module that knows what an app
 * is. This is the single place that turns the payload back into something drawable; any other payload
 * type renders nothing at all, leaving no reserved space behind.
 */
val AppIconExtraSlot: @Composable (extra: Any, modifier: Modifier) -> Unit = { extra, modifier ->
    (extra as? Pkg)?.let {
        AppIconImage(
            pkg = it,
            modifier = modifier,
            placeholder = painterResource(IoR.drawable.ic_default_app_icon_24),
        )
    }
}

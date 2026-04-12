package eu.darken.sdmse.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import eu.darken.sdmse.common.ca.CaDrawable

@Composable
fun CaDrawable.asComposable(): Painter {
    val drawable = get(LocalContext.current)
    return rememberDrawablePainter(drawable)
}

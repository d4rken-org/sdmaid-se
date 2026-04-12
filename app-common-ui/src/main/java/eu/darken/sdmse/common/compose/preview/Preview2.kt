package eu.darken.sdmse.common.compose.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Preview(showBackground = true, name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class Preview2

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Preview(
    showBackground = true,
    name = "Tablet Light",
    widthDp = 900,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    showBackground = true,
    name = "Tablet Dark",
    widthDp = 900,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class Preview2Tablet

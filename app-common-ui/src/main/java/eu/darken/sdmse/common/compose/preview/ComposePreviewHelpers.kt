package eu.darken.sdmse.common.compose.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.theming.SdmSeTheme
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeState
import eu.darken.sdmse.common.theming.ThemeStyle

@Composable
fun SampleContent(
    text: String = "Sample text",
    action: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = action) { Text("Click Me") }
        }
    }
}

@Preview2
@Composable
fun SampleContentPreview() {
    PreviewWrapper {
        SampleContent(text = "Sample Text")
    }
}

@Composable
fun PreviewWrapper(
    theme: ThemeState = ThemeState(ThemeMode.SYSTEM, style = ThemeStyle.DEFAULT),
    content: @Composable () -> Unit,
) {
    SdmSeTheme(state = theme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

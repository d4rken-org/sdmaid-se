package eu.darken.sdmse.main.ui.settings.acks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler

@Composable
fun AcknowledgementsScreenHost(
    vm: AcknowledgementsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    AcknowledgementsScreen(
        onNavigateUp = vm::navUp,
        onOpenUrl = vm::openUrl,
    )
}

@Composable
internal fun AcknowledgementsScreen(
    onNavigateUp: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val translatorsRaw = stringResource(R.string.translation_translators_people)
    val translatorsList = try {
        translatorsRaw.split(";").joinToString("\n")
    } catch (_: Exception) {
        translatorsRaw
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_acknowledgements_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            // Translations
            SettingsCategoryHeader(text = stringResource(R.string.settings_acknowledgements_category_translations_title))
            Text(
                text = stringResource(R.string.settings_acknowledgements_category_translations_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SettingsPreferenceItem(
                title = stringResource(R.string.translation_translators_title),
                subtitle = translatorsList,
                onClick = {},
            )
            SettingsPreferenceItem(
                title = stringResource(R.string.translation_encourage_title),
                subtitle = stringResource(R.string.translation_encourage_body),
                onClick = { onOpenUrl("https://crowdin.com/project/sdmaid-se") },
            )

            // Thanks
            SettingsCategoryHeader(text = stringResource(R.string.settings_acknowledgements_category_thanks_title))
            Text(
                text = stringResource(R.string.settings_acknowledgements_category_thanks_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SettingsPreferenceItem(
                title = "Max Patchs",
                subtitle = "Thanks for the great icons & graphics",
                onClick = { onOpenUrl("https://twitter.com/maxpatchs") },
            )
            SettingsPreferenceItem(
                title = "crowdin.com",
                subtitle = "For supporting translation of open-source projects",
                onClick = { onOpenUrl("https://crowdin.com/") },
            )

            // Licenses
            SettingsCategoryHeader(text = stringResource(R.string.settings_acknowledgements_category_licenses_title))
            Text(
                text = stringResource(R.string.settings_acknowledgements_category_licenses_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LicenseItem("libRootJava", "Run Java (and Kotlin) code as root! (APACHE 2.0)", "https://github.com/Chainfire/librootjava", onOpenUrl)
            LicenseItem("librootkotlinx", "Run rooted Kotlin JVM code made easy with coroutines. (APACHE 2.0)", "https://github.com/Mygod/librootkotlinx", onOpenUrl)
            LicenseItem("Shizuku", "Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process. (APACHE 2.0)", "https://github.com/RikkaApps/Shizuku", onOpenUrl)
            LicenseItem("Material Design Icons", "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)", "https://github.com/Templarian/MaterialDesign", onOpenUrl)
            LicenseItem("Lottie", "Airbnb's Lottie for Android. (APACHE 2.0)", "https://github.com/airbnb/lottie-android", onOpenUrl)
            LicenseItem("Reorderable", "Reorderable Composables for Jetpack Compose. (APACHE 2.0)", "https://github.com/Calvin-LL/Reorderable", onOpenUrl)
            LicenseItem("ZoomImage", "Library for zoom images, supported Android View, Compose and Compose Multiplatform. (APACHE 2.0)", "https://github.com/panpf/zoomimage", onOpenUrl)
            LicenseItem("Kotlin", "The Kotlin Programming Language. (APACHE 2.0)", "https://github.com/JetBrains/kotlin", onOpenUrl)
            LicenseItem("Dagger", "A fast dependency injector for Android and Java. (APACHE 2.0)", "https://github.com/google/dagger", onOpenUrl)
            LicenseItem("Android", "Android Open Source Project (APACHE 2.0)", "https://source.android.com/source/licenses.html", onOpenUrl)
            LicenseItem("Android", "The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.", "https://developer.android.com/distribute/tools/promote/brand.html", onOpenUrl)
        }
    }
}

@Composable
private fun LicenseItem(
    title: String,
    subtitle: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    SettingsPreferenceItem(
        title = title,
        subtitle = subtitle,
        onClick = { onOpenUrl(url) },
    )
}

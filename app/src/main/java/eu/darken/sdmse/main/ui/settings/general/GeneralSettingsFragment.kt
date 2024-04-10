package eu.darken.sdmse.main.ui.settings.general

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.locale.toList
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.ListPreference2
import eu.darken.sdmse.common.preferences.setupWithEnum
import eu.darken.sdmse.common.uix.PreferenceFragment3
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class GeneralSettingsFragment : PreferenceFragment3() {

    override val vm: GeneralSettingsViewModel by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var oneClickToolDialog: OneClickOptionsDialog

    override val settings: GeneralSettings by lazy { generalSettings }
    override val preferenceFile: Int = R.xml.preferences_general

    private val themeModePref: ListPreference2
        get() = findPreference(settings.themeMode.keyName)!!
    private val themeStylePref: ListPreference2
        get() = findPreference(settings.themeStyle.keyName)!!
    private val updateCheck: CheckBoxPreference
        get() = findPreference(settings.isUpdateCheckEnabled.keyName)!!
    private val oneClickTools: Preference
        get() = findPreference("dashboard.oneclick.tools")!!
    private val languageOverride: Preference
        get() = findPreference("core.ui.language")!!

    private val romTypeOverride: ListPreference2
        get() = findPreference(settings.romTypeDetection.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        romTypeOverride.setupWithEnum(settings.romTypeDetection)

        themeModePref.setupWithEnum(settings.themeMode)
        themeStylePref.setupWithEnum(settings.themeStyle)

        oneClickTools.setOnPreferenceClickListener {
            oneClickToolDialog.show(requireContext())
            true
        }

        languageOverride.apply {
            isVisible = hasApiLevel(33)
            setOnPreferenceClickListener {
                vm.showLanguagePicker()
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.isPro.observe2(this) { isPro ->
            themeModePref.alternativeClickListener = when {
                isPro -> null

                else -> {
                    {
                        MainDirections.goToUpgradeFragment(forced = true).navigate()
                    }
                }
            }
            themeStylePref.alternativeClickListener = when {
                isPro -> null

                else -> {
                    {
                        MainDirections.goToUpgradeFragment(forced = true).navigate()
                    }
                }
            }
        }

        vm.isUpdateCheckSupported.observe2(this) {
            updateCheck.isVisible = it
        }

        vm.currentLocales.observe2(this) { locales ->
            val names = locales.toList().first().displayName.ifEmpty { locales.toString() }
            languageOverride.summary = getString(R.string.ui_language_override_desc, names)
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
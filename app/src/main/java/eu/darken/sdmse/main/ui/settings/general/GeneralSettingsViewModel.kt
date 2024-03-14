package eu.darken.sdmse.main.ui.settings.general

import android.annotation.SuppressLint
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.locale.LocaleManager
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val updateChecker: UpdateChecker,
    private val localeManager: LocaleManager,
) : ViewModel3(dispatcherProvider) {

    val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.asLiveData2()

    val isUpdateCheckSupported = flow { emit(updateChecker.isCheckSupported()) }.asLiveData2()

    val currentLocales = localeManager.currentLocales.asLiveData2()

    @SuppressLint("NewApi")
    fun showLanguagePicker() = launch {
        log(TAG) { "showLanguagPicker()" }
        if (hasApiLevel(33)) {
            localeManager.showLanguagePicker()
        } else {
            throw IllegalStateException("This should not be clickable below API 33...")
        }
    }

    companion object {
        private val TAG = logTag("Settings", "General", "ViewModel")
    }
}
package eu.darken.sdmse.main.ui.settings.support

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SupportFragment : PreferenceFragment2() {

    private val vm: SupportFragmentVM by viewModels()

    override val preferenceFile: Int = R.xml.preferences_support
    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }

    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var webpageTool: WebpageTool

    private val installIdPref by lazy { findPreference<Preference>("support.installid")!! }
    private val debugLogPref by lazy { findPreference<Preference>("support.debuglog")!! }

    override fun onPreferencesCreated() {
        installIdPref.setOnPreferenceClickListener {
            vm.copyInstallID()
            true
        }
        debugLogPref.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.support_debuglog_label)
                setMessage(R.string.settings_debuglog_explanation)
                setPositiveButton(R.string.general_continue) { _, _ -> vm.startDebugLog() }
                setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                setNeutralButton(R.string.settings_privacy_policy_label) { _, _ -> webpageTool.open(SdmSeLinks.PRIVACY_POLICY) }
            }.show()
            true
        }
        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.clipboardEvent.observe2(this) { installId ->
            Snackbar.make(requireView(), installId, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.general_copy_action) {
                    clipboardHelper.copyToClipboard(installId)
                }
                .show()
        }

        vm.isRecording.observe2(this) {
            debugLogPref.isEnabled = !it
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
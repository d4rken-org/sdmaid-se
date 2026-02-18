package eu.darken.sdmse.main.ui.settings.support

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SupportFragment : PreferenceFragment2() {

    private val vm: SupportViewModel by viewModels()

    override val preferenceFile: Int = R.xml.preferences_support
    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }

    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var webpageTool: WebpageTool

    private val installIdPref by lazy { findPreference<Preference>("support.installid")!! }
    private val debugLogPref by lazy { findPreference<Preference>("support.debuglog")!! }
    private val debugLogFolderPref by lazy { findPreference<Preference>("support.debuglog.folder")!! }

    override fun onPreferencesCreated() {
        findPreference<Preference>("support.contact")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToSupportContactFormFragment().navigate()
            true
        }
        installIdPref.setOnPreferenceClickListener {
            vm.copyInstallID()
            true
        }
        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.clipboardEvent.observe2(this) { installId ->
            Snackbar.make(requireView(), installId, Snackbar.LENGTH_INDEFINITE)
                .setAction(eu.darken.sdmse.common.R.string.general_copy_action) {
                    clipboardHelper.copyToClipboard(installId)
                }
                .show()
        }

        vm.isRecording.observe2(this) { isRecording ->
            debugLogPref.setIcon(
                if (isRecording) R.drawable.ic_cancel
                else R.drawable.ic_bug_report
            )
            debugLogPref.setTitle(
                if (isRecording) R.string.debug_debuglog_stop_action
                else R.string.debug_debuglog_record_action
            )
            debugLogPref.setOnPreferenceClickListener {
                if (isRecording) {
                    vm.stopDebugLog()
                } else {
                    RecorderConsentDialog(requireContext(), webpageTool).showDialog {
                        vm.startDebugLog()
                    }
                }
                true
            }
            debugLogFolderPref.isEnabled = !isRecording
        }

        vm.debugLogFolderStats.observe2(this) { stats ->
            if (stats.fileCount == 0) {
                debugLogFolderPref.summary = getString(R.string.support_debuglog_folder_empty_desc)
            } else {
                val formattedSize = Formatter.formatShortFileSize(requireContext(), stats.totalSizeBytes)
                debugLogFolderPref.summary = resources.getQuantityString(
                    R.plurals.support_debuglog_folder_desc,
                    stats.fileCount,
                    stats.fileCount,
                    formattedSize
                )
            }
        }

        debugLogFolderPref.setOnPreferenceClickListener {
            val stats = vm.debugLogFolderStats.value ?: return@setOnPreferenceClickListener true
            if (stats.fileCount == 0) {
                Snackbar.make(requireView(), R.string.support_debuglog_folder_empty_desc, Snackbar.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.support_debuglog_folder_delete_confirmation_title)
                    setMessage(R.string.support_debuglog_folder_delete_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.deleteAllDebugLogs()
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()
            }
            true
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
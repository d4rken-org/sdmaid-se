package eu.darken.sdmse.main.ui.settings.support

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.core.GeneralSettings
import androidx.navigation.fragment.findNavController
import eu.darken.sdmse.common.navigation.safeNavigate
import eu.darken.sdmse.main.ui.navigation.DebugLogSessionsRoute
import eu.darken.sdmse.main.ui.navigation.SupportFormRoute
import javax.inject.Inject
import eu.darken.sdmse.common.ui.R as UiR



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

    override fun onResume() {
        super.onResume()
        vm.refreshSessions()
    }

    override fun onPreferencesCreated() {
        findPreference<Preference>("support.contact")!!.setOnPreferenceClickListener {
            findNavController().safeNavigate(SupportFormRoute)
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

        vm.events.observe2(this) { event ->
            when (event) {
                is SupportViewModel.SupportEvents.ShowShortRecordingWarning -> ShortRecordingDialog(
                    context = requireContext(),
                    onContinue = {},
                    onStopAnyway = { vm.confirmStopDebugLog() },
                ).show()

                is SupportViewModel.SupportEvents.LaunchRecorderActivity -> {
                    startActivity(event.intent)
                }
            }
        }

        vm.isRecording.observe2(this) { isRecording ->
            debugLogPref.setIcon(
                if (isRecording) UiR.drawable.ic_cancel
                else UiR.drawable.ic_bug_report
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
            if (stats.sessionCount == 0) {
                debugLogFolderPref.summary = getString(R.string.support_debuglog_folder_empty_desc)
            } else {
                val formattedSize = Formatter.formatShortFileSize(requireContext(), stats.totalSizeBytes)
                debugLogFolderPref.summary = resources.getQuantityString(
                    R.plurals.support_debuglog_folder_desc,
                    stats.sessionCount,
                    stats.sessionCount,
                    formattedSize
                )
            }
        }

        debugLogFolderPref.setOnPreferenceClickListener {
            findNavController().safeNavigate(DebugLogSessionsRoute)
            true
        }

        super.onViewCreated(view, savedInstanceState)
    }
}

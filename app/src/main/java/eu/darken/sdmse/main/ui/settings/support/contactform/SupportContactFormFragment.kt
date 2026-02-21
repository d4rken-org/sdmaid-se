package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SupportContactFormFragmentBinding
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.Companion.DESCRIPTION_MIN_WORDS
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.Companion.EXPECTED_MIN_WORDS
import javax.inject.Inject

@AndroidEntryPoint
class SupportContactFormFragment : Fragment3(R.layout.support_contact_form_fragment) {

    override val vm: SupportContactFormViewModel by viewModels()
    override val ui: SupportContactFormFragmentBinding by viewBinding()

    @Inject lateinit var webpageTool: WebpageTool
    @Inject lateinit var logSessionAdapter: LogSessionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
        }

        ViewCompat.setOnApplyWindowInsetsListener(ui.scrollView) { v, insets ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout: Insets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, displayCutout.bottom, ime.bottom)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomInset)
            insets
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.sendButton.setOnClickListener { vm.send() }

        ui.categoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = when (checkedIds.firstOrNull()) {
                R.id.category_bug -> SupportContactFormViewModel.Category.BUG
                R.id.category_feature -> SupportContactFormViewModel.Category.FEATURE
                R.id.category_question -> SupportContactFormViewModel.Category.QUESTION
                else -> return@setOnCheckedStateChangeListener
            }
            vm.updateCategory(category)
        }

        ui.toolGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val tool = when (checkedIds.firstOrNull()) {
                R.id.tool_general -> SupportContactFormViewModel.Tool.GENERAL
                R.id.tool_appcleaner -> SupportContactFormViewModel.Tool.APP_CLEANER
                R.id.tool_corpsefinder -> SupportContactFormViewModel.Tool.CORPSE_FINDER
                R.id.tool_systemcleaner -> SupportContactFormViewModel.Tool.SYSTEM_CLEANER
                R.id.tool_deduplicator -> SupportContactFormViewModel.Tool.DEDUPLICATOR
                R.id.tool_analyzer -> SupportContactFormViewModel.Tool.ANALYZER
                R.id.tool_appcontrol -> SupportContactFormViewModel.Tool.APP_CONTROL
                R.id.tool_scheduler -> SupportContactFormViewModel.Tool.SCHEDULER
                else -> return@setOnCheckedStateChangeListener
            }
            vm.updateTool(tool)
        }

        ui.descriptionInput.addTextChangedListener { text ->
            vm.updateDescription(text?.toString() ?: "")
        }

        ui.expectedInput.addTextChangedListener { text ->
            vm.updateExpectedBehavior(text?.toString() ?: "")
        }

        ui.triedUpdated.setOnCheckedChangeListener { _, isChecked -> vm.toggleTriedUpdated(isChecked) }
        ui.checkUpdatesLink.setOnClickListener { vm.openUpdateCheck() }
        ui.triedRestart.setOnCheckedChangeListener { _, isChecked -> vm.toggleTriedRestart(isChecked) }
        ui.triedClearCache.setOnCheckedChangeListener { _, isChecked -> vm.toggleTriedClearCache(isChecked) }
        ui.triedReboot.setOnCheckedChangeListener { _, isChecked -> vm.toggleTriedReboot(isChecked) }
        ui.triedPermissions.setOnCheckedChangeListener { _, isChecked -> vm.toggleTriedPermissions(isChecked) }
        ui.triedOtherInput.addTextChangedListener { text ->
            vm.updateTriedOther(text?.toString() ?: "")
        }

        ui.logSessionList.setupDefaults(logSessionAdapter)

        vm.logPickerState.observe2(ui) { pickerState ->
            val items = pickerState.sessions.map { session ->
                LogSessionAdapter.SessionVH.Item(
                    zipFile = session.zipFile,
                    size = session.size,
                    lastModified = session.lastModified,
                    isSelected = session.zipFile == pickerState.selectedZip,
                    onSelected = { vm.selectLogSession(session.zipFile) },
                    onDelete = { confirmDeleteLogSession(session.zipFile) },
                )
            }
            logSessionAdapter.update(items)
            logSessionEmpty.isVisible = pickerState.sessions.isEmpty() && !pickerState.isRecording
            debugLogButton.text = if (pickerState.isRecording) {
                getString(R.string.debug_debuglog_stop_action)
            } else {
                getString(R.string.debug_debuglog_record_action)
            }
            debugLogButton.setOnClickListener {
                if (pickerState.isRecording) {
                    vm.stopRecording()
                } else {
                    RecorderConsentDialog(requireContext(), webpageTool).showDialog {
                        vm.startRecording()
                    }
                }
            }
            updateCombinedUi()
        }

        vm.state.observe2(ui) { state ->
            triedCard.isVisible = state.isBug
            expectedCard.isVisible = state.isBug

            descriptionHint.text = when (state.category) {
                SupportContactFormViewModel.Category.BUG -> getString(R.string.support_contact_description_bug_hint)
                SupportContactFormViewModel.Category.FEATURE -> getString(R.string.support_contact_description_feature_hint)
                SupportContactFormViewModel.Category.QUESTION -> getString(R.string.support_contact_description_question_hint)
            }

            descriptionLayout.helperText = resources.getQuantityString(
                R.plurals.support_contact_word_count,
                state.descriptionWords,
                state.descriptionWords,
                DESCRIPTION_MIN_WORDS,
            )
            descriptionLayout.setWordCountColor(state.descriptionWords, DESCRIPTION_MIN_WORDS)

            expectedLayout.helperText = resources.getQuantityString(
                R.plurals.support_contact_word_count,
                state.expectedWords,
                state.expectedWords,
                EXPECTED_MIN_WORDS,
            )
            expectedLayout.setWordCountColor(state.expectedWords, EXPECTED_MIN_WORDS)
            updateCombinedUi()
        }

        vm.events.observe2 { event ->
            when (event) {
                is SupportContactFormEvents.OpenEmail -> try {
                    startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    Snackbar.make(requireView(), R.string.support_contact_no_email_app, Snackbar.LENGTH_LONG).show()
                }

                is SupportContactFormEvents.ShowError -> {
                    Snackbar.make(requireView(), event.messageRes, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshLogSessions()
    }

    private fun updateCombinedUi() {
        val state = vm.state.value
        val pickerState = vm.logPickerState.value
        ui.sendButton.isEnabled = state?.canSend == true
            && pickerState?.isRecording != true
        ui.debugLogCard.isVisible = state?.isBug == true
    }

    private fun confirmDeleteLogSession(zipFile: java.io.File) {
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
            setMessage(getString(eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x, zipFile.name))
            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                vm.deleteLogSession(zipFile)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

    private fun TextInputLayout.setWordCountColor(words: Int, minimum: Int) {
        if (words == 0) {
            setHelperTextColor(null)
            return
        }
        val attr = if (words >= minimum) {
            androidx.appcompat.R.attr.colorPrimary
        } else {
            androidx.appcompat.R.attr.colorError
        }
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        setHelperTextColor(ColorStateList.valueOf(color))
    }
}

package eu.darken.sdmse.swiper.ui.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.common.navigation.safeNavigate
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.FileTypeFilter
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.swiper.databinding.SwiperSessionsFragmentBinding
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsHeaderVH
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsSessionVH
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsUpgradeVH

@AndroidEntryPoint
class SwiperSessionsFragment : Fragment3(R.layout.swiper_sessions_fragment) {

    override val vm: SwiperSessionsViewModel by viewModels()
    override val ui: SwiperSessionsFragmentBinding by viewBinding()

    private val adapter by lazy { SwiperSessionsAdapter() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbar, top = true)
            insetsPadding(ui.fabContainer, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.list.setupDefaults(adapter, verticalDividers = false)
        ui.list.layoutManager = LinearLayoutManager(requireContext())

        ui.fab.setOnClickListener {
            openPicker()
        }

        parentFragmentManager.setFragmentResultListener(
            PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val pickerResult = PickerResult.fromBundle(result)
            val paths = pickerResult.selectedPaths.toSet()
            vm.setSelectedPaths(paths)
            if (paths.isNotEmpty()) {
                vm.createSession(paths)
            }
        }

        vm.state.observe2(ui) { state ->
            // Disable FAB if at session limit or scanning
            fab.isEnabled = state.canCreateNewSession

            // Build items list
            val items = mutableListOf<SwiperSessionsAdapter.Item>()

            val hasSessions = state.sessionsWithStats.isNotEmpty()

            // Update toolbar subtitle
            toolbar.subtitle = if (hasSessions) {
                getString(eu.darken.sdmse.swiper.R.string.swiper_sessions_current_sessions)
            } else {
                null
            }

            if (!hasSessions) {
                // Explanation card (only when there are no sessions)
                items.add(
                    SwiperSessionsHeaderVH.Item(
                        isPro = state.isPro,
                    )
                )
            }

            // Upgrade card for free version
            if (!state.isPro) {
                items.add(
                    SwiperSessionsUpgradeVH.Item(
                        freeVersionLimit = state.freeVersionLimit,
                        freeSessionLimit = state.freeSessionLimit,
                        onUpgrade = { safeNavigate(UpgradeRoute()) },
                    )
                )
            }

            // Session cards
            state.sessionsWithStats.forEachIndexed { index, sessionWithStats ->
                val position = index + 1
                val sessionId = sessionWithStats.session.sessionId
                val displayLabel = sessionWithStats.session.label
                    ?: getString(eu.darken.sdmse.swiper.R.string.swiper_session_default_label, position)
                items.add(
                    SwiperSessionsSessionVH.Item(
                        sessionWithStats = sessionWithStats,
                        position = position,
                        isScanning = state.isSessionScanning(sessionId),
                        isCancelling = state.isSessionCancelling(sessionId),
                        isRefreshing = state.isSessionRefreshing(sessionId),
                        onScan = { vm.scanSession(sessionId) },
                        onContinue = { vm.continueSession(sessionId) },
                        onRemove = { showDiscardConfirmation(sessionId) },
                        onCancel = { vm.cancelScan() },
                        onRename = {
                            showRenameDialog(
                                sessionId,
                                displayLabel,
                            )
                        },
                        onFilter = {
                            showFileTypeFilterDialog(
                                sessionId,
                                sessionWithStats.session.fileTypeFilter,
                            )
                        },
                        onSortOrder = {
                            showSortOrderDialog(
                                sessionId,
                                sessionWithStats.session.sortOrder,
                            )
                        },
                    )
                )
            }

            adapter.update(items)
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun openPicker() {
        safeNavigate(
            PickerRoute(
                request = PickerRequest(
                    requestKey = PICKER_REQUEST_KEY,
                    mode = PickerRequest.PickMode.DIRS,
                    allowedAreas = setOf(
                        DataArea.Type.PORTABLE,
                        DataArea.Type.SDCARD,
                        DataArea.Type.PUBLIC_DATA,
                        DataArea.Type.PUBLIC_MEDIA
                    ),
                )
            )
        )
    }

    private fun showDiscardConfirmation(sessionId: String) {
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(eu.darken.sdmse.swiper.R.string.swiper_discard_session_confirmation_title)
            setMessage(eu.darken.sdmse.swiper.R.string.swiper_discard_session_confirmation_message)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_remove_action) { _, _ ->
                vm.discardSession(sessionId)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

    private fun showRenameDialog(sessionId: String, currentLabel: String?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.swiper_session_rename_dialog, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.name_input)
        nameInput.setText(currentLabel)

        val dialog = MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(eu.darken.sdmse.swiper.R.string.swiper_session_rename_title)
            setView(dialogView)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                val newLabel = nameInput.text?.toString()?.takeIf { it.isNotBlank() }
                vm.renameSession(sessionId, newLabel)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()

        val positiveButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)

        fun updateSaveButton() {
            val newText = nameInput.text?.toString()?.trim()
            val isChanged = newText != (currentLabel ?: "")
            val isNotEmpty = !newText.isNullOrBlank()
            positiveButton.isEnabled = isChanged && isNotEmpty
        }

        updateSaveButton()

        nameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSaveButton()
            }
        })
    }

    private fun showFileTypeFilterDialog(sessionId: String, currentFilter: FileTypeFilter) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.swiper_file_type_filter_dialog, null)

        val checkImages = dialogView.findViewById<MaterialCheckBox>(R.id.category_images)
        val checkVideos = dialogView.findViewById<MaterialCheckBox>(R.id.category_videos)
        val checkAudio = dialogView.findViewById<MaterialCheckBox>(R.id.category_audio)
        val checkDocuments = dialogView.findViewById<MaterialCheckBox>(R.id.category_documents)
        val checkArchives = dialogView.findViewById<MaterialCheckBox>(R.id.category_archives)
        val customInput = dialogView.findViewById<TextInputEditText>(R.id.custom_extensions_input)

        // Subtitle taps toggle their checkbox
        dialogView.findViewById<View>(R.id.category_images_subtitle).setOnClickListener { checkImages.toggle() }
        dialogView.findViewById<View>(R.id.category_videos_subtitle).setOnClickListener { checkVideos.toggle() }
        dialogView.findViewById<View>(R.id.category_audio_subtitle).setOnClickListener { checkAudio.toggle() }
        dialogView.findViewById<View>(R.id.category_documents_subtitle).setOnClickListener { checkDocuments.toggle() }
        dialogView.findViewById<View>(R.id.category_archives_subtitle).setOnClickListener { checkArchives.toggle() }

        // Pre-populate from current filter
        checkImages.isChecked = FileTypeCategory.IMAGES in currentFilter.categories
        checkVideos.isChecked = FileTypeCategory.VIDEOS in currentFilter.categories
        checkAudio.isChecked = FileTypeCategory.AUDIO in currentFilter.categories
        checkDocuments.isChecked = FileTypeCategory.DOCUMENTS in currentFilter.categories
        checkArchives.isChecked = FileTypeCategory.ARCHIVES in currentFilter.categories
        if (currentFilter.customExtensions.isNotEmpty()) {
            customInput.setText(currentFilter.customExtensions.sorted().joinToString(", "))
        }

        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.swiper_file_type_filter_title)
            setView(dialogView)
            setPositiveButton(R.string.swiper_file_type_filter_apply_action) { _, _ ->
                val categories = mutableSetOf<FileTypeCategory>()
                if (checkImages.isChecked) categories.add(FileTypeCategory.IMAGES)
                if (checkVideos.isChecked) categories.add(FileTypeCategory.VIDEOS)
                if (checkAudio.isChecked) categories.add(FileTypeCategory.AUDIO)
                if (checkDocuments.isChecked) categories.add(FileTypeCategory.DOCUMENTS)
                if (checkArchives.isChecked) categories.add(FileTypeCategory.ARCHIVES)

                val customText = customInput.text?.toString().orEmpty()
                val customExtensions = FileTypeFilter.parseCustomExtensions(customText)

                val filter = FileTypeFilter(categories, customExtensions)
                vm.updateSessionFilter(sessionId, filter)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

    private fun showSortOrderDialog(sessionId: String, currentSortOrder: SortOrder) {
        val orders = SortOrder.entries.toTypedArray()
        val labels = orders.map { it.label.get(requireContext()) }.toTypedArray()
        val checkedIndex = orders.indexOf(currentSortOrder)

        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(eu.darken.sdmse.common.R.string.general_sort_by_title)
            setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                vm.updateSessionSortOrder(sessionId, orders[which])
                dialog.dismiss()
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

    companion object {
        private const val PICKER_REQUEST_KEY = "swiper_sessions_picker"
    }
}

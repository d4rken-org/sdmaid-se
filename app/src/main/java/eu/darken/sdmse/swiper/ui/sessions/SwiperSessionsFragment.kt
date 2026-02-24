package eu.darken.sdmse.swiper.ui.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SwiperSessionsFragmentBinding
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
                getString(R.string.swiper_sessions_current_sessions)
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
                        onUpgrade = { MainDirections.goToUpgradeFragment().navigate() },
                    )
                )
            }

            // Session cards
            state.sessionsWithStats.forEachIndexed { index, sessionWithStats ->
                val position = index + 1
                val sessionId = sessionWithStats.session.sessionId
                val displayLabel = sessionWithStats.session.label
                    ?: getString(R.string.swiper_session_default_label, position)
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
                    )
                )
            }

            adapter.update(items)
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun openPicker() {
        MainDirections.goToPicker(
            PickerRequest(
                requestKey = PICKER_REQUEST_KEY,
                mode = PickerRequest.PickMode.DIRS,
                allowedAreas = setOf(
                    DataArea.Type.PORTABLE,
                    DataArea.Type.SDCARD,
                    DataArea.Type.PUBLIC_DATA,
                    DataArea.Type.PUBLIC_MEDIA
                ),
            )
        ).navigate()
    }

    private fun showDiscardConfirmation(sessionId: String) {
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.swiper_discard_session_confirmation_title)
            setMessage(R.string.swiper_discard_session_confirmation_message)
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
            setTitle(R.string.swiper_session_rename_title)
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

    companion object {
        private const val PICKER_REQUEST_KEY = "swiper_sessions_picker"
    }
}

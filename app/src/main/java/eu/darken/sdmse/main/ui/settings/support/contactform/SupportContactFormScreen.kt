package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormCategoryCard
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormDebugLogCard
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormDescriptionCard
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormExpectedCard
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormFooterCard
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormFooterText
import eu.darken.sdmse.main.ui.settings.support.contactform.items.ContactFormToolCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SupportContactFormScreenHost(
    vm: SupportContactFormViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRecorderConsent by remember { mutableStateOf(false) }
    var showShortRecordingWarning by remember { mutableStateOf(false) }
    var showPostSendPrompt by remember { mutableStateOf(false) }
    var pendingDeleteSessionId by remember { mutableStateOf<SessionId?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshLogSessions()
        vm.checkPendingSend()
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SupportContactFormEvents.OpenEmail -> {
                    try {
                        context.startActivity(event.intent)
                        vm.markEmailLaunched()
                    } catch (_: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(context.getString(R.string.support_contact_no_email_app))
                    }
                }

                is SupportContactFormEvents.OpenUrl -> {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(event.url),
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }

                is SupportContactFormEvents.ShowError -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }

                SupportContactFormEvents.ShowShortRecordingWarning -> {
                    showShortRecordingWarning = true
                }

                SupportContactFormEvents.ShowPostSendPrompt -> {
                    showPostSendPrompt = true
                }
            }
        }
    }

    if (showRecorderConsent) {
        RecorderConsentDialog(
            onStartRecording = vm::startRecording,
            onOpenPrivacyPolicy = { vm.openUrl(SdmSeLinks.PRIVACY_POLICY) },
            onDismiss = { showRecorderConsent = false },
        )
    }

    if (showShortRecordingWarning) {
        ShortRecordingDialog(
            onContinue = {},
            onStopAnyway = vm::confirmStopRecording,
            onDismiss = { showShortRecordingWarning = false },
        )
    }

    if (showPostSendPrompt) {
        AlertDialog(
            onDismissRequest = { showPostSendPrompt = false },
            text = { Text(stringResource(R.string.support_contact_post_send_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPostSendPrompt = false
                    vm.navUp()
                }) {
                    Text(stringResource(CommonR.string.general_done_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostSendPrompt = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    pendingDeleteSessionId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSessionId = null },
            title = { Text(stringResource(CommonR.string.general_delete_confirmation_title)) },
            text = {
                Text(stringResource(CommonR.string.general_delete_confirmation_message_x, id.toString()))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLogSession(id)
                    pendingDeleteSessionId = null
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSessionId = null }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    SupportContactFormScreen(
        stateSource = vm.state,
        logPickerSource = vm.logPickerState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onCategoryChange = vm::updateCategory,
        onToolChange = vm::updateTool,
        onDescriptionChange = vm::updateDescription,
        onExpectedChange = vm::updateExpectedBehavior,
        onSelectSession = vm::selectLogSession,
        onDeleteSession = { pendingDeleteSessionId = it },
        onStartRecording = { showRecorderConsent = true },
        onStopRecording = vm::stopRecording,
        onSend = vm::send,
    )
}

@Composable
internal fun SupportContactFormScreen(
    stateSource: StateFlow<SupportContactFormViewModel.State> =
        MutableStateFlow(SupportContactFormViewModel.State()),
    logPickerSource: StateFlow<SupportContactFormViewModel.LogPickerState> =
        MutableStateFlow(SupportContactFormViewModel.LogPickerState()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onCategoryChange: (SupportContactFormViewModel.Category) -> Unit = {},
    onToolChange: (SupportContactFormViewModel.Tool) -> Unit = {},
    onDescriptionChange: (String) -> Unit = {},
    onExpectedChange: (String) -> Unit = {},
    onSelectSession: (SessionId) -> Unit = {},
    onDeleteSession: (SessionId) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val pickerState by logPickerSource.collectAsStateWithLifecycle()

    val canSend = state.canSend && !pickerState.isRecording && !pickerState.isZipping

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_contact_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                item {
                    ContactFormCategoryCard(
                        selected = state.category,
                        onChange = onCategoryChange,
                    )
                }
                if (state.isBug) {
                    item {
                        ContactFormDebugLogCard(
                            pickerState = pickerState,
                            onSelectSession = onSelectSession,
                            onDeleteSession = onDeleteSession,
                            onStartRecording = onStartRecording,
                            onStopRecording = onStopRecording,
                        )
                    }
                }
                item {
                    ContactFormToolCard(
                        selected = state.tool,
                        onChange = onToolChange,
                    )
                }
                item {
                    ContactFormDescriptionCard(
                        description = state.description,
                        wordCount = state.descriptionWords,
                        minWords = SupportContactFormViewModel.DESCRIPTION_MIN_WORDS,
                        category = state.category,
                        onDescriptionChange = onDescriptionChange,
                    )
                }
                if (state.isBug) {
                    item {
                        ContactFormExpectedCard(
                            expected = state.expectedBehavior,
                            wordCount = state.expectedWords,
                            minWords = SupportContactFormViewModel.EXPECTED_MIN_WORDS,
                            onExpectedChange = onExpectedChange,
                        )
                    }
                }
                item { ContactFormFooterCard() }
                item {
                    Button(
                        enabled = canSend,
                        onClick = onSend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Email,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.support_contact_send_action))
                    }
                }
                item { ContactFormFooterText() }
            }
        }
    }
}

@Preview2
@Composable
private fun SupportContactFormScreenQuestionPreview() {
    PreviewWrapper {
        SupportContactFormScreen()
    }
}

@Preview2
@Composable
private fun SupportContactFormScreenBugPreview() {
    PreviewWrapper {
        SupportContactFormScreen(
            stateSource = MutableStateFlow(
                SupportContactFormViewModel.State(
                    category = SupportContactFormViewModel.Category.BUG,
                    description = "Repro: open X, tap Y, crash.",
                    expectedBehavior = "Should not crash.",
                ),
            ),
        )
    }
}

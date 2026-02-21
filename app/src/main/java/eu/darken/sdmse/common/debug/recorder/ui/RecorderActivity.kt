package eu.darken.sdmse.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.URLSpan
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Activity2
import eu.darken.sdmse.databinding.DebugRecorderActivityBinding

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private lateinit var ui: DebugRecorderActivityBinding
    private val vm: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        vm.navEvents.observe2 {
            when (it) {
                null -> finish()
                else -> throw IllegalArgumentException("Unknown nav event: $it")
            }
        }
        vm.errorEvents.observe2 { it.asErrorDialogBuilder(this).show() }

        ui = DebugRecorderActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        EdgeToEdgeHelper(this).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.scrollView, top = true)
            insetsPadding(ui.actionBarInsetsWrapper, bottom = true)
        }

        val adapter = LogFileAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = true)

        // Setup scroll behavior for action bar
        ui.scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val deltaY = scrollY - oldScrollY
            if (deltaY > 10 && ui.actionBar.isVisible) {
                // Scrolling down - hide action bar
                ui.actionBar.animate()
                    .translationY(ui.actionBar.height.toFloat())
                    .setDuration(200)
                    .withEndAction { ui.actionBar.isVisible = false }
                    .start()
            } else if (deltaY < -10 && !ui.actionBar.isVisible) {
                // Scrolling up - show action bar
                ui.actionBar.isVisible = true
                ui.actionBar.animate()
                    .translationY(0f)
                    .setDuration(200)
                    .start()
            }
        }

        vm.state.observe2 { state ->
            ui.loadingIndicator.isGone = !state.isWorking
            ui.shareAction.isInvisible = state.isWorking
            ui.keepAction.isInvisible = state.isWorking

            ui.recordingPath.text = state.logDir?.let { "${it.path}/" } ?: "?"

            ui.logFilesHeader.isVisible = !state.isWorking && state.logEntries.isNotEmpty()
            ui.fileCountBadge.text = state.logEntries.size.toString()

            ui.listCaption.apply {
                val sizeText = state.compressedSize?.let {
                    Formatter.formatShortFileSize(this@RecorderActivity, it)
                } ?: "?"
                text = resources.getQuantityString(
                    R.plurals.debug_debuglog_screen_log_files_ready,
                    state.logEntries.size,
                    state.logEntries.size,
                    sizeText
                )
            }
            adapter.update(state.logEntries)
        }

        ui.shareAction.setOnClickListener { vm.share() }
        vm.shareEvent.observe2 { startActivity(it) }

        ui.privacyPolicyAction.apply {
            setOnClickListener { vm.goPrivacyPolicy() }
            val sp = SpannableString(text).apply {
                setSpan(URLSpan(""), 0, length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setText(sp, TextView.BufferType.SPANNABLE)
        }

        ui.keepAction.setOnClickListener { vm.keep() }
        ui.cancelAction.setOnClickListener { vm.discard() }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, path: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_PATH, path)
            return intent
        }
    }
}
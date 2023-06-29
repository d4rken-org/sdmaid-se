package eu.darken.sdmse.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.URLSpan
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isInvisible
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
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

        ui = DebugRecorderActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        vm.state.observe2 { state ->
            ui.loadingIndicator.isInvisible = !state.loading
            ui.shareAction.isInvisible = state.loading

            ui.recordingPath.text = state.normalPath?.path

            if (state.normalSize != -1L) {
                ui.recordingSize.text = Formatter.formatShortFileSize(this, state.normalSize)
            }
            if (state.compressedSize != -1L) {
                ui.recordingSizeCompressed.text = Formatter.formatShortFileSize(this, state.compressedSize)
            }
        }

        vm.errorEvents.observe2 {
            it.asErrorDialogBuilder(this).show()
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

        ui.cancelAction.setOnClickListener { finish() }
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

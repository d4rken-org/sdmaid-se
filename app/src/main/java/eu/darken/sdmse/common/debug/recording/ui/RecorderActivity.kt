package eu.darken.sdmse.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.core.view.isInvisible
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.uix.Activity2
import eu.darken.sdmse.databinding.DebugRecordingActivityBinding

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private lateinit var ui: DebugRecordingActivityBinding
    private val vm: RecorderActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = DebugRecordingActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        vm.state.observe2 { state ->
            ui.loadingIndicator.isInvisible = !state.loading
            ui.share.isInvisible = state.loading

            ui.recordingPath.text = state.normalPath

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

        ui.share.setOnClickListener { vm.share() }
        vm.shareEvent.observe2 { startActivity(it) }
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

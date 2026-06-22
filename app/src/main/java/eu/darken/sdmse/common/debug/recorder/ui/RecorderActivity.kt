package eu.darken.sdmse.common.debug.recorder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.theming.SdmSeTheme

@AndroidEntryPoint
class RecorderActivity : ComponentActivity() {

    private val vm: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(EXTRA_SESSION_ID) == null) {
            finish()
            return
        }

        enableEdgeToEdge()

        setContent {
            val themeState by vm.themeState.collectAsStateWithLifecycle()
            SdmSeTheme(state = themeState) {
                RecorderScreenHost(
                    vm = vm,
                    onLaunchShare = { intent ->
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            log(TAG, WARN) { "Share launch failed: $e" }
                            vm.onShareLaunchFailed(e)
                        } catch (e: SecurityException) {
                            log(TAG, WARN) { "Share launch denied: $e" }
                            vm.onShareLaunchFailed(e)
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val EXTRA_SESSION_ID = "sessionId"

        fun getLaunchIntent(context: Context, sessionId: SessionId): Intent =
            Intent(context, RecorderActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId.value)
            }
    }
}

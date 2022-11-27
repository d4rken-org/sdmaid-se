package eu.darken.sdmse.common.files.ui.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import eu.darken.sdmse.common.debug.logging.log

class PathPickerActivityContract : ActivityResultContract<PathPickerOptions, PathPickerResult?>() {

    override fun createIntent(context: Context, input: PathPickerOptions): Intent = Intent(
        context,
        PathPickerActivity::class.java
    ).apply {
        log { "createIntent(options=$input)" }
        val bundle = PathPickerActivityArgs(options = input).toBundle()
        putExtras(bundle)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PathPickerResult? {
        log { "parseResult(resultCode=$resultCode, intent=$intent)" }
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.getParcelableExtra(ARG_PICKER_RESULT)
    }

    companion object {
        internal const val ARG_PICKER_RESULT = "eu.darken.sdmse.common.files.ui.picker.PathPicker.Result"
    }
}
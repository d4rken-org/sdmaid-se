package eu.darken.sdmse.setup.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class SafGrantPrimaryContract : ActivityResultContract<SAFSetupModule.Result.PathAccess, Uri?>() {

    override fun createIntent(
        context: Context,
        input: SAFSetupModule.Result.PathAccess
    ): Intent = input.grantIntent

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = when (resultCode) {
        Activity.RESULT_OK -> intent?.data
        else -> null
    }
}
package eu.darken.sdmse.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Reusable
class EmailTool @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun build(email: Email, offerChooser: Boolean = false): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_EMAIL, email.receipients.toTypedArray())

        intent.addCategory(Intent.CATEGORY_DEFAULT)

        intent.putExtra(Intent.EXTRA_SUBJECT, email.subject)
        intent.putExtra(Intent.EXTRA_TEXT, email.body)

        email.attachment?.let { uri ->
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri("", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.type = "application/zip"
        } ?: run {
            intent.type = "message/rfc822"
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (offerChooser) Intent.createChooser(intent, null) else intent
    }

    data class Email(
        val receipients: List<String>,
        val subject: String,
        val body: String,
        val attachment: Uri? = null,
    )
}
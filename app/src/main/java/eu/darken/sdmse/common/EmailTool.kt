package eu.darken.sdmse.common

import android.content.Context
import android.content.Intent
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Reusable
class EmailTool @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun build(email: Email, offerChooser: Boolean = false): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "message/rfc822"
        intent.putExtra(Intent.EXTRA_EMAIL, email.receipients.toTypedArray())

        intent.addCategory(Intent.CATEGORY_DEFAULT)

        intent.putExtra(Intent.EXTRA_SUBJECT, email.subject)
        intent.putExtra(Intent.EXTRA_TEXT, email.body)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (offerChooser) Intent.createChooser(intent, null) else intent
    }

    data class Email(
        val receipients: List<String>,
        val subject: String,
        val body: String
    )
}
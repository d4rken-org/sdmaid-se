package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import okio.buffer
import okio.source

fun Uri.dropLastColon(): Uri = toString().removeSuffix(Uri.encode(":")).toUri()

@SuppressLint("Recycle")
fun Uri.read(context: Context) = context.contentResolver.openInputStream(this)?.source()

fun Uri.readAsText(context: Context) = read(context)?.buffer()?.readUtf8()
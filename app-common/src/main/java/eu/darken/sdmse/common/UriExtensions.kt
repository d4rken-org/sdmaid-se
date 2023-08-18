package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import okio.buffer
import okio.source

fun Uri.dropLastColon(): Uri = Uri.parse(toString().removeSuffix(Uri.encode(":")))

@SuppressLint("Recycle")
fun Uri.read(context: Context) = context.contentResolver.openInputStream(this)?.source()

fun Uri.readAsText(context: Context) = read(context)?.buffer()?.readUtf8()
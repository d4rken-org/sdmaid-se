package eu.darken.sdmse.appcontrol.core

import android.content.Context
import android.content.res.Resources
import eu.darken.sdmse.common.ca.CaString
import io.mockk.every
import io.mockk.mockk

/**
 * Resolves a [CaString] without Robolectric by faking the plural lookup that
 * `Context.getQuantityString2` performs, tagging each clause with its resource.
 */
internal fun CaString.resolve(): String {
    val res = mockk<Resources>().apply {
        every { getQuantityString(any(), any(), *anyVararg()) } answers {
            val quantity = secondArg<Int>()
            val name = when (firstArg<Int>()) {
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_toggle_result_message_x -> "toggled"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_toggle_result_skipped_x -> "skipped"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_force_stop_result_message_x -> "stopped"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_archive_result_x -> "archived"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_restore_result_x -> "restored"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_uninstall_result_message_x -> "uninstalled"
                eu.darken.sdmse.appcontrol.R.plurals.appcontrol_export_result_message_x -> "exported"
                eu.darken.sdmse.common.R.plurals.result_x_failed -> "failed"
                else -> "unknown"
            }
            "$quantity $name"
        }
    }
    return get(mockk<Context>().apply { every { resources } returns res })
}

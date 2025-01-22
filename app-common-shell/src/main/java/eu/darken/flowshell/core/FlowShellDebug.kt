package eu.darken.flowshell.core

import eu.darken.sdmse.common.BuildConfigWrap

object FlowShellDebug {
    var isDebug: Boolean = BuildConfigWrap.DEBUG

    fun logTag(vararg tags: String): String {
        val sb = StringBuilder("SDMSE:")
        for (i in tags.indices) {
            sb.append(tags[i])
            if (i < tags.size - 1) sb.append(":")
        }
        return sb.toString()
    }
}
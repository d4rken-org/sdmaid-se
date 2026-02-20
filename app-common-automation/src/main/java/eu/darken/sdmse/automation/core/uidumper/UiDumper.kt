package eu.darken.sdmse.automation.core.uidumper

import android.graphics.Rect
import android.util.Xml
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiDumper @Inject constructor(
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val shellOps: ShellOps,
) {

    suspend fun canDump(): Boolean {
        val adb = adbManager.canUseAdbNow()
        val root = rootManager.canUseRootNow()
        log(TAG, VERBOSE) { "canDump(): adb=$adb root=$root" }
        return adb || root
    }

    private suspend fun getShellMode(): ShellOps.Mode = when {
        adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
        rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
        else -> throw IllegalStateException("No ShellOps Mode available for UI dump")
    }

    suspend fun dump(): UiNode? {
        log(TAG) { "dump(): Starting UI dump" }

        val mode = getShellMode()

        // Dump directly to stdout - no temp file needed
        val result = shellOps.execute(
            ShellOpsCmd(listOf("uiautomator dump /dev/stdout")),
            mode,
        )
        log(TAG, VERBOSE) { "dump(): result: exitCode=${result.exitCode}, lines=${result.output.size}" }

        if (result.output.isEmpty()) {
            log(TAG, WARN) { "dump(): No output from uiautomator: ${result.errors}" }
            return null
        }

        val xml = result.output.joinToString("\n")
        log(TAG, VERBOSE) { "dump(): XML length: ${xml.length}" }

        return try {
            parseXml(xml)
        } catch (e: Exception) {
            log(TAG, WARN) { "dump(): XML parsing failed: ${e.message}" }
            null
        }
    }

    private data class PendingNode(
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val className: String?,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val children: MutableList<UiNode> = mutableListOf(),
    ) {
        fun toUiNode(): UiNode = UiNode(
            text = text,
            contentDesc = contentDesc,
            resourceId = resourceId,
            className = className,
            bounds = bounds,
            isClickable = isClickable,
            isEnabled = isEnabled,
            children = children.toList(),
        )
    }

    internal fun parseXml(xml: String): UiNode? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        val nodeStack = mutableListOf<PendingNode>()
        var rootChildren = mutableListOf<UiNode>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "hierarchy" -> {
                            rootChildren = mutableListOf()
                        }

                        "node" -> {
                            val pending = PendingNode(
                                text = parser.getAttributeValue(null, "text")?.takeIf { it.isNotEmpty() },
                                contentDesc = parser.getAttributeValue(null, "content-desc")?.takeIf { it.isNotEmpty() },
                                resourceId = parser.getAttributeValue(null, "resource-id")?.takeIf { it.isNotEmpty() },
                                className = parser.getAttributeValue(null, "class"),
                                bounds = parseBounds(parser.getAttributeValue(null, "bounds")),
                                isClickable = parser.getAttributeValue(null, "clickable") == "true",
                                isEnabled = parser.getAttributeValue(null, "enabled") == "true",
                            )
                            nodeStack.add(pending)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "node" -> {
                            if (nodeStack.isNotEmpty()) {
                                val completed = nodeStack.removeAt(nodeStack.lastIndex).toUiNode()
                                if (nodeStack.isNotEmpty()) {
                                    nodeStack.last().children.add(completed)
                                } else {
                                    rootChildren.add(completed)
                                }
                            }
                        }

                        "hierarchy" -> {
                            return rootChildren.firstOrNull()
                        }
                    }
                }
            }

            eventType = parser.next()
        }

        return rootChildren.firstOrNull()
    }

    internal fun parseBounds(boundsStr: String?): Rect {
        if (boundsStr == null) return Rect()

        // Format: "[left,top][right,bottom]"
        val regex = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".toRegex()
        val match = regex.find(boundsStr) ?: return Rect()

        val (left, top, right, bottom) = match.destructured
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    companion object {
        val TAG: String = logTag("Automation", "UiDumper")
    }
}

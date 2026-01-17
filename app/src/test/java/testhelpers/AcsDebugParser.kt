package testhelpers

/**
 * Parses ACS debug output from logs into [TestACSNodeInfo] trees for testing.
 *
 * Input format:
 * ```
 * ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=8b03c20, bounds=Rect(0, 0 - 1220, 2712)
 * ACS-DEBUG: -1: text='Storage', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=245e8c8, bounds=Rect(91, 1972 - 1129, 2086)
 * ACS-DEBUG: --2: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc123, bounds=Rect(100, 200 - 300, 250)
 * ```
 *
 * Usage:
 * ```kotlin
 * val root = AcsDebugParser.parseTree(logContent)
 * val context = createStepContextWithTree(root!!)
 * ```
 */
object AcsDebugParser {

    private val LINE_PATTERN = Regex(
        """ACS-DEBUG:\s*(-*)(\d+):\s*(.+)"""
    )

    private val TEXT_PATTERN = Regex("""text='([^']*)'""")
    private val CONTENT_DESC_PATTERN = Regex("""contentDesc='([^']*)'""")
    private val CLASS_PATTERN = Regex("""class=([^,]+)""")
    private val CLICKABLE_PATTERN = Regex("""clickable=(true|false)""")
    private val CHECKABLE_PATTERN = Regex("""checkable=(true|false)""")
    private val ENABLED_PATTERN = Regex("""enabled=(true|false)""")
    private val SCROLLABLE_PATTERN = Regex("""scrollable=(true|false)""")
    private val ID_PATTERN = Regex("""id=([^\s]+)\s+pkg=""")
    private val PKG_PATTERN = Regex("""pkg=([^,]+)""")

    data class ParsedNode(
        val level: Int,
        val text: String?,
        val contentDescription: String?,
        val className: String?,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isCheckable: Boolean,
        val isScrollable: Boolean,
        val viewIdResourceName: String?,
        val packageName: String?,
    )

    /**
     * Parses ACS debug log content and returns a [TestACSNodeInfo] tree.
     *
     * @param logContent Raw log content containing ACS-DEBUG lines
     * @return Root [TestACSNodeInfo] node, or null if parsing fails
     */
    fun parseTree(logContent: String): TestACSNodeInfo? {
        val parsedNodes = logContent.lines()
            .filter { it.contains("ACS-DEBUG:") && !it.contains("START") && !it.contains("STOP") }
            .mapNotNull { parseLine(it) }

        if (parsedNodes.isEmpty()) return null

        return buildTree(parsedNodes)
    }

    /**
     * Parses a single ACS-DEBUG line into a [ParsedNode].
     */
    fun parseLine(line: String): ParsedNode? {
        val lineMatch = LINE_PATTERN.find(line) ?: return null

        val dashes = lineMatch.groupValues[1]
        val level = dashes.length
        val properties = lineMatch.groupValues[3]

        val textMatch = TEXT_PATTERN.find(properties)
        val text = textMatch?.groupValues?.get(1)?.let { if (it == "null") null else it }

        val contentDescMatch = CONTENT_DESC_PATTERN.find(properties)
        val contentDescription = contentDescMatch?.groupValues?.get(1)?.let { if (it == "null") null else it }

        val className = CLASS_PATTERN.find(properties)?.groupValues?.get(1)?.trim()
        val isClickable = CLICKABLE_PATTERN.find(properties)?.groupValues?.get(1) == "true"
        val isCheckable = CHECKABLE_PATTERN.find(properties)?.groupValues?.get(1) == "true"
        val isEnabled = ENABLED_PATTERN.find(properties)?.groupValues?.get(1) == "true"
        val isScrollable = SCROLLABLE_PATTERN.find(properties)?.groupValues?.get(1) == "true"

        val idMatch = ID_PATTERN.find(properties)
        val viewIdResourceName = idMatch?.groupValues?.get(1)?.let { if (it == "null") null else it }

        val packageName = PKG_PATTERN.find(properties)?.groupValues?.get(1)?.trim()

        return ParsedNode(
            level = level,
            text = text,
            contentDescription = contentDescription,
            className = className,
            isClickable = isClickable,
            isEnabled = isEnabled,
            isCheckable = isCheckable,
            isScrollable = isScrollable,
            viewIdResourceName = viewIdResourceName,
            packageName = packageName,
        )
    }

    /**
     * Builds a [TestACSNodeInfo] tree from a flat list of [ParsedNode]s.
     */
    fun buildTree(nodes: List<ParsedNode>): TestACSNodeInfo? {
        if (nodes.isEmpty()) return null

        // Create TestACSNodeInfo for each parsed node
        val nodeInfos = nodes.map { it.toTestNodeInfo() }

        // Build parent-child relationships using a stack
        val stack = mutableListOf<Pair<Int, TestACSNodeInfo>>() // (level, node)

        for ((index, node) in nodes.withIndex()) {
            val nodeInfo = nodeInfos[index]
            val level = node.level

            // Pop nodes from stack until we find the parent (level - 1)
            while (stack.isNotEmpty() && stack.last().first >= level) {
                stack.removeAt(stack.lastIndex)
            }

            // If stack is not empty, add this node as child of the top node
            if (stack.isNotEmpty()) {
                stack.last().second.addChild(nodeInfo)
            }

            // Push current node to stack
            stack.add(level to nodeInfo)
        }

        // Return the first node (root)
        return nodeInfos.firstOrNull()
    }

    private fun ParsedNode.toTestNodeInfo() = TestACSNodeInfo(
        text = text,
        contentDescription = contentDescription,
        className = className,
        packageName = packageName,
        viewIdResourceName = viewIdResourceName,
        isClickable = isClickable,
        isEnabled = isEnabled,
        isCheckable = isCheckable,
        isScrollable = isScrollable,
    )
}

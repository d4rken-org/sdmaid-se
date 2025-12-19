package eu.darken.sdmse.automation.core.uidumper

fun UiNode.findByText(text: String): UiNode? =
    flatten().firstOrNull { it.text?.contains(text, ignoreCase = true) == true }

fun UiNode.findByExactText(text: String): UiNode? =
    flatten().firstOrNull { it.text.equals(text, ignoreCase = true) }

fun UiNode.findByContentDesc(desc: String): UiNode? =
    flatten().firstOrNull { it.contentDesc?.contains(desc, ignoreCase = true) == true }

fun UiNode.findByExactContentDesc(desc: String): UiNode? =
    flatten().firstOrNull { it.contentDesc.equals(desc, ignoreCase = true) }

fun UiNode.findByResourceId(id: String): UiNode? =
    flatten().firstOrNull { it.resourceId?.contains(id) == true }

fun UiNode.findByTexts(texts: Collection<String>): UiNode? =
    texts.firstNotNullOfOrNull { text -> findByExactText(text) }

fun UiNode.findByContentDescs(descs: Collection<String>): UiNode? =
    descs.firstNotNullOfOrNull { desc -> findByExactContentDesc(desc) }

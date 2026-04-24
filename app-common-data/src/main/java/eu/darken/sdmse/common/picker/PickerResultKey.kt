package eu.darken.sdmse.common.picker

import eu.darken.sdmse.common.navigation.ResultKey

/**
 * Typed key for [PickerResult] delivery via `NavigationController.setResult` /
 * `consumeResults`. Each [PickerRequest.requestKey] gets its own channel so multiple
 * picker launches from independent settings rows don't collide.
 */
data class PickerResultKey(val requestKey: String) : ResultKey<PickerResult> {
    override val name: String = "picker.result.$requestKey"
}

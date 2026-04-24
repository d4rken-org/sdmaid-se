package eu.darken.sdmse.common.navigation

/**
 * Typed key for cross-screen result delivery via [NavigationController.setResult] /
 * [NavigationController.resultFlow]. Declare one per result type, usually as a `data object`
 * next to the producer route:
 *
 * ```kotlin
 * data object PickerResultKey : ResultKey<PickerResult> {
 *     override val name: String = "picker.result"
 * }
 * ```
 *
 * The type parameter keeps producer/consumer call sites type-safe; the [name] is used
 * internally as the channel identifier.
 */
interface ResultKey<@Suppress("unused") T : Any> {
    val name: String
}

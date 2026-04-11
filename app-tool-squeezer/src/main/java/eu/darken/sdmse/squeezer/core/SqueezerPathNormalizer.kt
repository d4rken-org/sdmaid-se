package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.storage.PathMapper

/**
 * Normalizes user-picker output to [LocalPath]s the squeezer processors can actually act on.
 *
 * The picker can return [SAFPath] trees because it allows `PORTABLE` / `SDCARD` / `PUBLIC_DATA` /
 * `PUBLIC_MEDIA` data areas. Most SAF roots on the primary shared volume can be mapped back to
 * a [LocalPath] via [PathMapper.toLocalPath] so plain `java.io.File` access works. Roots that
 * can't be mapped (portable USB OTG, separate SD cards without MANAGE_EXTERNAL_STORAGE) are
 * surfaced in [Result.dropped] so the UI can tell the user why their scan returned nothing.
 *
 * Pure helper — takes a [PathMapper] as an argument rather than field-injecting so it can be
 * unit-tested without a Hilt graph or a ViewModel.
 */
object SqueezerPathNormalizer {

    data class Result(
        val accepted: Set<APath>,
        val dropped: List<APath>,
    )

    suspend fun normalize(
        input: Collection<APath>,
        pathMapper: PathMapper,
    ): Result {
        val accepted = mutableSetOf<APath>()
        val dropped = mutableListOf<APath>()
        for (path in input) {
            when (path) {
                is LocalPath -> accepted += path
                is SAFPath -> {
                    val mapped = pathMapper.toLocalPath(path)
                    if (mapped != null) accepted += mapped else dropped += path
                }
                else -> dropped += path
            }
        }
        return Result(accepted = accepted, dropped = dropped)
    }
}

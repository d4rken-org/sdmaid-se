package eu.darken.sdmse.analyzer.core.storage

import dagger.Reusable
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.OtherUsersCategory
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.storage.StorageStatsManager2
import eu.darken.sdmse.common.user.UserProfile2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

/**
 * Sizes the storage of users/profiles other than the current one.
 *
 * Deliberately does not reuse [AppStorageScanner], which is current-user-only: its root app-code
 * path would count shared APKs a second time for every user.
 *
 * Tiers, in order:
 * - ROOT: walk `/data/media/<id>` plus the user's private data areas. `/storage/emulated/<id>` is
 *   NOT usable here, FUSE filters it per calling user.
 * - STATS: `queryStatsForUser().dataBytes` as one opaque item. Exact app data, no shared files.
 * - NEITHER: the user is listed by name only, its bytes stay in the system residual.
 */
@Reusable
class OtherUserStorageScanner @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val statsManager: StorageStatsManager2,
) {

    suspend fun scan(
        storage: DeviceStorage,
        users: Collection<UserProfile2>,
        dataAreas: Set<DataArea>,
        useRoot: Boolean,
        onUser: (suspend (UserProfile2) -> Unit)? = null,
    ): OtherUsersCategory? {
        log(TAG) { "scan(): storage=${storage.id}, users=$users, useRoot=$useRoot" }
        if (users.isEmpty()) return null

        val groups = mutableListOf<ContentGroup>()
        val entries = mutableListOf<OtherUsersCategory.UserEntry>()

        users.sortedBy { it.handle.handleId }.forEach { user ->
            onUser?.invoke(user)
            val result = scanUser(storage, user, dataAreas, useRoot)
            groups.add(result.group)
            entries.add(result.entry)
        }

        return OtherUsersCategory(
            storageId = storage.id,
            groups = groups,
            users = entries,
        )
    }

    private data class UserResult(
        val group: ContentGroup,
        val entry: OtherUsersCategory.UserEntry,
    )

    private suspend fun scanUser(
        storage: DeviceStorage,
        user: UserProfile2,
        dataAreas: Set<DataArea>,
        useRoot: Boolean,
    ): UserResult {
        val label = user.getHumanLabel()
        val rootContents = if (useRoot) walkUser(user, dataAreas) else null

        val contents: Collection<ContentItem>
        val appDataKnown: Boolean
        val sharedMediaKnown: Boolean

        if (rootContents != null) {
            contents = rootContents
            appDataKnown = true
            sharedMediaKnown = true
        } else {
            // dataBytes already includes cacheBytes (see PkgOps.Stats), adding cache on top would
            // over-report every user by its entire cache.
            val dataBytes = try {
                statsManager.queryStatsForUser(storage.id, user.handle).dataBytes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Storage stats unavailable for ${user.handle}: ${e.asLog()}" }
                null
            }

            if (dataBytes != null) {
                log(TAG, INFO) { "Stats-only sizing for ${user.handle}: dataBytes=$dataBytes" }
                contents = setOf(
                    ContentItem.fromInaccessible(
                        LocalPath.build("data", "user", "${user.handle.handleId}"),
                        dataBytes,
                    )
                )
                appDataKnown = true
            } else {
                log(TAG, WARN) { "No size available for ${user.handle}, listing name only" }
                contents = emptySet()
                appDataKnown = false
            }
            // Never size another user's public storage below root: as shell `du` on
            // /storage/emulated/<id> reports a few KB for a real tree, without any error.
            sharedMediaKnown = false
        }

        val group = ContentGroup(label = label, contents = contents)

        return UserResult(
            group = group,
            entry = OtherUsersCategory.UserEntry(
                handle = user.handle,
                label = label,
                groupId = group.id,
                appDataKnown = appDataKnown,
                sharedMediaKnown = sharedMediaKnown,
                // Opaque stats items have nothing to browse into.
                isBrowsable = contents.any { !it.inaccessible },
            ),
        )
    }

    /**
     * Root sizing for one user, all-or-nothing.
     *
     * Returns null on any partial failure: [StorageStatsManager2.queryStatsForUser]'s dataBytes
     * already covers app-owned external data under `/data/media/<id>/Android`, so mixing a
     * successful media walk with stats for a failed private-data walk would double-count.
     */
    private suspend fun walkUser(user: UserProfile2, dataAreas: Set<DataArea>): Collection<ContentItem>? {
        val privateAreas = dataAreas
            .filter { it.type == DataArea.Type.PRIVATE_DATA }
            .filter { it.userHandle == user.handle }

        // PrivateDataModule silently omits locked credential-encrypted areas, so a missing half
        // means "we can't see this user's app data", not "this user has none".
        val hasCredentialEncrypted = privateAreas.any { it.isCredentialEncrypted }
        val hasDeviceEncrypted = privateAreas.any { it.isDeviceEncrypted }
        if (!hasCredentialEncrypted || !hasDeviceEncrypted) {
            log(TAG, WARN) {
                "Private data areas incomplete for ${user.handle} (ce=$hasCredentialEncrypted, de=$hasDeviceEncrypted)"
            }
            return null
        }

        val items = mutableListOf<ContentItem>()

        for (area in privateAreas) {
            val item = walkOrNull(area.path) ?: return null
            items.add(item)
        }

        // /data/media/<id>, not /storage/emulated/<id>: the latter is FUSE-filtered per calling user.
        val mediaPath = LocalPath.build("data", "media", "${user.handle.handleId}")
        val mediaItem = walkOrNull(mediaPath) ?: return null
        items.add(mediaItem)

        return items
    }

    /**
     * Walk that reports failure instead of degrading into an apparently-successful, tiny directory.
     *
     * `onError` returns false so a failure anywhere below the root aborts the walk instead of
     * finishing with a partial tree: the default (`true`, keep going) lets a listing error deep in
     * the tree pass as a complete result, which marks an under-counted user as fully known.
     */
    private suspend fun walkOrNull(path: APath): ContentItem? = try {
        val lookup = gatewaySwitch.lookup(path, type = GatewaySwitch.Type.AUTO)
        when {
            lookup.fileType != FileType.DIRECTORY -> ContentItem.fromLookup(lookup)
            else -> {
                val options = APathGateway.WalkOptions<APath, APathLookup<APath>>(
                    followSymlinks = false,
                    onError = { _, _ -> false },
                )
                val children = lookup.walk(gatewaySwitch, options)
                    .map { ContentItem.fromLookup(it) }
                    .take(WALK_MAX_ITEMS + 1)
                    .toList()

                if (children.size > WALK_MAX_ITEMS) {
                    // `du` is not atomic either, so a truncated walk counts as a failed walk: let
                    // the stats-only tier size this user instead of passing off a partial tree.
                    log(TAG, WARN) { "Walk item limit exceeded for $path" }
                    null
                } else {
                    children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to size $path: ${e.asLog()}" }
        null
    }

    companion object {
        private const val WALK_MAX_ITEMS = 100_000
        private val TAG = logTag("Analyzer", "Storage", "Scanner", "OtherUsers")
    }
}

/**
 * `/data_mirror/data_ce/null/<id>` (API 30+) or `/data/user/<id>` (legacy).
 */
private val DataArea.isCredentialEncrypted: Boolean
    get() = path.segments.contains("data_ce") || path.segments.contains("user")

/**
 * `/data_mirror/data_de/null/<id>` (API 30+) or `/data/user_de/<id>` (legacy).
 */
private val DataArea.isDeviceEncrypted: Boolean
    get() = path.segments.contains("data_de") || path.segments.contains("user_de")

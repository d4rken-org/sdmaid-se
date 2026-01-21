package eu.darken.sdmse.swiper.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.swiper.core.db.SwipeItemDao
import eu.darken.sdmse.swiper.core.db.SwipeSessionDao
import eu.darken.sdmse.swiper.core.db.SwipeSessionEntity
import eu.darken.sdmse.swiper.core.deleter.SwiperDeleter
import eu.darken.sdmse.swiper.core.scanner.SwiperScanner
import eu.darken.sdmse.swiper.core.tasks.SwiperDeleteTask
import eu.darken.sdmse.swiper.core.tasks.SwiperScanTask
import eu.darken.sdmse.swiper.core.tasks.SwiperTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Swiper @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val gatewaySwitch: GatewaySwitch,
    private val scanner: Provider<SwiperScanner>,
    private val deleter: Provider<SwiperDeleter>,
    private val sessionDao: SwipeSessionDao,
    private val itemDao: SwipeItemDao,
    private val upgradeRepo: UpgradeRepo,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.SWIPER

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val lastResult = MutableStateFlow<SwiperTask.Result?>(null)

    // In-memory cache: itemId -> lookup (persists across navigation within app session)
    private val lookupCache = mutableMapOf<Long, APathLookup<*>>()

    val activeSession: Flow<SwipeSession?> = sessionDao.getActiveSession()
        .map { it?.toModel() }
        .replayingShare(appScope)

    val activeSessions: Flow<List<SwipeSession>> = sessionDao.getAllActiveSessions()
        .map { entities -> entities.map { it.toModel() } }
        .replayingShare(appScope)

    fun getSession(sessionId: String): Flow<SwipeSession?> = sessionDao.getSessionFlow(sessionId)
        .map { it?.toModel() }

    data class SessionWithStats(
        val session: SwipeSession,
        val keepCount: Int,
        val deleteCount: Int,
        val undecidedCount: Int,
        val deletedCount: Int,
        val deleteFailedCount: Int,
    ) {
        val isScanned: Boolean get() = session.state != SessionState.CREATED
    }

    fun getSessionsWithStats(): Flow<List<SessionWithStats>> = activeSessions.flatMapLatest { sessions ->
        if (sessions.isEmpty()) {
            flowOf(emptyList())
        } else {
            val statsFlows = sessions.map { session ->
                itemDao.getDecisionStatsFlow(session.sessionId).map { stats -> session to stats }
            }
            combine(statsFlows) { sessionStatsPairs ->
                sessionStatsPairs.map { (session, stats) ->
                    SessionWithStats(
                        session = session,
                        keepCount = stats.find { it.decision == SwipeDecision.KEEP }?.count ?: 0,
                        deleteCount = stats.find { it.decision == SwipeDecision.DELETE }?.count ?: 0,
                        undecidedCount = stats.find { it.decision == SwipeDecision.UNDECIDED }?.count ?: 0,
                        deletedCount = stats.find { it.decision == SwipeDecision.DELETED }?.count ?: 0,
                        deleteFailedCount = stats.find { it.decision == SwipeDecision.DELETE_FAILED }?.count ?: 0,
                    )
                }
            }
        }
    }

    override val state: Flow<State> = combine(
        activeSession,
        lastResult,
        progress,
    ) { session, lastResult, progress ->
        State(
            session = session,
            lastResult = lastResult,
            progress = progress,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as SwiperTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(gatewaySwitch) {
                when (task) {
                    is SwiperScanTask -> performScan(task)
                    is SwiperDeleteTask -> performDelete(task)
                }
            }
            lastResult.value = result
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } catch (e: CancellationException) {
            throw e
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: SwiperScanTask): SwiperScanTask.Result {
        log(TAG) { "performScan(): $task" }

        val isPro = upgradeRepo.isPro()
        val itemLimit = if (isPro) null else SwiperSettings.FREE_VERSION_LIMIT

        // Determine paths and sessionId
        val (paths, sessionId) = if (task.sessionId != null) {
            // Scanning existing session
            val session = sessionDao.getSession(task.sessionId)
                ?: throw IllegalArgumentException("Session not found: ${task.sessionId}")
            session.sourcePaths.toSet() to task.sessionId
        } else {
            // Creating new session (backward compatibility)
            task.paths!! to null
        }

        val scanOptions = SwiperScanner.Options(
            paths = paths,
            itemLimit = itemLimit,
        )

        val scanResult = scanner.get().withProgress(this) {
            scan(scanOptions)
        }

        if (sessionId != null) {
            // Update existing session - delete old items first, then insert new
            // Clear cache for this session
            clearCacheForSession(sessionId)
            itemDao.deleteItemsForSession(sessionId)

            // Update session entity with new scan results
            val updatedSession = scanResult.session.copy(
                sessionId = sessionId,
                state = SessionState.READY,
            )
            sessionDao.update(updatedSession)

            // Insert items with correct sessionId
            val itemsWithCorrectSession = scanResult.items.map { it.copy(sessionId = sessionId) }
            itemDao.insertAll(itemsWithCorrectSession)

            // Populate cache with lookups - retrieve inserted items to get their auto-generated IDs
            val insertedItems = itemDao.getItemsForSessionSync(sessionId)
            insertedItems.forEachIndexed { index, entity ->
                lookupCache[entity.id] = scanResult.lookups[index]
            }
            log(TAG, INFO) { "performScan(): Populated cache with ${insertedItems.size} lookups" }

            log(TAG, INFO) { "performScan(): Updated session $sessionId with ${itemsWithCorrectSession.size} items" }

            return SwiperScanTask.Success(
                sessionId = sessionId,
                itemCount = itemsWithCorrectSession.size,
            )
        } else {
            // Save to database - session is created with READY state by the scanner
            sessionDao.insert(scanResult.session)
            itemDao.insertAll(scanResult.items)

            // Populate cache with lookups - retrieve inserted items to get their auto-generated IDs
            val insertedItems = itemDao.getItemsForSessionSync(scanResult.session.sessionId)
            insertedItems.forEachIndexed { index, entity ->
                lookupCache[entity.id] = scanResult.lookups[index]
            }
            log(TAG, INFO) { "performScan(): Populated cache with ${insertedItems.size} lookups" }

            log(TAG, INFO) { "performScan(): ${scanResult.items.size} items found" }

            return SwiperScanTask.Success(
                sessionId = scanResult.session.sessionId,
                itemCount = scanResult.items.size,
            )
        }
    }

    private suspend fun performDelete(task: SwiperDeleteTask): SwiperDeleteTask.Result {
        log(TAG) { "performDelete(): $task" }

        // Get items with fresh lookups from cache
        val itemsToDelete = getItemsForSessionSync(task.sessionId)
            .filter { it.decision == SwipeDecision.DELETE }

        var deletedSize = 0L
        var deletedPaths = emptySet<APath>()

        if (itemsToDelete.isNotEmpty()) {
            val result = deleter.get().withProgress(this) {
                delete(itemsToDelete, itemDao)
            }
            deletedSize = result.deletedSize
            deletedPaths = result.deletedPaths
        }

        // Remove decided items (DELETED and KEEP) from session
        itemDao.deleteByDecisions(task.sessionId, listOf(SwipeDecision.DELETED, SwipeDecision.KEEP))

        // Reset currentIndex to 0 - remaining items are all undecided
        sessionDao.updateCurrentIndex(task.sessionId, 0, Instant.now().toEpochMilli())

        // Check remaining items
        val failedCount = itemDao.countByDecision(task.sessionId, SwipeDecision.DELETE_FAILED)
        val undecidedCount = itemDao.countByDecision(task.sessionId, SwipeDecision.UNDECIDED)
        val remainingCount = failedCount + undecidedCount

        return if (remainingCount == 0) {
            // Session complete - cleanup
            sessionDao.updateState(task.sessionId, SessionState.COMPLETED.name, Instant.now().toEpochMilli())
            sessionDao.delete(task.sessionId)
            clearCacheForSession(task.sessionId)
            log(TAG, INFO) { "Session ${task.sessionId} completed and cleaned up" }
            SwiperDeleteTask.Success(affectedSpace = deletedSize, affectedPaths = deletedPaths)
        } else if (failedCount > 0) {
            // Failed items exist - need retry
            log(TAG, INFO) { "Session ${task.sessionId} has $failedCount failed, $undecidedCount undecided" }
            SwiperDeleteTask.PartialSuccess(
                affectedSpace = deletedSize,
                affectedPaths = deletedPaths,
                failedCount = failedCount,
            )
        } else {
            // Only undecided remain - session continues
            log(TAG, INFO) { "Session ${task.sessionId} has $undecidedCount undecided items remaining" }
            SwiperDeleteTask.Success(affectedSpace = deletedSize, affectedPaths = deletedPaths)
        }
    }

    suspend fun refreshSessionLookups(sessionId: String) = toolLock.withLock {
        log(TAG) { "refreshSessionLookups(sessionId=$sessionId)" }
        updateProgress { Progress.Data() }

        try {
            keepResourceHoldersAlive(gatewaySwitch) {
                updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

                val entities = itemDao.getItemsForSessionSync(sessionId)
                log(TAG) { "Refreshing lookups for ${entities.size} items" }

                entities.forEachIndexed { index, entity ->
                    updateProgressCount(Progress.Count.Percent(index, entities.size))
                    updateProgressSecondary(entity.path.userReadablePath)

                    try {
                        val lookup = gatewaySwitch.lookup(entity.path)
                        lookupCache[entity.id] = lookup
                    } catch (e: Exception) {
                        // File no longer exists - remove from DB
                        log(TAG) { "File no longer exists, removing: ${entity.path}" }
                        itemDao.deleteItem(entity.id)
                        lookupCache.remove(entity.id)
                    }
                }

                log(TAG, INFO) { "refreshSessionLookups(): Done, cache has ${lookupCache.size} entries" }
            }
        } finally {
            updateProgress { null }
        }
    }

    suspend fun hasSessionLookups(sessionId: String): Boolean {
        val entities = itemDao.getItemsForSessionSync(sessionId)
        if (entities.isEmpty()) return true // Empty session, nothing to cache
        return entities.any { lookupCache.containsKey(it.id) }
    }

    suspend fun updateDecision(itemId: Long, decision: SwipeDecision) = toolLock.withLock {
        log(TAG) { "updateDecision(itemId=$itemId, decision=$decision)" }
        itemDao.updateDecision(itemId, decision)
    }

    suspend fun updateCurrentIndex(sessionId: String, index: Int) = toolLock.withLock {
        log(TAG) { "updateCurrentIndex(sessionId=$sessionId, index=$index)" }
        sessionDao.updateCurrentIndex(sessionId, index, Instant.now().toEpochMilli())
    }

    suspend fun discardSession(sessionId: String) = toolLock.withLock {
        log(TAG) { "discardSession(sessionId=$sessionId)" }
        clearCacheForSession(sessionId)
        sessionDao.delete(sessionId)
    }

    suspend fun updateSessionLabel(sessionId: String, label: String?) = toolLock.withLock {
        log(TAG) { "updateSessionLabel(sessionId=$sessionId, label=$label)" }
        sessionDao.updateLabel(sessionId, label)
    }

    suspend fun retryFailedItem(itemId: Long) = toolLock.withLock {
        log(TAG) { "retryFailedItem(itemId=$itemId)" }
        itemDao.updateDecision(itemId, SwipeDecision.DELETE)
    }

    suspend fun retryAllFailed(sessionId: String) = toolLock.withLock {
        log(TAG) { "retryAllFailed(sessionId=$sessionId)" }
        val failedItems = itemDao.getItemsByDecisionSync(sessionId, SwipeDecision.DELETE_FAILED)
        failedItems.forEach { item ->
            itemDao.updateDecision(item.id, SwipeDecision.DELETE)
        }
    }

    suspend fun removeItem(itemId: Long) = toolLock.withLock {
        log(TAG) { "removeItem(itemId=$itemId)" }
        lookupCache.remove(itemId)
        itemDao.deleteItem(itemId)
    }

    suspend fun createSession(paths: Set<APath>): String = toolLock.withLock {
        log(TAG, INFO) { "createSession(paths=$paths)" }
        val sessionId = java.util.UUID.randomUUID().toString()
        val now = Instant.now()
        val session = SwipeSessionEntity(
            sessionId = sessionId,
            sourcePaths = paths.toList(),
            currentIndex = 0,
            totalItems = 0,
            createdAt = now,
            lastModifiedAt = now,
            state = SessionState.CREATED,
        )
        sessionDao.insert(session)
        log(TAG, INFO) { "Created session: $sessionId" }
        sessionId
    }

    fun getItemsForSession(sessionId: String): Flow<List<SwipeItem>> {
        return itemDao.getItemsForSession(sessionId).map { entities ->
            entities.mapNotNull { entity ->
                val lookup = lookupCache[entity.id] ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                SwipeItem(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    itemIndex = entity.itemIndex,
                    lookup = lookup as APathLookup<APath>,
                    decision = entity.decision,
                )
            }
        }
    }

    private suspend fun getItemsForSessionSync(sessionId: String): List<SwipeItem> {
        val entities = itemDao.getItemsForSessionSync(sessionId)
        return entities.mapNotNull { entity ->
            val lookup = lookupCache[entity.id] ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            SwipeItem(
                id = entity.id,
                sessionId = entity.sessionId,
                itemIndex = entity.itemIndex,
                lookup = lookup as APathLookup<APath>,
                decision = entity.decision,
            )
        }
    }

    private fun clearCacheForSession(sessionId: String) {
        // Remove all cache entries for items in this session
        // Note: This is a simple implementation - for better performance,
        // we could maintain a sessionId -> itemIds mapping
        val keysToRemove = lookupCache.keys.toList()
        keysToRemove.forEach { lookupCache.remove(it) }
        log(TAG) { "Cleared lookup cache for session $sessionId" }
    }

    data class State(
        val session: SwipeSession?,
        val progress: Progress.Data?,
        val lastResult: SwiperTask.Result? = null,
    ) : SDMTool.State

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Swiper): SDMTool
    }

    companion object {
        internal val TAG = logTag("Swiper")
    }
}

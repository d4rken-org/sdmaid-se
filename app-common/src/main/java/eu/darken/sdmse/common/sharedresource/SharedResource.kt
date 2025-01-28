package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.traceCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * A utility class to create child/parent dependencies for expensive resources.
 * Allows keeping reusable resources alive until it is no longer needed by anyone.
 */
@Suppress("ProtectedInFinal")
open class SharedResource<T : Any>(
    tag: String,
    parentScope: CoroutineScope,
    private val source: Flow<T>,
) : KeepAlive {
    private val iTag = "$tag:SR"
    override val resourceId: String = iTag

    private val leaseScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val lock = Mutex()

    private val leases = mutableSetOf<Lease>()
    private val children = mutableMapOf<SharedResource<*>, KeepAlive>()

    override val isClosed: Boolean
        get() = sourceJob == null

    private var sId: String? = null

    private var sourceJob: Job? = null
    private var sourceValue: T? = null

    suspend fun get(): Resource<T> = lock.withLock {
        val lId = "L:${UUID.randomUUID().toString().takeLast(4)}"

        if (Bugs.isTrace) {
            if (isClosed) {
                log(iTag, DEBUG) { "Lease($lId).get($sId): Getting new sourceValue" }
            } else {
                log(iTag, VERBOSE) { "Lease($lId).get($sId): Getting current sourceValue ($sId)" }
            }
        }

        if (sourceJob == null) {
            sId = "S:${UUID.randomUUID().toString().takeLast(4)}"

            log(iTag, DEBUG) { "Core($sId): Launching source job..." }
            var sourceError: Throwable? = null
            sourceJob = source
                .onEach {
                    sourceValue = it.also { log(iTag) { "Core($sId): sourceValue=$it" } }
                }
                .catch { error ->
                    log(iTag, WARN) { "Core($sId): Source ERROR, closing SharedResource: ${error.asLog()}" }
                    sourceError = error
                }
                .onCompletion { error ->
                    log(iTag, DEBUG) { "Core($sId): Source completed, closing SharedResource..." }
                    closeInternal()
                }
                .launchIn(leaseScope)

            if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).get($sId): Waiting for resource" }
            while (sourceValue == null && currentCoroutineContext().isActive) {
                sourceError?.let { throw it }
                delay(1)
            }
        }

        val lease = withContext(NonCancellable) {
            Lease(
                id = lId,
                job = leaseScope.launch {
                    if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).get($sId): Lease is active" }
                    awaitCancellation()
                },
                traceTag = if (Bugs.isDive) traceCall() else null
            ).also { leases.add(it) }
        }

        if (Bugs.isTrace) {
            val leaseSize = leases.size
            log(iTag, VERBOSE) { "Lease($lId).get($sId): Now holding $leaseSize lease(s)" }

            if (Bugs.isDive) {
                try {
                    leases.toList().forEachIndexed { i, l ->
                        log(iTag, VERBOSE) { "Lease($lId).get($sId): Now holding #$i - $l" }
                    }
                } catch (e: Exception) {
                    log(iTag, VERBOSE) { "Lease($lId).get($sId): Lease logging concurrency error" }
                }
            }
        }

        Resource(sourceValue!!, lease).also {
            if (Bugs.isTrace) log(iTag) { "Lease($lId).get($sId) --> ${it.item}" }
        }
    }

    private suspend fun closeLeaseInternal(lease: Lease): Unit = withContext(NonCancellable) {
        val lId = lease.id
        if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).close(): Closing..." }

        leases.remove(lease).also {
            if (Bugs.isTrace) {
                val leaseSize = leases.size
                if (it) log(iTag, VERBOSE) { "Lease($lId).close(): Removed! (now $leaseSize)" }
                else log(iTag, WARN) { "Lease($lId).close(): Already removed? (now $leaseSize) " }
            }
        }

        if (Bugs.isDive) log(iTag) { "Lease($lId).close(): Canceling lease job: ${lease.job}" }
        lease.job.cancelAndJoin()
        if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).close(): Lease job is completed" }

        if (leases.isEmpty()) {
            log(iTag, DEBUG) { "Lease($lId).close(): ZERO leases left for $sId, canceling source and waiting" }
            sourceJob!!.cancelAndJoin()

            children.apply {
                if (isEmpty()) return@apply
                onEachIndexed { index, entry ->
                    if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($sId): Closing child #$index $entry" }
                    entry.value.close()
                }
                clear()

                if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($sId): Remaining children have been cleared" }
            }

            leases.apply {
                if (isEmpty()) return@apply
                forEachIndexed { index, lease ->
                    if (Bugs.isTrace) log(iTag, VERBOSE) { "Core($sId): Canceling lease #$index: $lease..." }
                    lease.job.cancelAndJoin()
                }
                clear()
                if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($sId): Remaining leases have been cleared" }
            }
        } else {
            if (Bugs.isDive) {
                try {
                    leases.toList().forEachIndexed { index, lease ->
                        log(iTag, VERBOSE) { "Lease($lId).close(): Remaining lease #$index - $lease" }
                    }
                } catch (e: Exception) {
                    log(iTag, VERBOSE) { "Lease($lId).close(): Lease logging concurrency error" }
                }
            }
        }
    }

    override fun close() {
        if (isClosed) {
            if (Bugs.isTrace) log(iTag) { "Core($sId).close() already closed" }
            return
        } else {
            if (Bugs.isTrace) {
                log(iTag) { "Core($sId).close()ing via ${Exception().asLog()}" }
            } else {
                log(iTag) { "Core($sId).close()ing..." }
            }
        }

        runBlocking {
            lock.withLock { closeInternal() }
        }
    }

    private suspend fun closeInternal() = withContext(NonCancellable) {
        while (leases.isNotEmpty()) {
            log(iTag) { "Core($sId).close(): Current leases: ${leases.map { it.resourceId }}" }
            closeLeaseInternal(leases.first())
        }
        sourceValue = null
        sourceJob = null
        log(iTag, DEBUG) { "Core($sId): Shared resource flow completed." }
    }

    /**
     * A child resource will be kept alive by this resource, and will be closed once this resource is closed.
     *
     * The backup module is a child resource of the root shell
     * When the root shell closes, the backup module needs to "close" too.
     * But the backupmodule, while open, keeps the root shell alive.
     */
    suspend fun addChild(child: SharedResource<*>) = lock.withLock {
        if (children.contains(child)) {
            if (Bugs.isDive) {
                log(iTag, VERBOSE) { "Core($sId).addChild(): Already keeping child alive: $child" }
            }
            return@withLock
        }

        if (isClosed) {
            log(iTag, VERBOSE) { "Core($sId).addChild(): Can't add child, we are not alive: $child" }
            child.close()
            return@withLock
        }

        val keepAlive = child.get()
        val wrapped = Child(child, keepAlive)
        children[child] = wrapped

        if (Bugs.isDive) {
            val childrenSize = children.size
            log(iTag, VERBOSE) { "Core($sId).addChild(): Resource now has $childrenSize children: $child" }
        }
    }

    override fun toString(): String =
        "SharedResource(tag=$iTag, leases=${leases.size}, children=${children.size})"

    inner class Lease(
        internal val id: String,
        internal val job: Job,
        private val traceTag: String? = null
    ) : KeepAlive {
        override val resourceId: String = this@SharedResource.resourceId

        override val isClosed: Boolean
            get() = !job.isActive

        override fun close() = runBlocking {
            lock.withLock {
                closeLeaseInternal(this@Lease)
            }
        }

        override fun toString(): String = "Lease(id=$id, job=$job)" + (traceTag?.let { "\nCreated at $it" } ?: "")
    }

    private inner class Child(
        private val child: SharedResource<*>,
        private val ourKeepAlive: KeepAlive
    ) : KeepAlive {
        override val resourceId: String = child.resourceId

        private var closed: Boolean = false

        override val isClosed: Boolean
            get() = closed || ourKeepAlive.isClosed

        override fun close() {
            closed = true
            ourKeepAlive.close()
        }

        override fun toString(): String = "ChildResource(isClosed=$isClosed, child=$child)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SharedResource<*>.Child) return false
            if (resourceId != other.resourceId) return false
            return true
        }

        override fun hashCode(): Int = resourceId.hashCode()
    }

    companion object {
        fun createKeepAlive(tag: String, scope: CoroutineScope): SharedResource<Any> = SharedResource(
            tag,
            scope,
            callbackFlow {
                send("$tag{${Any().hashCode()}}")
                awaitClose()
            }
        )
    }

}

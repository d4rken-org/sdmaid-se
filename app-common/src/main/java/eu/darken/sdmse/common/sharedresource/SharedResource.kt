package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.traceCall
import eu.darken.sdmse.common.error.tryUnwrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * A utility class to create child/parent dependencies for expensive resources.
 * Allows keeping reusable resources alive until it is no longer needed by anyone.
 */
@Suppress("ProtectedInFinal")
open class SharedResource<T : Any>(
    tag: String,
    parentScope: CoroutineScope,
    source: Flow<T>,
) : KeepAlive {
    private val iTag = "$tag:SR"
    override val resourceId: String = iTag

    private val leaseScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val lock = Mutex()

    private val leases = mutableSetOf<Lease>()
    private val children = mutableMapOf<SharedResource<*>, KeepAlive>()

    override val isClosed: Boolean
        get() = !isAlive

    private var isAlive = false
    private var isClosing = false
    private var rId: String? = null

    private val resourceHolder: Flow<Event<T>> = source
        .onStart {
            lock.withLock {
                isAlive = true
                rId = "R:${UUID.randomUUID().toString().takeLast(4)}"
                log(iTag, DEBUG) { "Core($rId): Creating shared resource..." }

                if (children.isNotEmpty()) {
                    val _children = children.values.toList()
                    log(iTag, WARN) { "Core($rId): Non-empty child references: $_children" }
                    children.clear()
                }
            }
        }
        .map {
            @Suppress("USELESS_CAST")
            Event.Resource(rId!!, it) as Event<T>
        }
        .onEach { log(iTag) { "Core($rId): Resource ready: $it" } }
        .catch { log(iTag, WARN) { "Core($rId): Failed to provide resource: ${it.asLog()}" } }
        .onCompletion {
            lock.withLock {
                isAlive = false
                if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($rId): Releasing shared resource..." }

                children.values.forEach {
                    if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($rId): Closing child resource: $it" }
                    it.close()
                }
                children.clear()

                if (leases.isNotEmpty()) {
                    if (Bugs.isDebug) {
                        val remainingLeases = leases.joinToString()
                        log(iTag, VERBOSE) { "Core($rId): Cleaning up remaining leases: $remainingLeases" }
                    }

                    leases
                        .filter { it.job.isActive }
                        .forEachIndexed { index, lease ->
                            if (Bugs.isTrace) log(iTag, VERBOSE) { "Core($rId): Canceling #$index: $lease..." }
                            lease.job.cancelAndJoin()
                        }

                    if (Bugs.isDebug) log(iTag, VERBOSE) { "Core($rId): Remaining leases have been cleaned up." }

                    leases.clear()
                }

                val orphanedLeases = leaseScope.coroutineContext.job.children.filter { it.isActive }.toList()
                if (orphanedLeases.isNotEmpty()) {
                    log(iTag, WARN) { "Core($rId): Orphaned leases: $orphanedLeases" }
                    // This cancels all active leases, at the latest.
                    leaseScope.coroutineContext.cancelChildren()
                }
                log(iTag, DEBUG) { "Core($rId): Shared resource flow completed." }
            }
            parentScope.launch {
                // Needs to set isClosed AFTER the resourceHolder is REALLY finished
                // TODO this sucks but I really have not found a better way
                delay(50)
                if (Bugs.isDive) log(iTag, VERBOSE) { "Core($rId): Waiting for lock to set isClosing=false" }
                lock.withLock {
                    if (Bugs.isDive) log(iTag, VERBOSE) { "Core($rId): Setting isClosing=false" }
                    isClosing = false
                }
            }
        }
        .shareIn(
            parentScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            replay = 1,
        )

    suspend fun get(): Resource<T> {
        val lId = "L:${UUID.randomUUID().toString().takeLast(4)}"

        if (isClosing) {
            if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).get($rId): Resource is closing waiting..." }
            while (isClosing && currentCoroutineContext().isActive) {
                delay(1)
            }
        }

        val lease = lock.withLock {
            if (Bugs.isTrace) {
                if (isAlive) {
                    log(iTag, VERBOSE) { "Lease($lId).get($rId): Getting existing resource ($rId)" }
                } else {
                    log(iTag, DEBUG) { "Lease($lId).get($rId): Reviving SharedResource" }
                    log(iTag, VERBOSE) { "Lease($lId).get($rId): Revive call origin: ${traceCall()}" }
                }
            }

            val job = resourceHolder.launchIn(leaseScope).apply {
                invokeOnCompletion {
                    if (Bugs.isDive) {
                        val count = leases.size
                        log(iTag, VERBOSE) { "Lease($lId).get($rId): Lease on $rId completed (now=$count, $job)." }
                    }
                }
            }

            Lease(
                id = lId,
                job = job,
                traceTag = if (Bugs.isDive) traceCall() else null
            ).also {
                leases.add(it)

                if (Bugs.isDive) {
                    log(iTag, VERBOSE) { "Lease($lId).get($rId): Added new lease ($job)" }
                    val leaseSize = leases.size
                    log(iTag, VERBOSE) { "Lease($lId).get($rId): Now holding $leaseSize lease(s)" }
                }
            }
        }

        val resourceEvent = try {
            if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($lId).get($rId): Retrieving resource" }
            when (val event = resourceHolder.first()) {
                is Event.Error -> throw event.error
                is Event.Resource -> event
            }
        } catch (e: Exception) {
            log(iTag, WARN) { "Lease($lId).get($rId): Failed to retrieve resource (${e.asLog()}" }
            lease.close()
            throw e.tryUnwrap()
        }
        if (Bugs.isTrace) log(iTag) { "Lease($lId).get($rId): Resource retrieved: $resourceEvent" }
        return Resource(resourceEvent.resource, lease)
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
                log(iTag, VERBOSE) { "Core($rId).addChild(): Already keeping child alive: $child" }
            }
            return@withLock
        }

        if (!isAlive) {
            log(iTag, VERBOSE) { "Core($rId).addChild(): Can't add child, we are not alive: $child" }
            child.close()
            return@withLock
        }

        val keepAlive = child.get()
        val wrapped = Child(child, keepAlive)
        children[child] = wrapped

        if (Bugs.isDive) {
            val childrenSize = children.size
            log(iTag, VERBOSE) { "Core($rId).addChild(): Resource now has $childrenSize children: $child" }
        }
    }

    override fun close() {
        if (!isAlive) return
        if (Bugs.isTrace) {
            log(iTag) { "Core($rId).close() by ${Exception().asLog()}" }
        } else {
            log(iTag) { "Core($rId).close()" }
        }
        leaseScope.coroutineContext.cancelChildren()
        // TODO, do we need to lock and set isClosing?
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

        override fun close() {
            if (Bugs.isTrace) log(iTag, VERBOSE) { "Lease($id).close(): Closing... ($job)." }

            runBlocking {
                if (Bugs.isDive) {
                    log(iTag, VERBOSE) { "Lease($id).close(): Close code running, waiting for lock! ($job)" }
                }
                lock.withLock {
                    if (Bugs.isDive) {
                        log(iTag, VERBOSE) { "Lease($id).close(): Lock acquired, removing our lease! ($job)" }
                    }
                    leases.remove(this@Lease).also {
                        val leaseSize = leases.size
                        if (Bugs.isTrace) {
                            if (it) {
                                log(iTag, VERBOSE) { "Lease($id).close(): Removed! (now $leaseSize) ($job)" }
                            } else {
                                log(iTag, WARN) { "Lease($id).close(): Already removed? (now $leaseSize) ($job)" }
                            }
                        }
                    }

                    if (leases.isEmpty()) {
                        isClosing = true
                        log(iTag, DEBUG) { "Lease($id).close(): ZERO leases left for $rId, setting isClosing=true" }
                    }

                    if (job.isActive) {
                        if (Bugs.isTrace) log(iTag) { "Lease($id).close(): Canceling job! ($job)" }
                        job.cancel()
                    } else {
                        if (Bugs.isTrace) log(iTag, WARN) { "Lease($id).close(): Already cancelled! ($job)" }
                    }

                    if (job.isCompleted) {
                        if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($id).close(): Job is completed ($job)" }
                    } else {
                        if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($id).close(): Waiting for completion ($job)" }
                        job.join()
                        if (Bugs.isDive) log(iTag, VERBOSE) { "Lease($id).close(): Job is now completed ($job)" }
                    }
                }

                if (Bugs.isDive) {
                    try {
                        leases.toList().forEachIndexed { index, lease ->
                            log(iTag, VERBOSE) { "Lease($id).close(): Remaining lease #$index - $lease" }
                        }
                    } catch (e: Exception) {
                        log(iTag, VERBOSE) { "Lease($id).close(): Lease logging concurrency error" }
                    }
                }
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

    sealed interface Event<T> {
        data class Resource<T>(val id: String, val resource: T) : Event<T>
        data class Error<T>(val error: Throwable) : Event<T>
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

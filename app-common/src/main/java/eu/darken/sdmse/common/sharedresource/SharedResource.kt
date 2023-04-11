package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.traceCall
import eu.darken.sdmse.common.error.tryUnwrap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A utility class to create child/parent dependencies for expensive resources.
 * Allows keeping reusable resources alive until it is no longer needed by anyone.
 */
@Suppress("ProtectedInFinal")
open class SharedResource<T : Any> constructor(
    tag: String,
    parentScope: CoroutineScope,
    source: Flow<T>,
) : KeepAlive {
    private val iTag = "$tag:SR"
    override val resourceId: String = iTag

    private val leaseScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val lock = Mutex()

    private val leases = mutableSetOf<ActiveLease>()
    private val children = mutableMapOf<SharedResource<*>, KeepAlive>()

    override val isClosed: Boolean
        get() = !isAlive

    private var isAlive: Boolean = false

    private val resourceHolder: Flow<Event<T>> = source
        .onStart {
            lock.withLock {
                isAlive = true
                log(iTag, DEBUG) { "Acquiring shared resource..." }

                if (children.isNotEmpty()) {
                    val _children = children.values.toList()
                    log(iTag, WARN) { "Non-empty child references: $_children" }
                    children.clear()
                }
            }
        }
        .onCompletion {
            lock.withLock {
                isAlive = false
                log(iTag, VERBOSE) { "Releasing shared resource..." }

                children.values.forEach {
                    log(iTag, VERBOSE) { "Closing child resource: $it" }
                    it.close()
                }
                children.clear()

                if (leases.isNotEmpty()) {
                    if (Bugs.isDebug) {
                        val remainingLeases = leases.joinToString()
                        log(iTag, VERBOSE) { "Cleaning up remaining leases: $remainingLeases" }
                    }

                    leases
                        .filter { it.job.isActive }
                        .forEachIndexed { index, activeLease ->
                            if (Bugs.isTrace) log(iTag, VERBOSE) { "Canceling #$index: $activeLease..." }
                            activeLease.job.cancelAndJoin()
                        }

                    if (Bugs.isDebug) log(iTag, VERBOSE) { "Remaining leases have been cleaned up." }

                    leases.clear()
                }

                val orphanedLeases = leaseScope.coroutineContext.job.children.filter { it.isActive }.toList()
                if (orphanedLeases.isNotEmpty()) {
                    log(iTag, WARN) { "Orphaned leases: $orphanedLeases" }
                    // This cancels all active leases, at the latest.
                    leaseScope.coroutineContext.cancelChildren()
                }

                log(iTag, DEBUG) { "Shared resource flow completed." }
            }
        }
        .map {
            @Suppress("USELESS_CAST")
            Event.Resource(it) as Event<T>
        }
        .onEach { log(iTag, VERBOSE) { "Resource ready: $it" } }
        .catch {
            log(iTag, WARN) { "Failed to provide resource: ${it.asLog()}" }
            emit(Event.Error(it))
        }
        .shareIn(parentScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    suspend fun get(): Resource<T> {
        if (Bugs.isTrace && !isAlive) {
            log(iTag, DEBUG) { "get(): Reviving SharedResource" }
            log(iTag, VERBOSE) { "get(): Revive call origin: ${traceCall()}" }
        }

        val activeLease = lock.withLock {
            val job = resourceHolder.launchIn(leaseScope).apply {
                invokeOnCompletion {
                    if (Bugs.isTraceDeepDive) {
                        val leaseSize = leases.size
                        log(iTag, VERBOSE) { "get(): Lease completed (now=$leaseSize, $job)." }
                    }
                }
            }

            ActiveLease(
                job,
                if (Bugs.isTraceDeepDive) traceCall() else null
            ).also {
                if (Bugs.isTraceDeepDive) {
                    log(iTag, VERBOSE) { "get(): Adding new lease ($job)" }
                }

                leases.add(it)

                if (Bugs.isTraceDeepDive) {
                    val leaseSize = leases.size
                    log(iTag, VERBOSE) { "get(): Now holding $leaseSize lease(s)" }
                }
            }
        }

        val resource = try {
            if (Bugs.isTraceDeepDive) log(iTag, VERBOSE) { "get(): Retrieving resource" }
            when (val event = resourceHolder.first()) {
                is Event.Error -> throw event.error
                is Event.Resource -> event.resource
            }
        } catch (e: Exception) {
            log(iTag, WARN) { "get(): Failed to retrieve resource (${e.asLog()}" }
            activeLease.close()
            throw e.tryUnwrap()
        }

        return Resource(resource, activeLease)
    }

    inner class ActiveLease(
        internal val job: Job,
        private val traceTag: String? = null
    ) : KeepAlive {
        override val resourceId: String = this@SharedResource.resourceId

        override val isClosed: Boolean
            get() = !job.isActive

        override fun close() {
            if (Bugs.isTraceDeepDive) {
                log(iTag, VERBOSE) { "Closing keep alive ($job)." }
            }
            leaseScope.launch {
                if (Bugs.isTraceDeepDive) log(iTag, VERBOSE) { "Close code running, waiting for lock! ($job)" }
                val removed = lock.withLock {
                    if (Bugs.isTraceDeepDive) log(iTag, VERBOSE) { "Close code running, WITH lock! ($job)" }

                    leases.remove(this@ActiveLease).also {
                        if (Bugs.isTraceDeepDive) log(iTag, VERBOSE) { "Lease removed, will cancel! ($job)" }

                        if (job.isActive) {
                            job.cancel()
                        } else {
                            if (Bugs.isTrace) log(iTag, WARN) { "Already closed! ($job)" }
                        }
                    }
                }

                if (Bugs.isTraceDeepDive) {
                    val leaseSize = leases.size
                    if (removed) {
                        log(iTag, VERBOSE) { "Active lease removed (now $leaseSize) ($job)" }
                    } else {
                        log(iTag, WARN) { "Lease was already removed? (now $leaseSize) ($job)" }
                    }

                    try {
                        leases.toList().forEachIndexed { index, activeLease ->
                            log(iTag, VERBOSE) { "Lease #$index - $activeLease" }
                        }
                    } catch (e: Exception) {
                        log(iTag, VERBOSE) { "Lease logging concurrency error" }
                    }
                }
            }

        }

        override fun toString(): String = "ActiveLease(job=$job)${traceTag?.let { "\nCreated at $it" } ?: ""}"
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
            if (Bugs.isTraceDeepDive) log(iTag, VERBOSE) { "Already keeping child alive: $child" }
            return@withLock
        }

        if (!isAlive) {
            log(iTag, VERBOSE) { "addChild(): Can't add child, we are not alive: $child" }
            child.close()
            return@withLock
        }

        val keepAlive = child.get()
        val wrapped = Child(child, keepAlive)
        children[child] = wrapped

        if (Bugs.isTraceDeepDive) {
            val childrenSize = children.size
            log(iTag, VERBOSE) { "addChild(): Resource now has $childrenSize children: $child" }
        }
    }

    override fun close() {
        if (!isAlive) return
        log(iTag) { "close()" }
        leaseScope.coroutineContext.cancelChildren()
    }

    override fun toString(): String =
        "SharedResource(tag=$iTag, leases=${leases.size}, children=${children.size})"

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

    sealed class Event<T> {

        data class Resource<T>(val resource: T) : Event<T>()
        data class Error<T>(val error: Throwable) : Event<T>()
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

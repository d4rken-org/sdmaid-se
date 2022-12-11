package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.error.getStackTraceString
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
    private val tag: String,
    parentScope: CoroutineScope,
    source: Flow<T>,
) : KeepAlive {
    private val iTag = "$tag:SR"

    private val childScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val lock = Mutex()

    private val activeLeases = mutableSetOf<ActiveLease>()
    private val children = mutableSetOf<KeepAlive>()
    private val parents = mutableMapOf<SharedResource<*>, Resource<T>>()

    override val isClosed: Boolean
        get() = !isAlive

    var isAlive: Boolean = false
        private set

    private val resourceHolder: Flow<Event<T>> = source
        .onStart {
            lock.withLock {
                isAlive = true
                log(iTag, VERBOSE) { "Acquiring shared resource..." }

                if (activeLeases.isNotEmpty()) {
                    log(iTag, WARN) { "Non-empty activeLeases: $activeLeases" }
                    if (Bugs.isDebug) throw IllegalStateException("Non-empty activeLeases: $activeLeases")
                    activeLeases.forEach { it.close() }
                    activeLeases.clear()
                }

                if (children.isNotEmpty()) {
                    log(iTag, WARN) { "Non-empty child references: $children" }
                    if (Bugs.isDebug) throw IllegalStateException("Non-empty child references: $children")
                    children.clear()
                }

                if (parents.isNotEmpty()) {
                    log(iTag, WARN) { "Non-empty parent references: $parents" }
                    parents.clear()
                }
            }
        }
        .onCompletion {
            lock.withLock {
                isAlive = false
                log(iTag, VERBOSE) { "Releasing shared resource..." }

                // Cancel all borrowed resources
                childScope.coroutineContext.cancelChildren()

                activeLeases
                    .filterNot { it.isClosed }
                    .forEach {
                        log(iTag, WARN) { "Shared resource released with despite active lease: $it" }
                        if (Bugs.isDebug) throw IllegalStateException("Shared resource released with despite active leases: $activeLeases")
                    }
                activeLeases.clear()

                children.forEach {
                    log(iTag, VERBOSE) { "Closing child resource: $it" }
                    it.close()
                }
                children.clear()

                parents.clear()
            }
        }
        .map {
            @Suppress("USELESS_CAST")
            Event.Resource(it) as Event<T>
        }
        .catch {
            log(iTag, WARN) { "Failed to provide resource: ${it.asLog()}" }
            emit(Event.Error(it))
        }
        .onEach { log(iTag, VERBOSE) { "Resource ready: $it" } }
        .shareIn(parentScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    suspend fun get(): Resource<T> {
        if (Bugs.isDebug && !isAlive) {
            log(iTag, VERBOSE) { "get() Reviving SharedResource: $iTag\n${Throwable().getStackTraceString()}" }
//            log(tag, VERBOSE) { "get() Reviving SharedResource" }
        }

        val activeLease = lock.withLock {
            val job = resourceHolder.launchIn(childScope).apply {
                invokeOnCompletion {
                    log(iTag, VERBOSE) {
                        "get(): Resource lease completed (leases=${activeLeases.size}, parents=${parents.size})"
                    }
                    if (Bugs.isDebug && parents.isNotEmpty()) {
                        log(iTag, VERBOSE) { "parents=${parents.values.joinToString("\n")}" }
                    }
                }
            }

            ActiveLease(job)
        }

        val resource = try {
            log(iTag, VERBOSE) { "get(): Retrieving resource" }
            when (val event = resourceHolder.first()) {
                is Event.Error -> throw event.error
                is Event.Resource -> event.resource
            }
        } catch (e: Exception) {
            log(iTag, VERBOSE) { "get(): Failed to retrieve resource: ${e.asLog()}" }
            activeLease.close()
            throw e.tryUnwrap()
        }

        lock.withLock {
            if (activeLease.isClosed) {
                log(iTag, ERROR) { "get(): We got a resource, but the lease is already closed???" }
            } else {
                log(iTag, VERBOSE) { "get(): Adding new lease: $activeLease" }
                activeLeases.add(activeLease)
                log(iTag, VERBOSE) { "get(): Now holding ${activeLeases.size} lease(s)" }
            }
        }

        return Resource(resource, activeLease)
    }

    inner class ActiveLease(private val job: Job) : KeepAlive {
        override val isClosed: Boolean
            get() = !job.isActive

        override fun close() {
            log(iTag, VERBOSE) { "Closing keep alive" }
            if (!job.isActive) {
                log(iTag, WARN) { "Already closed!" }
            } else {
                job.cancel()
                activeLeases.remove(this)
            }
        }

        override fun toString(): String = "ActiveLease(job=$job)"
    }

    /**
     * A child resource will be kept alive by this resource, and will be closed once this resource is closed.
     *
     * The backup module is a child resource of the root shell
     * When the root shell closes, the backup module needs to "close" too.
     * But the backupmodule, while open, keeps the root shell alive.
     */
    suspend fun addChild(resource: Resource<*>) = lock.withLock {
        if (!isAlive) {
            log(iTag, WARN) { "addChild(): Can't add holder is already closed: $resource" }
            resource.close()
            return@withLock
        }

        val wrapped = ChildResource(resource)

        if (!children.add(wrapped)) {
            log(iTag, WARN) { "addChild(): Child resource has already been added: $resource" }
        } else {
            log(iTag, VERBOSE) { "addChild(): Resource now has ${children.size} children: $resource" }
        }
    }

    private class ChildResource(private val resource: Resource<*>) : KeepAlive {
        var closed: Boolean = false

        override val isClosed: Boolean
            get() = closed || resource.isClosed

        override fun close() {
            closed = true
            resource.close()
        }

        override fun toString(): String = "ChildResource(isClosed=$isClosed, resource=$resource)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChildResource) return false

            if (resource != other.resource) return false

            return true
        }

        override fun hashCode(): Int = resource.hashCode()
    }

    suspend fun addParent(parent: HasSharedResource<*>): SharedResource<T> = addParent(parent.sharedResource)

    /**
     * Convenience method to add us as child to a parent. See [addChild]
     * While the parent is alive, we will be kept alive too.
     *
     * rootResource.keepAliveWith(appBackupModule)
     * While the app backup module is active, don't release the root resource so it can be re-used
     */
    suspend fun addParent(parent: SharedResource<*>): SharedResource<T> {
        if (!parent.isAlive) {
            log(iTag, WARN) { "Parent(${parent.iTag}) is closed, not adding keep alive: ${Throwable().asLog()}" }
//            log(tag, WARN) { "Parent(${parent.tag}) is closed, not adding keep alive." }
            return this
        }

        if (parents.contains(parent)) {
            log(iTag, VERBOSE) { "Parent already contains us as keep-alive" }
            return this
        }

        log(iTag, VERBOSE) { "Adding us as new keep-alive to parent $parent" }

        val ourself = get()

        lock.withLock {
            if (parents.contains(parent)) {
                // Race condition, synchronizing on get() would lead to dead-lock
                ourself.close()
            } else {
                // Store this so we can detect duplicate calls to `keepAliveWith`
                parents[parent] = ourself
                // Add our self as child to the parent, so if the parent is cancelled, we can be cancelled too
                parent.addChild(ourself)
            }
        }

        return this
    }

    override fun close() {
        log(iTag) { "close()" }
        childScope.coroutineContext.cancelChildren()
    }

    override fun toString(): String =
        "SharedResource(tag=$iTag, leases=${activeLeases.size}, children=${children.size}, parents=${parents.size})"

    sealed class Event<T> {

        data class Resource<T>(val resource: T) : Event<T>()
        data class Error<T>(val error: Throwable) : Event<T>()
    }

    companion object {
        fun createKeepAlive(tag: String, scope: CoroutineScope): SharedResource<Any> = SharedResource(
            tag,
            scope,
            callbackFlow {
                send(Any())
                awaitClose()
            }
        )
    }

}

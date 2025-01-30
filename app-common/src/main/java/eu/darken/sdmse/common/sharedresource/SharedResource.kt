package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.traceCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.Duration
import java.util.UUID

/**
 * A utility class to create child/parent dependencies for expensive resources.
 * Allows keeping reusable resources alive until it is no longer needed by anyone.
 */
open class SharedResource<T : Any>(
    tag: String,
    parentScope: CoroutineScope,
    private val source: Flow<T>,
    private val stopTimeout: Duration = Duration.ofSeconds(3),
) : KeepAlive {
    private val iTag = "$tag:SR"
    override val resourceId: String = iTag

    private val coreLock = Mutex()

    private val leaseScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val leaseCheckLock = Mutex()
    private var leaseCheckJob: Job? = null

    private val leases = mutableSetOf<Lease>()
    private val children = mutableMapOf<SharedResource<*>, KeepAlive>()

    private var sId: String? = null
    private var sourceJob: Job? = null
    private var sourceValue: T? = null
    private var sourceError: Throwable? = null

    override val isClosed: Boolean
        get() = sourceJob == null

    suspend fun get(): Resource<T> {
        val lId = "L:${UUID.randomUUID().toString().takeLast(4)}"
        if (Bugs.isTrace) {
            val call = traceCall()
            log(iTag, VERBOSE) { "[$sId|$lId]-get() ... via $call" }
        }

        if (sourceJob?.isActive == false) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() Source is currently closing, waiting..." }
            while (sourceJob != null) yield()
        }

        var lease: Lease? = null

        coreLock.withLock("[$sId|$lId]-get()-sourcejob") {
            withContext(NonCancellable) {
                if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-get() getting lease..." }
                lease = Lease(
                    id = lId,
                    job = leaseScope.launch {
                        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-get() Lease is active" }
                        awaitCancellation()
                    },
                ).also {
                    leases.add(it)
                    if (Bugs.isTrace) {
                        log(iTag, VERBOSE) { "[$sId|$lId]-get() Now holding ${leases.size} lease(s)" }
                        leases.toList().forEachIndexed { i, l ->
                            log(iTag, VERBOSE) { "[$sId|$lId]-get() Now holding #$i - $l" }
                        }
                    }
                }

                if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() Checking for source job..." }
                if (sourceJob != null) {
                    if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-get() Source job already exists" }
                    return@withContext
                }

                if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() Launching source job..." }
                sId = "S:${UUID.randomUUID().toString().takeLast(4)}"
                sourceError = null
                sourceJob = source
                    .onStart {
                        if (Bugs.isTrace) log(iTag) { "[$sId|$lId]-source: Starting source..." }
                    }
                    .onEach {
                        if (Bugs.isTrace) log(iTag) { "[$sId|$lId]-source: sourceValue=$it" }
                        sourceValue = it
                    }
                    .onCompletion { reason ->
                        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-source: onCompletion due to $reason" }
                        sourceError = reason
                        if (reason is InternalCancelationException) {
                            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-source: Internal cancel, no cleanup" }
                            return@onCompletion
                        }
                        leaseScope.launch(NonCancellable) {
                            if (Bugs.isTrace) {
                                log(iTag, DEBUG) { "[$sId|$lId]-source: onCompletion calling closeLeases()" }
                            }
                            closeLeases("onCompletion")
                            if (Bugs.isTrace) {
                                log(iTag, VERBOSE) {
                                    "[$sId|$lId]-source: onCompletion calling leaseCheck(forced=true)"
                                }
                            }
                            leaseCheck("onCompletion", forced = true)
                        }
                        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-source: onCompletion done" }
                    }
                    .catch { error -> log(iTag, WARN) { "[$sId|$lId]-source ERROR: ${error.asLog()}" } }
                    .launchIn(leaseScope)
                if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() ...source job launched $sourceJob" }
            }
        }

        var value: T? = sourceValue
        var error: Throwable? = sourceError
        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-get() Now waiting... (value=$value, error=$error)" }
        while (value == null && error == null) {
            value = sourceValue?.also {
                if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() sourceValue loop, got $it" }
            }
            error = sourceError?.also {
                if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() sourceError loop, got $it" }
            }
            yield()
        }

        if (value == null) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|$lId]-get() sourceValue failed, waiting for cleanup" }
            while (sourceJob != null) yield()
            if (Bugs.isTrace) log(iTag, WARN) { "[$sId|$lId]-get() sourceValue failed, throwing $error" }
            throw error!!
        }

        if (Bugs.isTrace) log(iTag) { "[$sId|$lId]-get() returning value $value" }
        return Resource(value, lease!!)
    }

    // Must be called with coreLock.withLock { }
    private suspend fun closeLease(tag: String, lease: Lease): Unit = withContext(NonCancellable) {
        val lId = lease.id
        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-closeLease()-$tag on $lease" }

        leases.remove(lease).also {
            if (Bugs.isTrace) {
                val leaseSize = leases.size
                if (it) log(iTag, VERBOSE) { "[$sId|$lId]-closeLease()-$tag Removed this lease (now $leaseSize)" }
                else log(iTag, WARN) { "[$sId|$lId]-closeLease()-$tag Already removed? (now $leaseSize) " }
            }
        }

        if (Bugs.isTrace) log(iTag) { "[$sId|$lId]-closeLease()-$tag Canceling lease job: ${lease.job}" }
        lease.job.cancelAndJoin()
        if (Bugs.isTrace) {
            log(iTag, VERBOSE) { "[$sId|$lId]-closeLease()-$tag Lease job completed" }
            leases.forEachIndexed { index, lease ->
                log(iTag, VERBOSE) { "[$sId|$lId]-closeLease()-$tag Remaining lease #$index - $lease" }
            }
        }
    }

    private suspend fun leaseCheck(tag: String, forced: Boolean) = leaseCheckLock.withLock {
        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-leaseCheck(forced=$forced)-$tag" }

        if (leaseCheckJob != null) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-leaseCheck()-$tag canceling previous lease check..." }
            leaseCheckJob!!.cancelAndJoin()
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-leaseCheck()-$tag ... previous check cancelled" }
        }

        if (forced || stopTimeout == Duration.ZERO) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-leaseCheck()-$tag Running doLeasecheck() immediately" }
            doLeaseCheck(tag)
        } else {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-leaseCheck()-$tag Launching delayed doLeaseCheck() job" }
            leaseCheckJob = leaseScope.launch {
                if (Bugs.isTrace) {
                    log(iTag, DEBUG) { "[$sId|_]-leaseCheck()-$tag waiting for expiration ($stopTimeout)" }
                }
                delay(stopTimeout.toMillis())
                doLeaseCheck(tag)
            }
        }
    }

    private suspend fun doLeaseCheck(tag: String) = coreLock.withLock("doLeaseCheck()-$tag") {
        if (leases.isNotEmpty()) {
            if (Bugs.isTrace) {
                log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag There are leases (${leases.size}), aborting" }
                leases.forEachIndexed { index, lease ->
                    log(iTag, VERBOSE) { "[$sId|_]-doLeaseCheck()-$tag Current lease #$index - $lease" }
                }
            }
            return
        }
        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag ZERO leases left" }

        if (sourceJob == null) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag sourceJob was already null" }
            return@withLock
        }

        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag Cancelling sourceJob" }
        sourceJob!!.cancel(InternalCancelationException("[$sId|_]-doLeaseCheck()-$tag ZERO leases left"))
        sourceJob!!.join() // Waits till source.onComplete is done
        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag sourceJob has completed" }

        children.apply {
            if (isEmpty()) return@apply
            onEachIndexed { index, entry ->
                if (Bugs.isTrace) {
                    log(iTag, VERBOSE) { "[$sId|_]-doLeaseCheck()-$tag Closing child #$index ${entry.value}" }
                }
                entry.value.close()
            }
            clear()

            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-doLeaseCheck() Remaining children have been cleared" }
        }

        sourceValue = null
        sourceJob = null
        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-doLeaseCheck()-$tag Source nulled. fin." }
        sId = null
    }

    override fun close() {
        if (isClosed) {
            if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() already closed" }
            return
        } else {
            if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() via ${Exception().asLog()}" }
        }

        runBlocking(NonCancellable) {
            if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() calling closeLeases()" }
            closeLeases("close()")
            leaseCheck("close", forced = false)
        }
    }

    private suspend fun closeLeases(tag: String) = coreLock.withLock("closeLeases()-$tag") {
        if (leases.isEmpty()) {
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-closeLeases()-$tag No leases to close" }
            return@withLock
        }

        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-closeLeases()-$tag Current leases=${leases.size}" }
        while (leases.isNotEmpty()) {
            if (Bugs.isTrace) log(iTag) { "[$sId|_]-closeLeases()-$tag Current leases: ${leases.map { it.id }}" }
            val lease = leases.first()
            closeLease("closeLeases", lease)
        }

        if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-closeLeases()-$tag All leases closed" }
    }

    /**
     * A child resource will be kept alive by this resource, and will be closed once this resource is closed.
     *
     * The backup module is a child resource of the root shell
     * When the root shell closes, the backup module needs to "close" too.
     * But the backupmodule, while open, keeps the root shell alive.
     */
    suspend fun addChild(child: SharedResource<*>) = coreLock.withLock("addChild-${child.resourceId}") {
        if (children.contains(child)) {
            if (Bugs.isTrace) {
                log(iTag, VERBOSE) { "[$sId|_]-addChild() Already keeping child alive: $child" }
            }
            return@withLock
        }

        if (isClosed) {
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-addChild() Can't add child, we are not alive: $child" }
            if (!child.isClosed) {
                val trace = IllegalStateException("Tried to add open child to closed parent")
                log(iTag, WARN) { "[$sId|_]-addChild() We are closed! Can't add open child $child:\n${trace.asLog()}" }
            }
            return@withLock
        }

        if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-addChild() Adding child to us: $child" }

        val keepAlive = child.get()
        val wrapped = Child(child, keepAlive)
        children[child] = wrapped

        if (Bugs.isTrace) {
            val childrenSize = children.size
            log(iTag, VERBOSE) { "[$sId|_]-addChild() Resource now has $childrenSize " }
            if (Bugs.isTrace) {
                children.onEachIndexed { index, entry ->
                    log(iTag, VERBOSE) { "[$sId|_]-addChild() Now has #$index ${entry.value} " }
                }
            }
        }
    }

    override fun toString(): String =
        "SharedResource(tag=$iTag, sId=$sId, leases=${leases.size}, children=${children.size})"

    inner class Lease(
        internal val id: String,
        internal val job: Job,
    ) : KeepAlive {
        override val resourceId: String = this@SharedResource.resourceId

        override val isClosed: Boolean
            get() = !job.isActive

        override fun close() = runBlocking(NonCancellable) {
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$id]-Lease.close() Close called on lease..." }

            if (isClosed) {
                if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$id]-Lease.close() Already closed" }
                return@runBlocking
            }

            coreLock.withLock("[$sId|$id]-Lease.close()") {
                closeLease("Lease.close", this@Lease)
            }

            leaseCheck("Lease.close", forced = false)

            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$id]-Lease.close() ... finished close on lease" }
        }

        override fun toString(): String = "Lease(id=$id, job=$job)"
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
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-Child.close() Close called on $child" }
            closed = true
            ourKeepAlive.close()
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-Child.close() ... finished close on $child" }
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

    private class InternalCancelationException(override val message: String) : CancellationException()

    companion object {
        fun createKeepAlive(
            tag: String,
            scope: CoroutineScope,
            stopTimeout: Duration = Duration.ofSeconds(3),
        ): SharedResource<Any> = SharedResource(
            tag = tag,
            parentScope = scope,
            stopTimeout = stopTimeout,
            source = callbackFlow {
                send("$tag{${Any().hashCode()}}")
                awaitClose()
            }
        )
    }

}

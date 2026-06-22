package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.traceCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
    /**
     * Promote selected lifecycle breadcrumbs (acquire / source-launch / wait / cache-hit / completion)
     * to non-trace DEBUG, so silent-failure debug logs from this resource can be diagnosed without trace mode.
     * Off by default — opt in per resource where silent failures are a known support pain point.
     */
    private val verboseLifecycle: Boolean = false,
) : KeepAlive {
    private val iTag = "$tag:SR"
    override val resourceId: String = iTag

    private inline fun lifecycleLog(message: () -> String) {
        if (verboseLifecycle || Bugs.isTrace) log(iTag, DEBUG, message = message)
    }

    private val coreLock = Mutex()

    private val leaseScope = CoroutineScope(parentScope.newCoroutineContext(SupervisorJob()))

    private val leaseCheckLock = Mutex()
    private var leaseCheckJob: Job? = null

    private val leases = mutableSetOf<Lease>()
    private val children = mutableMapOf<SharedResource<*>, KeepAlive>()

    /**
     * The single currently-active source "generation". A new [get] either reuses this or, when it's
     * null (none yet, or a previous one was detached for teardown), launches a fresh one. When the
     * last lease is released the generation is *detached* — the slot is cleared immediately under
     * [coreLock] and the slow teardown runs off-lock — so a new [get] never waits for a closing
     * generation to finish (which can take minutes if the underlying source, e.g. a root host, is
     * slow to tear down).
     */
    @Volatile
    private var active: Generation<T>? = null
    private var generationCounter: Long = 0

    private class Generation<T : Any>(
        val token: Long,
        val id: String,
    ) {
        /** Completed when the first value arrives; completed exceptionally if the source fails first. */
        val ready = CompletableDeferred<Unit>()
        @Volatile var value: T? = null
        @Volatile var error: Throwable? = null
        var job: Job? = null
    }

    /** Id of the active generation, for log breadcrumbs. */
    private val sId: String?
        get() = active?.id

    override val isClosed: Boolean
        get() = active == null

    suspend fun get(): Resource<T> {
        val lId = "L:${UUID.randomUUID().toString().takeLast(4)}"
        if (Bugs.isTrace) {
            val call = traceCall()
            log(iTag, VERBOSE) { "[$sId|$lId]-get() ... via $call" }
        }

        var lease: Lease? = null
        var gen: Generation<T>? = null

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
                active?.let {
                    if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|$lId]-get() Source job already exists" }
                    gen = it
                    return@withContext
                }

                val newGen = Generation<T>(
                    token = ++generationCounter,
                    id = "S:${UUID.randomUUID().toString().takeLast(4)}",
                )
                active = newGen
                gen = newGen
                lifecycleLog { "[${newGen.id}|$lId]-get() Launching source job..." }
                newGen.job = source
                    .onStart {
                        lifecycleLog { "[${newGen.id}|$lId]-source: Starting source..." }
                    }
                    .onEach {
                        if (Bugs.isTrace) log(iTag) { "[${newGen.id}|$lId]-source: sourceValue=$it" }
                        // Only the still-active generation may publish its value; a detached/stale
                        // generation must never clobber a fresh one's state.
                        if (active === newGen) newGen.value = it
                        newGen.ready.complete(Unit)
                    }
                    .onCompletion { reason ->
                        lifecycleLog { "[${newGen.id}|$lId]-source: onCompletion due to $reason" }
                        if (active === newGen) newGen.error = reason
                        // Wake any get() still waiting on the first value.
                        if (!newGen.ready.isCompleted) {
                            newGen.ready.completeExceptionally(
                                reason ?: IllegalStateException("Source completed without a value")
                            )
                        }
                        if (reason is InternalCancelationException) {
                            if (Bugs.isTrace) log(iTag, DEBUG) { "[${newGen.id}|$lId]-source: Internal cancel, no cleanup" }
                            return@onCompletion
                        }
                        // Self-completion (error / natural end) of the *active* generation: release leases
                        // and detach. The schedule-time `active === newGen` guard can go stale before the
                        // launched coroutine runs — a concurrent close()+get() may detach newGen and install
                        // a fresh generation — so re-validate under coreLock and close-leases + detach in ONE
                        // atomic hold. Otherwise we'd globally close the *fresh* generation's lease and detach
                        // it instead.
                        if (active === newGen) {
                            leaseScope.launch(NonCancellable) {
                                // leaseCheckLock -> coreLock: the SAME order leaseCheck() uses, so no deadlock.
                                leaseCheckLock.withLock {
                                    val detached = coreLock.withLock("onCompletion-${newGen.id}") {
                                        if (active !== newGen) {
                                            if (Bugs.isTrace) log(iTag, DEBUG) { "[${newGen.id}|$lId]-source: onCompletion superseded, skipping teardown" }
                                            return@withLock false
                                        }
                                        if (Bugs.isTrace) log(iTag, DEBUG) { "[${newGen.id}|$lId]-source: onCompletion closing leases + detaching" }
                                        closeLeasesLocked("onCompletion")
                                        detachLocked("onCompletion")
                                        true
                                    }
                                    // Only when we actually force-detached newGen: cancel its pending delayed
                                    // timer (if any) so a stale timer can't later detach a future idle
                                    // generation early. Done OFF coreLock (the timer body needs it), mirroring
                                    // leaseCheck()'s own cancel ordering. The superseded path above leaves
                                    // leaseCheckJob untouched, so it can never cancel a *different*
                                    // generation's timer.
                                    if (detached) {
                                        leaseCheckJob?.cancelAndJoin()
                                        leaseCheckJob = null
                                    }
                                }
                            }
                        }
                        if (Bugs.isTrace) log(iTag, DEBUG) { "[${newGen.id}|$lId]-source: onCompletion done" }
                    }
                    .catch { error -> log(iTag, WARN) { "[${newGen.id}|$lId]-source ERROR: ${error.asLog()}" } }
                    .launchIn(leaseScope)
                if (Bugs.isTrace) log(iTag, DEBUG) { "[${newGen.id}|$lId]-get() ...source job launched ${newGen.job}" }
            }
        }

        val generation = gen!!
        // Await readiness OFF-lock (no busy-loop, no waiting on a closing generation). If we're
        // cancelled (caller gone) or the source fails before producing a value, release the
        // provisional lease we registered above so it can't pin the resource open forever.
        if (!generation.ready.isCompleted) {
            lifecycleLog { "[${generation.id}|$lId]-get() Waiting for source value..." }
        }
        try {
            generation.ready.await()
        } catch (e: Throwable) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[${generation.id}|$lId]-get() await failed (${e.javaClass.simpleName}), releasing provisional lease" }
            lease!!.close()
            throw e
        }

        val value = generation.value
        if (value == null) {
            lease!!.close()
            val error = generation.error ?: IllegalStateException("Source produced no value")
            if (Bugs.isTrace) log(iTag, WARN) { "[${generation.id}|$lId]-get() no value, throwing $error" }
            throw error
        }

        if (Bugs.isTrace) log(iTag) { "[${generation.id}|$lId]-get() returning value $value" }
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

        if (active == null) {
            if (Bugs.isTrace) log(iTag, DEBUG) { "[$sId|_]-doLeaseCheck()-$tag active was already null" }
            return@withLock
        }
        detachLocked(tag)
    }

    /**
     * Detach the active generation and schedule its (possibly slow) teardown off-lock.
     *
     * Caller MUST hold [coreLock]. Intentionally non-suspend: its body performs no suspending calls,
     * so it can run inside any coreLock hold (doLeaseCheck, or the onCompletion self-teardown).
     */
    private fun detachLocked(tag: String) {
        val generation = active ?: return

        // Detach the generation: clear the active slot *now*, under the lock, so a subsequent get()
        // starts a fresh source immediately instead of waiting for this (potentially slow) teardown.
        if (Bugs.isTrace) log(iTag, DEBUG) { "[${generation.id}|_]-detach()-$tag Detaching generation" }
        active = null
        val detachedJob = generation.job
        val detachedChildren = children.toMap()
        children.clear()

        // Release any get() that is still parked on this generation's `ready` (e.g. a caller whose
        // lease was force-closed by close() while it was mid-acquire). It must NOT keep waiting for
        // the off-lock source teardown below, which may be slow/wedged — that's the very hang we fix.
        // No-op when a value was already produced (the common detach-after-use case).
        if (!generation.ready.isCompleted) {
            generation.ready.completeExceptionally(
                IllegalStateException("[${generation.id}|_]-detach()-$tag detached before a value was produced")
            )
        }

        // Schedule the source-job cancel+join FIRST, so a throwing child.close() below can never strand
        // it (which would leak e.g. a root host that is never disconnected). The cancel+join can itself
        // be slow, so run it off BOTH coreLock and leaseCheckLock — a wedged source close must never
        // block a future get() or leaseCheck().
        leaseScope.launch(NonCancellable) {
            detachedJob?.cancel(InternalCancelationException("[${generation.id}|_]-detach()-$tag ZERO leases left"))
            detachedJob?.join() // Waits till source.onComplete is done
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[${generation.id}|_]-detach()-$tag teardown complete" }
        }

        // Close children synchronously — this only releases the leases we hold on them and is fast;
        // callers (and tests) rely on children being closed by the time the parent reads as closed.
        // Guard each close so one misbehaving child can't abort the rest of the cleanup.
        detachedChildren.values.forEachIndexed { index, child ->
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[${generation.id}|_]-detach()-$tag Closing child #$index $child" }
            runCatching { child.close() }.onFailure {
                log(iTag, WARN) { "[${generation.id}|_]-detach()-$tag Child close failed for $child: ${it.asLog()}" }
            }
        }
    }

    override fun close() {
        runBlocking(NonCancellable) {
            // Decide AND close leases under a single coreLock hold. A cold get() registers its lease and
            // publishes `active` atomically under coreLock; an unsynchronized isClosed (== active == null)
            // fast-path could observe null while such a get() holds the lock about to set it, return early,
            // and leak the just-created lease/source. Reading the decision under the lock closes that window.
            val hadState = coreLock.withLock("close()") {
                if (active == null && leases.isEmpty()) {
                    if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() already closed" }
                    return@withLock false
                }
                if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() via ${Exception().asLog()}" }
                if (Bugs.isTrace) log(iTag) { "[$sId|_]-close() calling closeLeases()" }
                closeLeasesLocked("close()")
                true
            }
            // leaseCheck must run OFF coreLock (it takes leaseCheckLock -> coreLock via doLeaseCheck). A
            // get() that starts after the locked decision is a legitimate new acquisition; doLeaseCheck
            // keeps it if its lease survives, detaches it otherwise — both correct.
            if (hadState) leaseCheck("close", forced = false)
        }
    }

    // Caller MUST hold coreLock.
    private suspend fun closeLeasesLocked(tag: String) {
        if (leases.isEmpty()) {
            if (Bugs.isTrace) log(iTag, VERBOSE) { "[$sId|_]-closeLeases()-$tag No leases to close" }
            return
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
        val existing = children[child]
        if (existing != null && !existing.isClosed) {
            if (Bugs.isTrace) {
                log(iTag, VERBOSE) { "[$sId|_]-addChild() Already keeping child alive: $child" }
            }
            return@withLock
        }
        if (existing != null) {
            // Stale child entry — drop it and fall through to re-adopt
            if (Bugs.isTrace) {
                log(iTag, VERBOSE) { "[$sId|_]-addChild() Replacing stale closed child: $child" }
            }
            children.remove(child)
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

    /**
     * Test-only seam (module-internal): inject a raw [KeepAlive] under [key] directly into the
     * children map, bypassing the normal [addChild] adoption flow. Lets tests drive [detachLocked]'s
     * child-close loop with a child whose `close()` misbehaves (throws), to verify one bad child can
     * neither abort the rest of the cleanup nor strand the off-lock source teardown. Not for production.
     */
    internal suspend fun injectChildForTest(key: SharedResource<*>, child: KeepAlive) =
        coreLock.withLock("injectChildForTest") { children[key] = child }

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

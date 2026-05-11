package eu.darken.sdmse.common.shell.ipc

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ShellOpsIPCFlowTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    @Test
    fun `round trip preserves all event types`() {
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout("hello"),
            ShellOpsStreamEvent.Stdout("world"),
            ShellOpsStreamEvent.Stderr("warning"),
            ShellOpsStreamEvent.Exit(0),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val remote = events.asFlow().toRemoteInputStream(scope)
                val collected = remote.toShellOpsEventFlow().toList()
                collected shouldContainExactly events
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `non-zero exit code preserved`() {
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout("oops"),
            ShellOpsStreamEvent.Exit(127),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val collected = events.asFlow().toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected shouldContainExactly events
                (collected.last() as ShellOpsStreamEvent.Exit).exitCode shouldBe 127
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `error event with message round-trips`() {
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout("partial"),
            ShellOpsStreamEvent.Error("java.io.IOException: pipe closed"),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val collected = events.asFlow().toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected shouldContainExactly events
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `large stdout payload streams across multiple chunks`() {
        // 4500 lines * ~150 chars = ~675 KB raw; parceled UTF-16 would be ~1.3 MB
        // — comfortably above one binder transaction. With chunking it streams cleanly.
        val lineCount = 4500
        val line = "package:/data/app/~~hashabcdefghijk==/com.example.app${"x".repeat(70)}=com.example.app"
        val source: Flow<ShellOpsStreamEvent> = flow {
            repeat(lineCount) { emit(ShellOpsStreamEvent.Stdout(line)) }
            emit(ShellOpsStreamEvent.Exit(0))
        }
        val scope = makeScope()
        try {
            runBlocking {
                val collected = source.toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected.size shouldBe lineCount + 1
                collected.first().shouldBeInstanceOf<ShellOpsStreamEvent.Stdout>()
                (collected.first() as ShellOpsStreamEvent.Stdout).line shouldBe line
                collected.last() shouldBe ShellOpsStreamEvent.Exit(0)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `single event larger than chunk limit still fits in its own chunk`() {
        // A single Stdout line of ~128 KB (way above the 64 KB chunk target) must still be deliverable.
        val hugeLine = "x".repeat(128 * 1024)
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout(hugeLine),
            ShellOpsStreamEvent.Exit(0),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val collected = events.asFlow().toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected.size shouldBe 2
                (collected[0] as ShellOpsStreamEvent.Stdout).line.length shouldBe hugeLine.length
                collected[1] shouldBe ShellOpsStreamEvent.Exit(0)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `EOF before terminal event is a protocol failure`() {
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout("interrupted"),
            // No Exit or Error — host died mid-stream.
        )
        val scope = makeScope()
        try {
            runBlocking {
                val flow = events.asFlow().toRemoteInputStream(scope).toShellOpsEventFlow()
                val thrown = runCatching { flow.toList() }.exceptionOrNull()
                thrown.shouldBeInstanceOf<IOException>()
                (thrown.message ?: "") shouldBe "ShellOps stream ended without Exit or Error event"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `source flow error is converted to terminal Error event`() {
        val source: Flow<ShellOpsStreamEvent> = flow {
            emit(ShellOpsStreamEvent.Stdout("before failure"))
            throw IllegalStateException("host crashed")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val collected = source.toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected.size shouldBe 2
                (collected[0] as ShellOpsStreamEvent.Stdout).line shouldBe "before failure"
                collected[1].shouldBeInstanceOf<ShellOpsStreamEvent.Error>()
                (collected[1] as ShellOpsStreamEvent.Error).message shouldBe "java.lang.IllegalStateException: host crashed"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `mixed stdout and stderr preserve order`() {
        val events = listOf<ShellOpsStreamEvent>(
            ShellOpsStreamEvent.Stdout("out1"),
            ShellOpsStreamEvent.Stderr("err1"),
            ShellOpsStreamEvent.Stdout("out2"),
            ShellOpsStreamEvent.Stderr("err2"),
            ShellOpsStreamEvent.Exit(0),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val collected = events.asFlow().toRemoteInputStream(scope).toShellOpsEventFlow().toList()
                collected shouldContainExactly events
            }
        } finally {
            scope.cancel()
        }
    }
}

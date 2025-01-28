package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class SharedResourceTest : BaseTest() {
    @BeforeEach
    fun setup() {
        Bugs.apply {
            isDebug = true
            isTrace = true
            isDive = true
        }
    }

    @AfterEach
    fun teardown() {
        Bugs.apply {
            isDebug = false
            isTrace = false
            isDive = false
        }
    }

    @Test fun `Lease-close closes Core`() = runTest2(autoCancel = true) {
        (1..100).forEach {
            val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO)

            sr.isClosed shouldBe true
            val lease = sr.get()
            sr.isClosed shouldBe false
            lease.close()
            sr.isClosed shouldBe true
        }
    }

    @Test fun `multiple leases`() = runTest2(autoCancel = true) {
        val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO)

        sr.isClosed shouldBe true

        val lease1 = sr.get()
        lease1.isClosed shouldBe false
        sr.isClosed shouldBe false

        val lease2 = sr.get()
        lease2.isClosed shouldBe false

        sr.isClosed shouldBe false

        lease2.close()

        lease1.isClosed shouldBe false
        lease2.isClosed shouldBe true
        sr.isClosed shouldBe false
    }

    @Test fun `Core-close closes all leases`() = runTest2(autoCancel = true) {
        val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO)

        sr.isClosed shouldBe true

        val lease1 = sr.get().apply {
            isClosed shouldBe false
        }

        val lease2 = sr.get().apply {
            isClosed shouldBe false
        }

        sr.close()

        sr.isClosed shouldBe true
        lease1.isClosed shouldBe true
        lease2.isClosed shouldBe true
    }

    @Test fun `started parents start children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        srParent.get()

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false
    }

    @Test fun `closed parents dont add children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe true

        srParent.get()

        srChild.isClosed shouldBe true
    }

    @Test fun `closed parents close added children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        srChild.get()
        srChild.isClosed shouldBe false
        srParent.addChild(srChild)
        srChild.isClosed shouldBe true
    }

    @Test fun `childs dont keep parents alive`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        val lease1 = srParent.get().apply {
            isClosed shouldBe false
        }

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false

        lease1.close()
        lease1.isClosed shouldBe true
        srChild.isClosed shouldBe true
        srParent.isClosed shouldBe true
    }

    @Test fun `Core-close() closes all children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild1 = SharedResource.createKeepAlive("child", this + Dispatchers.IO)
        val srChild2 = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        srParent.get()

        srParent.addChild(srChild1)
        srChild1.isClosed shouldBe false

        srParent.addChild(srChild2)
        srChild2.isClosed shouldBe false

        srParent.close()
        srChild1.isClosed shouldBe true
        srChild2.isClosed shouldBe true
    }

    @Test fun `addChild double call is noop`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO)

        srParent.get()

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false
    }

    @Test fun `error during creation is forwarded`() = runTest2(autoCancel = true) {
        val srError = SharedResource<Unit>(
            tag = "parent",
            parentScope = this@runTest2 + Dispatchers.IO,
            source = flow { throw IllegalStateException() }
        )

        shouldThrow<IllegalStateException> { srError.get() }
        shouldThrow<IllegalStateException> { srError.get() }
    }
}
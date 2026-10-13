package eu.darken.sdmse.automation.core.specs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CheckIdentifiersTest : BaseTest() {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val ipcFunnel = IPCFunnel(appContext, TestDispatcherProvider())

    private val targetPkg = "com.example.target".toPkgId()

    private val specGen = object : SpecGenerator {
        override val tag = "Test"
        override suspend fun isResponsible(pkg: Installed) = false
    }

    private fun node(text: String?, vararg children: ACSNodeInfo): ACSNodeInfo = mockk(relaxed = true) {
        every { this@mockk.text } returns text
        every { packageName } returns "com.android.settings"
        every { childCount } returns children.size
        every { getChild(any()) } returns null
        children.forEachIndexed { index, child ->
            every { getChild(index) } returns child
        }
    }

    private fun stepContext(): StepContext {
        val context = object : AutomationExplorer.Context {
            override val host: AutomationHost = mockk(relaxed = true)
            override val androidContext: Context = appContext
            override val progress: Flow<Progress.Data?> = emptyFlow()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }
        return StepContext(hostContext = context, tag = "Test", stepAttempts = 0)
    }

    private fun pkgInfo(label: String?): Installed = mockk(relaxed = true) {
        every { id } returns targetPkg
        every { packageName } returns targetPkg.name
        every { this@mockk.label } returns label?.toCaString()
        every { applicationInfo } returns null
    }

    private suspend fun check(label: String?, root: ACSNodeInfo): Boolean {
        val check = with(specGen) { checkIdentifiers(ipcFunnel, pkgInfo(label)) }
        return check(stepContext(), root)
    }

    private fun settingsPage(vararg texts: String?): ACSNodeInfo = node(
        null,
        node("App info"),
        *texts.map { node(it) }.toTypedArray(),
        node("Storage & cache"),
    )

    @Test
    fun `label with a line break matches a node with a space`() = runTest {
        check("Reminder\nFLEX", settingsPage("Reminder FLEX")) shouldBe true
    }

    @Test
    fun `identical label matches`() = runTest {
        check("Reminder FLEX", settingsPage("Reminder FLEX")) shouldBe true
    }

    @Test
    fun `label with a line break does not match a different app`() = runTest {
        check("Reminder\nFLEX", settingsPage("Other App")) shouldBe false
    }

    @Test
    fun `whitespace-only label does not match unrelated nodes`() = runTest {
        check("  ", settingsPage("Other App")) shouldBe false
    }

    @Test
    fun `comparison is case-sensitive`() = runTest {
        check("reminder flex", settingsPage("Reminder FLEX")) shouldBe false
    }

    @Test
    fun `node text with a line break matches a label with a space`() = runTest {
        check("Reminder FLEX", settingsPage("Reminder\nFLEX")) shouldBe true
    }

    @Test
    fun `normalized label is found inside normalized node text`() = runTest {
        check("Reminder\nFLEX", settingsPage("App: Reminder\tFLEX (installed)")) shouldBe true
    }

    @Test
    fun `empty label does not match unrelated nodes`() = runTest {
        check("", settingsPage("Other App")) shouldBe false
    }

    @Test
    fun `nodes without text do not match`() = runTest {
        check("Reminder FLEX", node(null, node(null), node(null, node(null)))) shouldBe false
    }
}

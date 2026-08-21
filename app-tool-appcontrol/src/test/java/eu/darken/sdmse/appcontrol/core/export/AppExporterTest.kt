package eu.darken.sdmse.appcontrol.core.export

import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import testhelpers.saf.FakeDocumentsProvider
import testhelpers.saf.LegacyQueryShimProvider
import testhelpers.saf.SafTestHarness
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AppExporterTest : BaseTest() {

    private val mimeTypeTool = MimeTypeTool()
    private val sourceDir = File(IO_TEST_BASEDIR, "app-exporter-sources")

    @After
    fun tearDown() {
        // BaseTest's cleanup is a JUnit5 @AfterAll and doesn't run here.
        unmockkAll()
        LegacyQueryShimProvider.unregisterAll()
        sourceDir.deleteRecursively()
    }

    private fun harness(provider: FakeDocumentsProvider = FakeDocumentsProvider.tree()) = SafTestHarness(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        provider = provider,
    )

    private fun SafTestHarness.exporter() = AppExporter(
        context = context,
        contentResolver = contentResolver,
        mimeTypeTool = mimeTypeTool,
    )

    private fun localFile(name: String, content: String = "apk-payload"): LocalPath {
        sourceDir.mkdirs()
        return LocalPath.build(File(sourceDir, name).apply { writeText(content) })
    }

    /** An [AppInfo] whose export type follows from what the package offers, same as in production. */
    private fun appInfo(
        baseApk: APath? = localFile("base.apk"),
        splits: Set<APath>? = null,
    ): AppInfo {
        val pkg = mockk<SourceAvailable>().apply {
            every { id } returns Pkg.Id(PKG_NAME)
            every { packageName } returns PKG_NAME
            every { label } returns APP_LABEL.toCaString()
            every { userHandle } returns UserHandle2(handleId = 0)
            every { installId } returns InstallId(Pkg.Id(PKG_NAME), UserHandle2(handleId = 0))
            every { versionName } returns VERSION_NAME
            every { versionCode } returns VERSION_CODE
            every { sourceDir } returns baseApk
            every { splitSources } returns splits
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = false,
            canBeStopped = false,
            canBeExported = true,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = false,
        )
    }

    /** The display name of the document a [FakeDocumentsProvider] handed back for the n-th create. */
    private fun FakeDocumentsProvider.createdName(index: Int = 0): String? =
        nodeById(createdDocumentIds[index])?.displayName

    private fun FakeDocumentsProvider.createdMimeType(index: Int = 0): String? =
        nodeById(createdDocumentIds[index])?.mimeType

    @Test
    fun `a free name is used as is`() = runTest2 {
        val harness = harness()
        val target = appInfo()

        val result = harness.exporter().save(target, harness.treeUri)

        harness.provider.createdName() shouldBe "$BASE_NAME.apk"
        harness.provider.resolve("$BASE_NAME.apk").shouldNotBeNull()
        result.savePath shouldBe harness.docUri("$BASE_NAME.apk")
        result.exportSize shouldBe "apk-payload".length.toLong()
    }

    @Test
    fun `a taken name gets a counter before the extension`() = runTest2 {
        val harness = harness(FakeDocumentsProvider.tree { file("$BASE_NAME.apk") })

        harness.exporter().save(appInfo(), harness.treeUri)

        val createdName = harness.provider.createdName()!!
        createdName shouldBe "$BASE_NAME (1).apk"
        // The counter has to sit in front of the extension, otherwise the file stops being an APK.
        createdName.endsWith(".apk") shouldBe true
    }

    @Test
    fun `the counter keeps climbing while names are taken`() = runTest2 {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("$BASE_NAME.apk")
                file("$BASE_NAME (1).apk")
            }
        )

        harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.createdName() shouldBe "$BASE_NAME (2).apk"
    }

    @Test
    fun `a document the provider renamed is dropped and creation retried`() = runTest2 {
        // ExternalStorageProvider sanitizes or uniquifies display names, so what we asked for and what
        // we got can differ - a name without our extension must not become the export.
        val harness = harness()
        harness.provider.renameNextCreatedTo = "mangled-by-the-provider"

        val result = harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.createdName(0) shouldBe null
        harness.provider.deletedDocuments shouldContainExactly listOf(
            FakeDocumentsProvider.docIdOf(listOf("mangled-by-the-provider"))
        )
        harness.provider.createdName(1) shouldBe "$BASE_NAME.apk"
        result.savePath shouldBe harness.docUri("$BASE_NAME.apk")
    }

    @Test
    fun `a document whose name can't be read back is dropped and the failure raised`() = runTest2 {
        // A failing query must not pass as "this provider supplies no display name" - accepting the
        // document then would put the export under whatever name the provider chose.
        val harness = harness()
        val createdId = FakeDocumentsProvider.docIdOf(listOf("$BASE_NAME.apk"))
        harness.provider.failDocQueryFor[createdId] = RuntimeException("provider is having a bad day")

        shouldThrow<RuntimeException> { harness.exporter().save(appInfo(), harness.treeUri) }

        harness.provider.deletedDocuments shouldContainExactly listOf(createdId)
        harness.provider.hasNode("$BASE_NAME.apk") shouldBe false
    }

    @Test
    fun `a document without any display name is accepted`() = runTest2 {
        // Not every provider reports a display name. That is a genuine "no name", not a failed read,
        // so the document we just created stays.
        val harness = harness()
        harness.provider.createNextWithoutDisplayName = true

        val result = harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.createdName() shouldBe null
        harness.provider.deletedDocuments.shouldBeEmpty()
        result.savePath shouldBe harness.docUri("$BASE_NAME.apk")
    }

    @Test
    fun `a mangled document that can't be deleted still moves to the next candidate`() = runTest2 {
        // SAFDocFile.delete() raises for anything but a missing document, and the cleanup of a name
        // we can't use must not take the retry with it.
        val harness = harness()
        harness.provider.renameNextCreatedTo = "mangled-by-the-provider"
        harness.provider.failNextDeleteWith = SecurityException("no permission to delete")

        val result = harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.deleteCalls shouldContainExactly listOf(
            FakeDocumentsProvider.docIdOf(listOf("mangled-by-the-provider"))
        )
        harness.provider.deletedDocuments.shouldBeEmpty()
        harness.provider.createdName(1) shouldBe "$BASE_NAME.apk"
        result.savePath shouldBe harness.docUri("$BASE_NAME.apk")
    }

    @Test
    fun `a name claimed between listing and creation moves to the next candidate`() = runTest2 {
        // The listing is advice about a point in time: another writer can take the name we picked
        // before our create lands, and that failure has to turn into a retry, not into an error.
        val harness = harness()
        harness.provider.onChildQuery = { parentId ->
            harness.provider.onChildQuery = null
            harness.provider.addNode(
                FakeDocumentsProvider.Node(
                    documentId = FakeDocumentsProvider.docIdOf(listOf("$BASE_NAME.apk")),
                    parentId = parentId,
                    displayName = "$BASE_NAME.apk",
                    mimeType = MimeTypes.Apk.value,
                )
            )
        }

        val result = harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.createdDocuments shouldContainExactly listOf(
            FakeDocumentsProvider.ROOT_ID to "$BASE_NAME.apk",
            FakeDocumentsProvider.ROOT_ID to "$BASE_NAME (1).apk",
        )
        harness.provider.createdName() shouldBe "$BASE_NAME (1).apk"
        result.savePath shouldBe harness.docUri("$BASE_NAME (1).apk")
    }

    @Test
    fun `an APK export is declared as an APK`() = runTest2 {
        val harness = harness()

        harness.exporter().save(appInfo(), harness.treeUri)

        harness.provider.createdMimeType() shouldBe "application/vnd.android.package-archive"
        harness.provider.createdMimeType() shouldBe MimeTypes.Apk.value
        harness.provider.createdName() shouldBe "$BASE_NAME.apk"
    }

    @Test
    fun `a bundle export is declared with the type of its extension`() = runTest2 {
        val harness = harness()
        val target = appInfo(splits = setOf(localFile("split_config.apk", content = "split-payload")))

        val result = harness.exporter().save(target, harness.treeUri)

        target.exportType shouldBe AppExportType.BUNDLE
        // Name and type have to pair up, otherwise the framework appends a counter behind ".apks".
        harness.provider.createdMimeType() shouldBe mimeTypeTool.fromExtension("apks")
        harness.provider.createdName() shouldBe "$BASE_NAME.apks"

        val zipped = mutableListOf<String>()
        ZipInputStream(harness.contentResolver.openInputStream(result.savePath)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                zipped.add(entry.name)
            }
        }
        zipped shouldContainExactly listOf("base.apk", "split_config.apk")
    }

    @Test
    fun `a failed write removes the incomplete document and raises`() = runTest2 {
        // The source vanishing after the document exists is what leaves a zero byte file behind.
        val harness = harness()
        val vanished = localFile("vanishing.apk")
        vanished.file.delete()

        shouldThrow<Exception> { harness.exporter().save(appInfo(baseApk = vanished), harness.treeUri) }

        harness.provider.deletedDocuments shouldContainExactly listOf(
            FakeDocumentsProvider.docIdOf(listOf("$BASE_NAME.apk"))
        )
        harness.provider.hasNode("$BASE_NAME.apk") shouldBe false
    }

    companion object {
        private const val PKG_NAME = "eu.thlab.testapp"
        private const val APP_LABEL = "Test App"
        private const val VERSION_NAME = "1.2.3"
        private const val VERSION_CODE = 42L
        private const val BASE_NAME = "$APP_LABEL ($PKG_NAME) - $VERSION_NAME[$VERSION_CODE]"
    }
}

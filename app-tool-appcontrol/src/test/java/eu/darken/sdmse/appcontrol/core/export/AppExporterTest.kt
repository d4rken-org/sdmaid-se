package eu.darken.sdmse.appcontrol.core.export

import android.content.pm.ApplicationInfo
import android.net.Uri
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import testhelpers.json.toComparableKotlinxJson
import testhelpers.saf.FakeDocumentsProvider
import testhelpers.saf.LegacyQueryShimProvider
import testhelpers.saf.SafTestHarness
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AppExporterTest : BaseTest() {

    private val mimeTypeTool = MimeTypeTool()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
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
        json = json,
    )

    private fun localFile(name: String, content: String = BASE_PAYLOAD): LocalPath {
        sourceDir.mkdirs()
        return LocalPath.build(File(sourceDir, name).apply { writeText(content) })
    }

    private fun splitSource(id: String, fileName: String) = SourceAvailable.SplitSource(
        id = id,
        path = localFile(fileName, content = SPLIT_PAYLOAD),
    )

    private fun SafTestHarness.entryNames(uri: Uri): List<String> = buildList {
        ZipInputStream(contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                add(entry.name)
            }
        }
    }

    private fun SafTestHarness.entryText(uri: Uri, name: String): String? {
        ZipInputStream(contentResolver.openInputStream(uri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
            }
        }
        return null
    }

    /** An [AppInfo] whose export type follows from what the package offers, same as in production. */
    private fun appInfo(
        baseApk: APath? = localFile("base.apk"),
        splits: List<SourceAvailable.SplitSource>? = null,
        splitsNamed: List<SourceAvailable.SplitSource>? = splits,
        versionName: String? = VERSION_NAME,
    ): AppInfo {
        val pkg = mockk<SourceAvailable>().apply {
            every { id } returns Pkg.Id(PKG_NAME)
            every { packageName } returns PKG_NAME
            every { label } returns APP_LABEL.toCaString()
            every { userHandle } returns UserHandle2(handleId = 0)
            every { installId } returns InstallId(Pkg.Id(PKG_NAME), UserHandle2(handleId = 0))
            every { this@apply.versionName } returns versionName
            every { versionCode } returns VERSION_CODE
            every { sourceDir } returns baseApk
            every { splitSources } returns splits?.map { it.path }?.toSet()
            every { splitSourcesNamed } returns splitsNamed
            every { applicationInfo } returns ApplicationInfo().apply {
                minSdkVersion = MIN_SDK
                targetSdkVersion = TARGET_SDK
            }
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
    fun `a bundle export is an XAPK declared with the type of its extension`() = runTest2 {
        val harness = harness()
        val target = appInfo(splits = listOf(splitSource(SPLIT_ID, SPLIT_FILE)))

        val result = harness.exporter().save(target, harness.treeUri)

        target.exportType shouldBe AppExportType.BUNDLE
        // Name and type have to pair up, otherwise the framework appends a counter behind ".xapk".
        harness.provider.createdMimeType() shouldBe mimeTypeTool.fromExtension("xapk")
        harness.provider.createdName() shouldBe "$BASE_NAME.xapk"

        harness.entryNames(result.savePath) shouldContainExactly listOf(
            "base.apk",
            SPLIT_FILE,
            "manifest.json",
        )
    }

    @Test
    fun `the XAPK manifest describes the archive`() = runTest2 {
        val harness = harness()
        val target = appInfo(splits = listOf(splitSource(SPLIT_ID, SPLIT_FILE)))

        val result = harness.exporter().save(target, harness.treeUri)

        // Field names and types are what APKPure reads, a number where a string belongs breaks it.
        harness.entryText(result.savePath, "manifest.json")!!.toComparableKotlinxJson() shouldBe """
            {
                "xapk_version": 2,
                "package_name": "$PKG_NAME",
                "name": "$APP_LABEL",
                "version_code": "$VERSION_CODE",
                "version_name": "$VERSION_NAME",
                "min_sdk_version": "$MIN_SDK",
                "target_sdk_version": "$TARGET_SDK",
                "total_size": ${BASE_PAYLOAD.length + SPLIT_PAYLOAD.length},
                "split_apks": [
                    {
                        "file": "base.apk",
                        "id": "base"
                    },
                    {
                        "file": "$SPLIT_FILE",
                        "id": "$SPLIT_ID"
                    }
                ],
                "split_configs": [
                    "$SPLIT_ID"
                ]
            }
        """.toComparableKotlinxJson()
    }

    @Test
    fun `an app without a version name gets an empty one, not the word null`() = runTest2 {
        val harness = harness()
        val target = appInfo(
            splits = listOf(splitSource(SPLIT_ID, SPLIT_FILE)),
            versionName = null,
        )

        val result = harness.exporter().save(target, harness.treeUri)

        val manifest = Json.parseToJsonElement(harness.entryText(result.savePath, "manifest.json")!!).jsonObject
        manifest["version_name"] shouldBe JsonPrimitive("")
    }

    @Test
    fun `splits without usable ids fail before a document is created`() = runTest2 {
        // Index pairing that can't be trusted (no split names, or a length mismatch) has to end the
        // export, a partial XAPK would install as a broken app.
        val harness = harness()
        val target = appInfo(
            splits = listOf(splitSource(SPLIT_ID, SPLIT_FILE)),
            splitsNamed = null,
        )

        shouldThrow<IllegalStateException> { harness.exporter().save(target, harness.treeUri) }

        target.exportType shouldBe AppExportType.BUNDLE
        harness.provider.createdDocuments.shouldBeEmpty()
    }

    @Test
    fun `sources that share a file name fail before a document is created`() = runTest2 {
        // Two entries under one name would make an archive that no installer can take apart.
        val harness = harness()
        val nested = File(sourceDir, "nested").apply { mkdirs() }
        val collidingBase = LocalPath.build(File(nested, "base.apk").apply { writeText(BASE_PAYLOAD) })
        val target = appInfo(splits = listOf(SourceAvailable.SplitSource(SPLIT_ID, collidingBase)))

        shouldThrow<IllegalStateException> { harness.exporter().save(target, harness.treeUri) }

        harness.provider.createdDocuments.shouldBeEmpty()
    }

    @Test
    fun `a bundle without a base APK fails before a document is created`() = runTest2 {
        val harness = harness()
        val target = appInfo(
            baseApk = null,
            splits = listOf(splitSource(SPLIT_ID, SPLIT_FILE)),
        )

        shouldThrow<IllegalStateException> { harness.exporter().save(target, harness.treeUri) }

        harness.provider.createdDocuments.shouldBeEmpty()
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
        private const val MIN_SDK = 26
        private const val TARGET_SDK = 34
        private const val BASE_NAME = "$APP_LABEL ($PKG_NAME) - $VERSION_NAME[$VERSION_CODE]"
        private const val BASE_PAYLOAD = "apk-payload"
        private const val SPLIT_PAYLOAD = "split-payload"
        private const val SPLIT_ID = "config.xxhdpi"
        private const val SPLIT_FILE = "split_config.xxhdpi.apk"
    }
}

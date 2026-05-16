package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.content.Context
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class EditorOptionsCreatorTest : BaseTest() {

    private fun lookup(
        segments: Array<String>,
        type: FileType = FileType.FILE,
    ): APathLookup<*> = LocalPathLookup(
        lookedUp = LocalPath.build(*segments),
        fileType = type,
        size = 0L,
        modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
        target = null,
    )

    private fun areaInfo(
        prefix: APath,
        type: DataArea.Type,
        prefixLabel: CaString = "Prefix".toCaString(),
    ): AreaInfo = AreaInfo(
        file = prefix,
        prefix = prefix,
        dataArea = DataArea(
            path = prefix,
            type = type,
            label = "Area".toCaString(),
            userHandle = UserHandle2(handleId = 0),
        ),
        isBlackListLocation = false,
    )

    private fun mockForensics(
        responses: Map<APath, AreaInfo?>,
    ): FileForensics = mockk<FileForensics>().apply {
        coEvery { identifyArea(any<APath>()) } answers {
            val path = it.invocation.args[0] as APath
            responses[path]
        }
    }

    private fun mockContext(): Context = mockk<Context>(relaxed = true).apply {
        every { resources } returns mockk(relaxed = true)
    }

    @Test
    fun `creator produces SegmentCriterium with Equal mode for FILE targets`() = runTest2 {
        val fileLookup = lookup(arrayOf("storage", "emulated", "0", "Downloads", "file.dat"), FileType.FILE)
        val prefixPath = LocalPath.build("storage", "emulated", "0")
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(
                mapOf(fileLookup.lookedUp to areaInfo(prefixPath, DataArea.Type.SDCARD)),
            ),
        )

        val options = creator.createOptions(setOf(fileLookup))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        val criterium = options.pathCriteria!!.single()
        criterium.mode.shouldBeInstanceOf<SegmentCriterium.Mode.Equal>()
        criterium.segments shouldBe fileLookup.segments
    }

    @Test
    fun `creator produces SegmentCriterium with Start mode and trailing-slash segment for DIRECTORY targets`() = runTest2 {
        val dirLookup = lookup(arrayOf("storage", "emulated", "0", "Downloads", "subdir"), FileType.DIRECTORY)
        val prefixPath = LocalPath.build("storage", "emulated", "0")
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(
                mapOf(dirLookup.lookedUp to areaInfo(prefixPath, DataArea.Type.SDCARD)),
            ),
        )

        val options = creator.createOptions(setOf(dirLookup))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        val criterium = options.pathCriteria!!.single()
        criterium.mode.shouldBeInstanceOf<SegmentCriterium.Mode.Start>()
        // Trailing empty segment marks directory boundary.
        criterium.segments.last() shouldBe ""
    }

    @Test
    fun `creator targetAreas contains DataArea types from identifyArea results`() = runTest2 {
        val a = lookup(arrayOf("storage", "emulated", "0", "Downloads", "a.dat"))
        val b = lookup(arrayOf("storage", "emulated", "0", "Movies", "b.dat"))
        val prefixPath = LocalPath.build("storage", "emulated", "0")
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(
                mapOf(
                    a.lookedUp to areaInfo(prefixPath, DataArea.Type.SDCARD),
                    b.lookedUp to areaInfo(prefixPath, DataArea.Type.PUBLIC_MEDIA),
                ),
            ),
        )

        val options = creator.createOptions(setOf(a, b))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        options.areas shouldBe setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA)
    }

    @Test
    fun `creator label is null when targets identify to different prefixes`() = runTest2 {
        val a = lookup(arrayOf("storage", "emulated", "0", "Downloads", "a.dat"))
        val b = lookup(arrayOf("storage", "emulated", "0", "Movies", "b.dat"))
        val prefixA = LocalPath.build("storage", "emulated", "0", "Downloads")
        val prefixB = LocalPath.build("storage", "emulated", "0", "Movies")
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(
                mapOf(
                    a.lookedUp to areaInfo(prefixA, DataArea.Type.SDCARD),
                    b.lookedUp to areaInfo(prefixB, DataArea.Type.SDCARD),
                ),
            ),
        )

        val options = creator.createOptions(setOf(a, b))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        options.label shouldBe null
    }

    @Test
    fun `creator label is null and areas empty when identifyArea returns null for all targets`() = runTest2 {
        val orphan = lookup(arrayOf("some", "unknown", "path"))
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(mapOf(orphan.lookedUp to null)),
        )

        val options = creator.createOptions(setOf(orphan))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        options.label shouldBe null
        options.areas shouldBe emptySet()
    }

    @Test
    fun `creator saveAsEnabled is always true`() = runTest2 {
        val file = lookup(arrayOf("path", "x"), FileType.FILE)
        val prefixPath = LocalPath.build("path")
        val creator = EditorOptionsCreator(
            context = mockContext(),
            fileForensics = mockForensics(
                mapOf(file.lookedUp to areaInfo(prefixPath, DataArea.Type.SDCARD)),
            ),
        )

        val options = creator.createOptions(setOf(file))

        options.shouldBeInstanceOf<CustomFilterEditorOptions>()
        options.saveAsEnabled shouldBe true
    }
}

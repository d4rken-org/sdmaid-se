package eu.darken.sdmse.common.exclusion.core

import android.content.Context
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.deleteAll
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.ExclusionStorage
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.json.toComparableJson
import java.io.File

class ExclusionStorageTest : BaseTest() {
    private val testFolder = File(IO_TEST_BASEDIR, "exclusion-storage-test")
    private val moshi = SerializationAppModule().moshi()
    private val context: Context = mockk<Context>().apply {
        every { filesDir } returns testFolder
    }

    @AfterEach
    fun cleanup() {
        testFolder.deleteAll()
    }

    fun create() = ExclusionStorage(
        dispatcherProvider = TestDispatcherProvider(),
        context = context,
        moshi = moshi
    )

    @Test
    fun `combined serialization`() = runTest {
        val storage = create()

        val original = setOf(
            PkgExclusion(
                pkgId = "test.pkg".toPkgId()
            ),
            PathExclusion(LocalPath.build("test", "path"))
        )

        storage.save(original)

        val saveFile = File(testFolder, "exclusions/exclusions-v1.json")
        saveFile.readText().toComparableJson() shouldBe """
            [
                {
                    "pkgId": {
                        "name": "test.pkg"
                    },
                    "tags": [
                        "GENERAL"
                    ]
                },
                {
                    "path": {
                        "file": "/test/path",
                        "pathType": "LOCAL"
                    },
                    "tags": [
                        "GENERAL"
                    ]
                }
            ]
        """.toComparableJson()

        storage.load() shouldBe original
    }
}
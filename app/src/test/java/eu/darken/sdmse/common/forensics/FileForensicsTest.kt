package eu.darken.sdmse.common.forensics

import android.content.Context
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FileForensicsTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var csiProcessor: CSIProcessor
    @MockK lateinit var testAreaInfo: AreaInfo
    val processors = mutableSetOf<CSIProcessor>()


    @BeforeEach fun setup() {
        MockKAnnotations.init(this)

        coEvery { csiProcessor.identifyArea(any()) } returns testAreaInfo
        processors.add(csiProcessor)
    }

    @AfterEach fun teardown() {

    }

    @Test fun init() = runTest {
        val forensics = FileForensics(context, pkgRepo, processors)
        val testPath = LocalPath.build("/test")
        val areaInfo = forensics.determineLocationType(testPath)
        areaInfo shouldBe testAreaInfo
    }
}
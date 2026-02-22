package eu.darken.sdmse.common.coil

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PathPreviewFetcherTest : BaseTest() {

    private fun mockLookup(name: String): APathLookup<*> = mockk<APathLookup<*>>().apply {
        every { lookedUp } returns LocalPath.build("test", name)
    }

    @Test fun `isTextFile detects txt extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("readme.txt")) shouldBe true
    }

    @Test fun `isTextFile detects log extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("app.log")) shouldBe true
    }

    @Test fun `isTextFile detects html extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("index.html")) shouldBe true
    }

    @Test fun `isTextFile detects json extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("data.json")) shouldBe true
    }

    @Test fun `isTextFile detects kotlin extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("Main.kt")) shouldBe true
    }

    @Test fun `isTextFile detects gradle extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("build.gradle")) shouldBe true
    }

    @Test fun `isTextFile rejects image extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("photo.jpg")) shouldBe false
    }

    @Test fun `isTextFile rejects apk extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("app.apk")) shouldBe false
    }

    @Test fun `isTextFile rejects no extension`() {
        PathPreviewFetcher.isTextFile(mockLookup("noext")) shouldBe false
    }

    @Test fun `isTextFile is case insensitive`() {
        PathPreviewFetcher.isTextFile(mockLookup("README.TXT")) shouldBe true
        PathPreviewFetcher.isTextFile(mockLookup("Data.JSON")) shouldBe true
    }
}

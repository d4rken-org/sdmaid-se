package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SubDirToPkgCheckTest : BaseTest() {

    @MockK lateinit var gatewaySwitch: GatewaySwitch

    private lateinit var check: SubDirToPkgCheck

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { hasApiLevel(30) } returns true

        // Mock the extension function
        mockkStatic("eu.darken.sdmse.common.files.APathExtensionsKt")

        check = SubDirToPkgCheck(gatewaySwitch)
    }

    @Test
    fun `test Android 11 standard package`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build(
                "/data/app",
                "~~4IQxTCyRFPq4S53KU3KhBQ==",
                "com.example.app-ekB8USahHaRHG-eHQqgtaA=="
            ),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.example.app".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test Android 11 versioned TrichromeLibrary package`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build(
                "/data/app",
                "~~LZPmWFF7U8b5p2t4TdZl_g==",
                "com.google.android.trichromelibrary_661308832-Kg7UPJMq-h9KEhvNLQ_MQQ=="
            ),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.google.android.trichromelibrary_661308832".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test Android 11 Chrome package`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build(
                "/data/app",
                "~~KV8oafUkVvQFcQ0CrB9Xcg==",
                "com.android.chrome-u8-iRxh1dNuDLvYFLhiLCg=="
            ),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.android.chrome".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test non-Android 11 path returns empty`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "com.example.app-1"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe emptySet()
    }

    @Test
    fun `test incomplete path returns empty`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "~~4IQxTCyRFPq4S53KU3KhBQ=="),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe emptySet()
    }

    @Test
    fun `test regex patterns directly`() = runTest {
        // Test the ANDROID11_SUBDIR regex pattern
        val regex = Regex("^([a-zA-Z0-9._\\-]+)-[a-zA-Z0-9=_-]{24,}$")

        // Standard package
        val match1 = regex.matchEntire("com.example.app-ekB8USahHaRHG-eHQqgtaA==")
        match1?.groupValues?.get(1) shouldBe "com.example.app"

        // Versioned TrichromeLibrary package
        val match2 = regex.matchEntire("com.google.android.trichromelibrary_661308832-Kg7UPJMq-h9KEhvNLQ_MQQ==")
        match2?.groupValues?.get(1) shouldBe "com.google.android.trichromelibrary_661308832"

        // Chrome package
        val match3 = regex.matchEntire("com.android.chrome-u8-iRxh1dNuDLvYFLhiLCg==")
        match3?.groupValues?.get(1) shouldBe "com.android.chrome"

        // Invalid pattern
        val match4 = regex.matchEntire("com.example.app-123")
        match4 shouldBe null
    }

    @Test
    fun `test API level below 30 returns empty`() = runTest {
        every { hasApiLevel(30) } returns false

        val areaInfo = AreaInfo(
            file = LocalPath.build(
                "/data/app",
                "~~4IQxTCyRFPq4S53KU3KhBQ==",
                "com.example.app-ekB8USahHaRHG-eHQqgtaA=="
            ),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe emptySet()
    }

    @Test
    fun `test Android 11 single segment requires listFiles - successful case`() = runTest {
        val hashDir = "~~4IQxTCyRFPq4S53KU3KhBQ=="
        val packageDir = LocalPath.build("com.example.app-ekB8USahHaRHG-eHQqgtaA==")

        // Mock listFiles to return the package directory
        coEvery {
            any<LocalPath>().listFiles(any<GatewaySwitch>())
        } returns listOf(packageDir)

        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", hashDir),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.example.app".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test Android 11 single segment requires listFiles - empty directory`() = runTest {
        val hashDir = "~~4IQxTCyRFPq4S53KU3KhBQ=="

        // Mock listFiles to return empty list
        coEvery {
            any<LocalPath>().listFiles(any<GatewaySwitch>())
        } returns emptyList()

        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", hashDir),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe emptySet()
    }

    @Test
    fun `test Android 11 single segment requires listFiles - multiple files`() = runTest {
        val hashDir = "~~4IQxTCyRFPq4S53KU3KhBQ=="
        val packageDir1 = LocalPath.build("com.example.app1-ekB8USahHaRHG-eHQqgtaA==")
        val packageDir2 = LocalPath.build("com.example.app2-fkC9VTbiIbSIG-fHRrguaB==")

        // Mock listFiles to return multiple directories
        coEvery {
            any<LocalPath>().listFiles(any<GatewaySwitch>())
        } returns listOf(packageDir1, packageDir2)

        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", hashDir),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        // Should return empty because singleOrNull() returns null for multiple items
        result.owners shouldBe emptySet()
    }

    @Test
    fun `test Android 11 single segment requires listFiles - exception during listing`() = runTest {
        val hashDir = "~~4IQxTCyRFPq4S53KU3KhBQ=="

        // Mock listFiles to throw exception
        coEvery {
            any<LocalPath>().listFiles(any<GatewaySwitch>())
        } throws RuntimeException("Permission denied")

        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", hashDir),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        // Should return empty due to exception handling
        result.owners shouldBe emptySet()
    }

    @Test
    fun `test Android 11 single segment requires listFiles - versioned package`() = runTest {
        val hashDir = "~~LZPmWFF7U8b5p2t4TdZl_g=="
        val packageDir = LocalPath.build("com.google.android.trichromelibrary_661308832-Kg7UPJMq-h9KEhvNLQ_MQQ==")

        // Mock listFiles to return versioned package directory
        coEvery {
            any<LocalPath>().listFiles(any<GatewaySwitch>())
        } returns listOf(packageDir)

        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", hashDir),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.google.android.trichromelibrary_661308832".toPkgId(), UserHandle2(0))
        )
    }
}
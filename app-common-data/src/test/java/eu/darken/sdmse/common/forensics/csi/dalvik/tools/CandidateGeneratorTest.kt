package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import eu.darken.sdmse.common.Architecture
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CandidateGeneratorTest : BaseTest() {
    private val base1 = LocalPath.build("/data/dalvik-cache")
    private val base2 = LocalPath.build("/cache/dalvik-cache")

    private val dalvik1 = LocalPath.build(base1, "x86")
    private val dalvik2 = LocalPath.build(base1, "x64")
    private val dalvik3 = LocalPath.build(base2, "x86")
    private val dalvik4 = LocalPath.build(base2, "x64")

    private val storageDalvikProfileX861 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik1
    }
    private val storageDalvikProfileX641 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik2
    }
    private val storageDalvikProfileX862 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik3
    }
    private val storageDalvikProfileX642 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik4
    }
    private val storageData1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/data/app")
    }
    private val storageData2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/mnt/expand/uuid/app")
    }
    private val storageSystem = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/system")
    }
    private val storageSystemApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_APP
        every { path } returns LocalPath.build("/system/app")
    }
    private val storageSystemPrivApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_PRIV_APP
        every { path } returns LocalPath.build("/system/priv-app")
    }
    private val storageVendor = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/vendor")
    }

    private val areaManager = mockk<DataAreaManager>().apply {
        every { state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageData1,
                    storageData2,
                    storageSystem,
                    storageSystemApp,
                    storageSystemPrivApp,
                    storageDalvikProfileX861,
                    storageDalvikProfileX641,
                    storageDalvikProfileX862,
                    storageDalvikProfileX642,
                    storageVendor,
                )
            )
        )
    }

    private fun createGen() = DalvikCandidateGenerator(
        areaManager,
        architecture = mockk<Architecture>().apply {
            every { folderNames } returns listOf("x86", "x64")
        }
    )

    @Test
    fun `date prefix removal`() = runTest {
        val target = LocalPath.build(
            "/data/dalvik-cache/arm64/apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk@classes.art"
        )
        createGen().getCandidates(target).single {
            it == LocalPath.build("/apex/com.android.permission/priv-app/GooglePermissionController@M_2022_06/GooglePermissionController.apk")
        }
    }
}
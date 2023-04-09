package eu.darken.sdmse.appcleaner.core.forensics

import android.content.Context
import android.content.res.AssetManager
import eu.darken.sdmse.appcleaner.core.forensics.sieves.dynamic.DynamicSieve
import eu.darken.sdmse.appcleaner.core.forensics.sieves.json.JsonBasedSieve
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import java.time.Instant

abstract class BaseFilterTest : BaseTest() {

    @MockK lateinit var pkgOps: PkgOps
    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var areaManager: DataAreaManager
    @MockK lateinit var fileForensics: FileForensics
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var storageEnvironment: StorageEnvironment
    @MockK lateinit var systemCleanerSettings: SystemCleanerSettings
    @MockK lateinit var context: Context
    @MockK lateinit var assetManager: AssetManager

    val storageData1 = mockk<DataArea>().apply {
        every { flags } returns setOf(DataArea.Flag.PRIMARY)
        every { type } returns Type.DATA
        every { path } returns LocalPath.build("/data")
    }

    val storageData2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns Type.DATA
        every { path } returns LocalPath.build("/mnt/expand", rngString)
    }

    val storageDataApp1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.APP_APP
        every { path } returns storageData1.path.child("app")
    }

    val storageDataApp2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.APP_APP
        every { path } returns storageData2.path.child("app")
    }

    val storageDataAppAsec1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.APP_ASEC
        every { path } returns storageData1.path.child("app-asec")
    }

    val storageDataAppAsec2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.APP_ASEC
        every { path } returns storageData2.path.child("app-asec")
    }

    val storageDataAppPrivate1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.APP_APP_PRIVATE
        every { path } returns storageData1.path.child("app-private")
    }

    val storageDataAppPrivate2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.APP_APP_PRIVATE
        every { path } returns storageData2.path.child("app-private")
    }

    val storageDataAppLib1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.APP_LIB
        every { path } returns storageData1.path.child("app-lib")
    }

    val storageDataAppLib2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.APP_LIB
        every { path } returns storageData2.path.child("app-lib")
    }

    val storageDataSystem1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.DATA_SYSTEM
        every { path } returns storageData1.path.child("system")
    }

    val storageDataSystem2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.DATA_SYSTEM
        every { path } returns storageData2.path.child("system")
    }

    val storageDataSystemCE1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.DATA_SYSTEM_CE
        every { path } returns storageData1.path.child("system_ce")
    }

    val storageDataSystemCE2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.DATA_SYSTEM_CE
        every { path } returns storageData2.path.child("system_ce")
    }

    val storageDataSystemDE1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.DATA_SYSTEM_DE
        every { path } returns storageData1.path.child("system_de")
    }

    val storageDataSystemDE2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.DATA_SYSTEM_DE
        every { path } returns storageData2.path.child("system_de")
    }

    val storageDalvikDex1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.DALVIK_DEX
        every { path } returns storageData1.path.child("dalvik-cache", "arm64")
    }

    val storageDalvikDex2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.DALVIK_DEX
        every { path } returns storageData2.path.child("dalvik-cache", "arm64")
    }

    val storageDalvikProfile1 = mockk<DataArea>().apply {
        every { flags } returns storageData1.flags
        every { type } returns Type.DALVIK_PROFILE
        every { path } returns storageData1.path.child("dalvik-cache", "profiles")
    }

    val storageDalvikProfile2 = mockk<DataArea>().apply {
        every { flags } returns storageData2.flags
        every { type } returns Type.DALVIK_PROFILE
        every { path } returns storageData2.path.child("dalvik-cache", "profiles")
    }

    val storageSdcard1 = mockk<DataArea>().apply {
        every { flags } returns setOf(DataArea.Flag.PRIMARY)
        every { type } returns Type.SDCARD
        every { path } returns LocalPath.build("/storage", "emulated", "0")
    }

    val storageSdcard2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns Type.SDCARD
        every { path } returns LocalPath.build("/storage", "ABCD-EFGH")
    }

    val storageAndroidData1 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard1.flags
        every { type } returns Type.PUBLIC_DATA
        every { path } returns storageSdcard1.path.child("Android", "data")
    }

    val storageAndroidData2 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard2.flags
        every { type } returns Type.PUBLIC_DATA
        every { path } returns storageSdcard2.path.child("Android", "data")
    }

    val storageAndroidObb1 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard1.flags
        every { type } returns Type.PUBLIC_OBB
        every { path } returns storageSdcard1.path.child("Android", "obb")
    }

    val storageAndroidObb2 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard2.flags
        every { type } returns Type.PUBLIC_OBB
        every { path } returns storageSdcard2.path.child("Android", "obb")
    }

    val storageAndroidMedia1 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard1.flags
        every { type } returns Type.PUBLIC_MEDIA
        every { path } returns storageSdcard1.path.child("Android", "media")
    }

    val storageAndroidMedia2 = mockk<DataArea>().apply {
        every { flags } returns storageSdcard2.flags
        every { type } returns Type.PUBLIC_MEDIA
        every { path } returns storageSdcard2.path.child("Android", "media")
    }

    val storageCachePartition = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns Type.DOWNLOAD_CACHE
        every { path } returns LocalPath.build("cache")
    }

    private val candidates = mutableListOf<Candidate>()

    @BeforeEach
    open fun setup() {
        if (!::pkgOps.isInitialized) {
            MockKAnnotations.init(this)
        }

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            BaseFilterTest::class.java.classLoader.getResourceAsStream(arg(0))
        }

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageData1,
                    storageData2,
                    storageDataApp1,
                    storageDataApp2,
                    storageDataAppAsec1,
                    storageDataAppAsec2,
                    storageDataAppPrivate1,
                    storageDataAppPrivate2,
                    storageDataAppLib1,
                    storageDataAppLib2,
                    storageDataSystem1,
                    storageDataSystem2,
                    storageDataSystemCE1,
                    storageDataSystemCE2,
                    storageDataSystemDE1,
                    storageDataSystemDE2,
                    storageDalvikDex1,
                    storageDalvikDex2,
                    storageDalvikProfile1,
                    storageDalvikProfile2,
                    storageSdcard1,
                    storageSdcard2,
                    storageAndroidData1,
                    storageAndroidData2,
                    storageAndroidObb1,
                    storageAndroidObb2,
                    storageAndroidMedia1,
                    storageAndroidMedia2,
                    storageCachePartition,
                )
            )
        )

        coEvery { gatewaySwitch.listFiles(any()) } returns emptyList()
        coEvery { gatewaySwitch.exists(any()) } returns false
        coEvery { gatewaySwitch.canRead(any()) } returns false
        coEvery { gatewaySwitch.lookupFiles(any()) } answers {
            throw ReadException(arg<APath>(0))
        }
        storageEnvironment.apply {
            every { dataDir } returns LocalPath.build("/data")
            every { ourCacheDirs } returns listOf(
                LocalPath.build("/data/data/eu.darken.sdmse/cache")
            )
            every { ourExternalCacheDirs } returns listOf(
                LocalPath.build("/storage/emulated/0/Android/data/eu.darken.sdmse/cache"),
                LocalPath.build("/storage/ABCD-1234/Android/data/eu.darken.sdmse/cache"),
            )
            every { ourCodeCacheDirs } returns listOf(
                LocalPath.build("/data/data/eu.darken.sdmse/code_cache")
            )
        }

        coEvery { pkgOps.viewArchive(any(), any()) } returns null

        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
    }

    @AfterEach
    open fun teardown() {
        candidates.clear()
    }

    val testPkg: String = "test.pkg.name"

    fun addDefaultNegatives() {
        neg(testPkg, Type.SDCARD, "sdcard")
        neg(testPkg, Type.SDCARD, "sdcard", rngString)
        neg(testPkg, Type.SDCARD, "eu.thedarken.sdm", "files")
        neg(testPkg, Type.SDCARD, "eu.thedarken.sdm")
        neg(testPkg, Type.SDCARD, "eu.thedarken.sdm.huawei", "files")
        neg(testPkg, Type.SDCARD, "eu.thedarken.sdm.huawei")
        neg(testPkg, Type.SDCARD, "DCIM")
        neg(testPkg, Type.SDCARD, "DCIM", "Camera")
        neg(testPkg, Type.SDCARD, "Android")
        neg(testPkg, Type.SDCARD, "Android", "data")
        neg(testPkg, Type.SDCARD, "Android", "data", rngString)
        neg(testPkg, Type.SDCARD, "Photos")
        neg(testPkg, Type.SDCARD, "Pictures")
        neg(testPkg, Type.SDCARD, "Camera")
        neg(testPkg, Type.SDCARD, "Music")
        neg(testPkg, Type.PRIVATE_DATA, "data", "data")
        neg(testPkg, Type.DATA, "data")
        neg(testPkg, Type.SYSTEM_APP, "system", "app")
        neg(testPkg, Type.SYSTEM, "system")
        neg(testPkg, Type.DOWNLOAD_CACHE, "cache")
    }

    fun addCandidate(candidate: Candidate) {
        candidates.add(candidate)
    }

    suspend fun confirm(filter: ExpendablesFilter) {
        candidates.isEmpty() shouldBe false

        filter.initialize()

        for (c in candidates) {
            val target: APathLookup<APath> = mockk<APathLookup<APath>>().apply {
                c.lastModified?.let {
                    every { modifiedAt } returns it
                }
            }

            when (c.matchType) {
                Candidate.Type.POSITIVE -> {
                    c.areaTypes.forEach { loc ->
                        c.pkgs.forEach { pkg ->
                            c.prefixFreePaths.forEach { segs ->
                                withClue("Should match $pkg, $loc $segs") {
                                    filter.isExpendable(pkg, target, loc, segs) shouldBe true
                                }
                            }
                        }
                    }
                }
                Candidate.Type.NEGATIVE -> {
                    c.areaTypes.forEach { loc ->
                        c.pkgs.forEach { pkg ->
                            c.prefixFreePaths.forEach { segs ->
                                withClue("Should NOT match $pkg, $loc $segs") {
                                    filter.isExpendable(pkg, target, loc, segs) shouldBe false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class Candidate(
        val matchType: Type,
        val areaTypes: Collection<DataArea.Type>,
        val pkgs: Collection<Pkg.Id>,
        val prefixFreePaths: Collection<Segments>,
        val lastModified: Instant? = null
    ) {
        enum class Type {
            POSITIVE, NEGATIVE
        }

    }

    fun createJsonSieveFactory() = object : JsonBasedSieve.Factory {
        override fun create(assetPath: String): JsonBasedSieve {
            return JsonBasedSieve(
                context = context,
                assetPath = assetPath,
                baseMoshi = SerializationAppModule().moshi(),
            )
        }
    }

    fun createDynamicSieveFactory() = object : DynamicSieve.Factory {
        override fun create(configs: Set<DynamicSieve.MatchConfig>): DynamicSieve {
            return DynamicSieve(
                configs = configs,
            )
        }
    }
}
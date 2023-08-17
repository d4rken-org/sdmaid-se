package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.SAFPathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserHandle2
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
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
abstract class SystemCleanerFilterTest : BaseTest() {

    @MockK lateinit var pkgOps: PkgOps
    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var areaManager: DataAreaManager
    @MockK lateinit var fileForensics: FileForensics
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var storageEnvironment: StorageEnvironment
    @MockK lateinit var systemCleanerSettings: SystemCleanerSettings


    val storageData1 = DataArea(
        flags = setOf(DataArea.Flag.PRIMARY),
        type = Type.DATA,
        path = LocalPath.build("/data"),
        userHandle = UserHandle2(-1)
    )

    val storageData2 = DataArea(
        flags = emptySet(),
        type = Type.DATA,
        path = LocalPath.build("/mnt/expand", rngString),
        userHandle = UserHandle2(-1)
    )

    val storageDataApp1 = storageData1.copy(
        type = Type.APP_APP,
        path = storageData1.path.child("app"),
    )

    val storageDataApp2 = storageData2.copy(
        type = Type.APP_APP,
        path = storageData2.path.child("app"),
    )

    val storageDataAppAsec1 = storageData1.copy(
        type = Type.APP_ASEC,
        path = storageData1.path.child("app-asec"),
    )

    val storageDataAppAsec2 = storageData2.copy(
        type = Type.APP_ASEC,
        path = storageData2.path.child("app-asec"),
    )

    val storageDataAppPrivate1 = storageData1.copy(
        type = Type.APP_APP_PRIVATE,
        path = storageData1.path.child("app-private"),
    )

    val storageDataAppPrivate2 = storageData2.copy(
        type = Type.APP_APP_PRIVATE,
        path = storageData2.path.child("app-private"),
    )

    val storageDataAppLib1 = storageData1.copy(
        type = Type.APP_LIB,
        path = storageData1.path.child("app-lib"),
    )

    val storageDataAppLib2 = storageData2.copy(
        type = Type.APP_LIB,
        path = storageData2.path.child("app-lib"),
    )

    val storageDataSystem1 = storageData1.copy(
        type = Type.DATA_SYSTEM,
        path = storageData1.path.child("system"),
    )

    val storageDataSystem2 = storageData2.copy(
        type = Type.DATA_SYSTEM,
        path = storageData2.path.child("system"),
    )

    val storageDataSystemCE1 = storageData1.copy(
        type = Type.DATA_SYSTEM_CE,
        path = storageData1.path.child("system_ce"),
    )

    val storageDataSystemCE2 = storageData2.copy(
        type = Type.DATA_SYSTEM_CE,
        path = storageData2.path.child("system_ce"),
    )

    val storageDataSystemDE1 = storageData1.copy(
        type = Type.DATA_SYSTEM_DE,
        path = storageData1.path.child("system_de"),
    )

    val storageDataSystemDE2 = storageData2.copy(
        type = Type.DATA_SYSTEM_DE,
        path = storageData2.path.child("system_de"),
    )

    val storageDalvikDex1 = storageData1.copy(
        type = Type.DALVIK_DEX,
        path = storageData1.path.child("dalvik-cache", "arm64"),
    )

    val storageDalvikDex2 = storageData2.copy(
        type = Type.DALVIK_DEX,
        path = storageData2.path.child("dalvik-cache", "arm64"),
    )

    val storageDalvikProfile1 = storageData1.copy(
        type = Type.DALVIK_PROFILE,
        path = storageData1.path.child("dalvik-cache", "profiles"),
    )

    val storageDalvikProfile2 = storageData2.copy(
        type = Type.DALVIK_PROFILE,
        path = storageData2.path.child("dalvik-cache", "profiles"),
    )

    val storageSdcard1 = DataArea(
        flags = setOf(DataArea.Flag.PRIMARY),
        type = Type.SDCARD,
        path = LocalPath.build("/storage", "emulated", "0"),
        userHandle = UserHandle2(0),
    )

    val storageSdcard2 = DataArea(
        flags = emptySet(),
        type = Type.SDCARD,
        path = LocalPath.build("/storage", "ABCD-EFGH"),
        userHandle = UserHandle2(1),
    )

    val storageAndroidData1 = storageSdcard1.copy(
        type = Type.PUBLIC_DATA,
        path = storageSdcard1.path.child("Android", "data"),
    )

    val storageAndroidData2 = storageSdcard2.copy(
        type = Type.PUBLIC_DATA,
        path = storageSdcard2.path.child("Android", "data"),
    )

    val storageAndroidObb1 = storageSdcard1.copy(
        type = Type.PUBLIC_OBB,
        path = storageSdcard1.path.child("Android", "obb"),
    )

    val storageAndroidObb2 = storageSdcard2.copy(
        type = Type.PUBLIC_OBB,
        path = storageSdcard2.path.child("Android", "obb"),
    )

    val storageAndroidMedia1 = storageSdcard1.copy(
        type = Type.PUBLIC_MEDIA,
        path = storageSdcard1.path.child("Android", "media"),
    )

    val storageAndroidMedia2 = storageSdcard2.copy(
        type = Type.PUBLIC_MEDIA,
        path = storageSdcard2.path.child("Android", "media"),
    )

    val storageCachePartition = DataArea(
        flags = emptySet(),
        type = Type.DOWNLOAD_CACHE,
        path = LocalPath.build("cache"),
        userHandle = UserHandle2(-1)
    )

    private val dataAreas = setOf(
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

    private data class TreeKey(
        val segments: Segments,
        val fileType: FileType,
    )

    private val pathTree = mutableMapOf<TreeKey, APathLookup<*>>()
    val positives = mutableListOf<APathLookup<*>>()
    val negatives = mutableListOf<APathLookup<*>>()

    @BeforeEach
    open fun setup() {
        if (!::pkgOps.isInitialized) {
            MockKAnnotations.init(this)
        }

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = dataAreas
            )
        )

        coEvery { gatewaySwitch.listFiles(any()) } returns emptyList()
        coEvery { gatewaySwitch.exists(any()) } returns false
        coEvery { gatewaySwitch.canRead(any()) } returns false
        coEvery { gatewaySwitch.lookupFiles(any()) } answers {
            throw ReadException(arg<APath>(0))
        }
        every { storageEnvironment.dataDir } returns LocalPath.build("/data")

        coEvery { pkgOps.viewArchive(any(), any()) } returns null

        coEvery { pkgRepo.query(any(), any()) } returns emptySet()

        coEvery { fileForensics.identifyArea(any()) } returns null

        dataAreas.forEach { area ->
            val mockedLockup = LocalPathLookup(
                lookedUp = area.path as LocalPath,
                fileType = FileType.DIRECTORY,
                size = 512L,
                modifiedAt = Instant.EPOCH,
                target = null,
            )
            val treeKey = TreeKey(area.path.segments, mockedLockup.fileType)
            pathTree[treeKey] = mockedLockup
            val pathCopy = area.path.child()
            coEvery { gatewaySwitch.lookupFiles(pathCopy) } returns emptyList()
        }
    }

    @AfterEach
    open fun teardown() {
        pkgs.clear()
        positives.clear()
        negatives.clear()
    }

    suspend fun mockDefaults() {
        neg(Type.SDCARD, "DCIM", Flag.Dir)
        neg(Type.SDCARD, "DCIM/Camera", Flag.Dir)
        neg(Type.SDCARD, "Android", Flag.Dir)
        neg(Type.SDCARD, "Photos", Flag.Dir)
        neg(Type.SDCARD, "Pictures", Flag.Dir)
        neg(Type.SDCARD, "Camera", Flag.Dir)
        neg(Type.SDCARD, "Music", Flag.Dir)
    }

    suspend fun confirm(filter: SystemCleanerFilter) {
        withClue("Need at least one positive candidate") {
            positives.isNotEmpty() shouldBe true
        }
        withClue("Need at least one negative candidate") {
            negatives.isNotEmpty() shouldBe true
        }

        filter.initialize()

        withClue("No filter should just match a random file") {
            doMock(Type.SDCARD, UUID.randomUUID().toString(), null, Flag.File).forEach {
                filter.matches(it) shouldBe false
            }
        }

        withClue("No filter should just match a random folder") {
            val randoms = mutableListOf<APathLookup<*>>().apply {
                val randomFolder = UUID.randomUUID().toString()
                addAll(doMock(Type.SDCARD, randomFolder, null, Flag.Dir))
                val randomFile = UUID.randomUUID().toString()
                addAll(doMock(Type.SDCARD, "$randomFolder/$randomFile", null, Flag.File))
            }

            randoms.forEach { filter.matches(it) shouldBe false }
        }

        withClue("No filter should match the root of a data area") {
            dataAreas.forEach { area ->
                val lookup = LocalPathLookup(
                    lookedUp = area.path as LocalPath,
                    fileType = FileType.DIRECTORY,
                    size = 512L,
                    modifiedAt = Instant.EPOCH,
                    target = null,
                )
                filter.matches(lookup) shouldBe false
            }
        }

        positives.forEach { canidate ->
            withClue("Should match ${canidate.path} (${canidate.fileType})") {
                filter.matches(canidate) shouldBe true
            }
            log { "Matched: ${canidate.path}" }
        }
        negatives.forEach { canidate ->
            withClue("Should NOT match ${canidate.path} (${canidate.fileType})") {
                filter.matches(canidate) shouldBe false
            }
            log { "Didn't match: ${canidate.path}" }
        }
    }

    sealed interface Flag {
        object File : Flag
        object Dir : Flag

        sealed interface Area : Flag {
            object Primary : Area
            object Secondary : Area
        }
    }

    suspend fun pos(location: Type, path: String, vararg flags: Flag) {
        val mockedFiles = doMock(location, path, null, *flags)
        positives.addAll(mockedFiles)
    }

    suspend fun neg(location: Type, path: String, vararg flags: Flag) {
        val mockedFiles = doMock(location, path, null, *flags)
        negatives.addAll(mockedFiles)
    }

    suspend fun doMock(
        areaType: Type,
        targetPath: String,
        callback: ((APathLookup<*>) -> Unit)?,
        vararg flags: Flag
    ): Collection<APathLookup<*>> {
        val flagsCollection = listOf(*flags)

        require(areaManager.currentAreas().any { it.type == areaType }) { "Area not mocked: $areaType" }

        return areaManager
            .currentAreas()
            .filter { it.type == areaType }
            .mapNotNull { area ->
                if (flagsCollection.any { it is Flag.Area.Primary } && !area.hasFlags(DataArea.Flag.PRIMARY)) {
                    return@mapNotNull null
                }
                if (flagsCollection.any { it is Flag.Area.Secondary } && area.hasFlags(DataArea.Flag.PRIMARY)) {
                    return@mapNotNull null
                }
                require(!(flagsCollection.contains(Flag.Dir) && flagsCollection.contains(Flag.File))) { "Can't be both file and dir." }

                val mockPath = area.path.child(targetPath)
                val mockLookup = when (area.path.pathType) {
                    APath.PathType.LOCAL -> LocalPathLookup(
                        lookedUp = mockPath as LocalPath,
                        fileType = if (flagsCollection.contains(Flag.Dir)) {
                            FileType.DIRECTORY
                        } else if (flagsCollection.contains(Flag.File)) {
                            FileType.FILE
                        } else {
                            throw IllegalArgumentException("Unknown file type")
                        },
                        size = when {
                            flagsCollection.contains(Flag.Dir) -> 512L
                            else -> 1024 * 1024L
                        },
                        modifiedAt = Instant.EPOCH,
                        target = null,
                    )

                    APath.PathType.SAF -> SAFPathLookup(
                        lookedUp = mockPath as SAFPath,
                        docFile = mockk<SAFDocFile>().apply {
                            every { isDirectory } returns flagsCollection.contains(Flag.Dir)
                            every { isFile } returns flagsCollection.contains(Flag.File)

                            every { length } returns when {
                                flagsCollection.contains(Flag.Dir) -> 512L
                                else -> 1024 * 1024L
                            }
                            every { lastModified } returns Instant.EPOCH
                        },
                    )

                    APath.PathType.RAW -> throw NotImplementedError()
                }

                coEvery { fileForensics.identifyArea(mockPath) } returns mockk<AreaInfo>().apply {
                    every { type } returns areaType
                    every { prefix } returns area.path
                    every { prefixFreeSegments } returns mockPath.segments.drop(prefix.segments.size)
                }

                coEvery { gatewaySwitch.canRead(mockPath) } returns true

                if (flagsCollection.contains(Flag.Dir)) {
                    coEvery { gatewaySwitch.lookupFiles(mockPath) } returns emptyList()
                }

                val treeKey = TreeKey(mockPath.segments, mockLookup.fileType)
                val treeKeyParent = TreeKey(mockPath.segments.dropLast(1), FileType.DIRECTORY)

                pathTree[treeKeyParent]?.let { parent ->
                    val parentsChildren = gatewaySwitch.lookupFiles(parent.lookedUp)
                    coEvery { gatewaySwitch.lookupFiles(parent.lookedUp) } returns parentsChildren + mockLookup
                } ?: throw IllegalArgumentException("$mockPath has no mocked parent")

                pathTree[treeKey]?.let {
                    throw IllegalArgumentException("Duplicate $mockLookup overwrites $it")
                }
                pathTree[treeKey] = mockLookup

                mockLookup
            }
    }

    private val pkgs = mutableMapOf<Pkg.Id, Installed>()
    suspend fun mockPkg(pkgId: Pkg.Id): Installed {
        val installed = mockk<Installed>().apply {
            every { id } returns pkgId
            every { packageName } returns id.name
        }
        pkgs[pkgId] = installed
        coEvery { pkgRepo.query(pkgId, any()) } answers {
            setOf(pkgs[arg(0)]!!)
        }
        return installed
    }

    private val pkgArchives = mutableMapOf<String, ApkInfo>()
    suspend fun mockArchive(pkgId: Pkg.Id, path: APath): ApkInfo {
        val apkInfo = mockk<ApkInfo>().apply {
            every { id } returns pkgId
        }
        pkgArchives[path.path] = apkInfo
        coEvery { pkgOps.viewArchive(any(), any()) } answers {
            pkgArchives[arg<APath>(0).path]
        }
        return apkInfo
    }

}
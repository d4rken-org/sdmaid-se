package eu.darken.sdmse.common.common.clutter

import android.content.Context
import android.content.res.AssetManager
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.clutter.manual.JsonMarkerParser
import eu.darken.sdmse.common.clutter.manual.ManualMarkerSource
import eu.darken.sdmse.common.common.clutter.MarkerSourceTestTool.Candi.MatchType.NEG
import eu.darken.sdmse.common.common.clutter.MarkerSourceTestTool.Candi.MatchType.POS
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.serialization.SerializationModule
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.StorageArea.Type.*
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.*

class MarkerSourceTestTool(private val assetPath: String) {

    private val moshi = SerializationModule().moshi()

    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private val pkgOps: PkgOps = mockk()

    private val testApps = mutableListOf<Installed>()

    private var cachedSource: ManualMarkerSource? = null

    init {
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            File(arg<String>(0)).inputStream()
        }

        coEvery { pkgOps.getInstalledPackages() } returns testApps
    }

    fun getMarkerSource(): MarkerSource {
        cachedSource?.let { return it }

        return ManualMarkerSource(pkgOps) {
            JsonMarkerParser(context, moshi).fromAssets(assetPath)
        }.also {
            cachedSource = it
        }
    }

    fun resetMarkerSource() {
        cachedSource = null
    }

    fun addMockPkg(pkgName: String) {
        mockk<Installed>().apply {
            every { packageName } returns pkgName
        }.run { testApps.add(this) }
        resetMarkerSource()
    }

    suspend fun checkBasics() {
        neg(SDCARD, emptySet(), emptySet(), UUID.randomUUID().toString())
        neg(PUBLIC_DATA, emptySet(), emptySet(), "")
        neg(PUBLIC_DATA, emptySet(), emptySet(), UUID.randomUUID().toString())
        neg(DATA, emptySet(), emptySet(), "")
        neg(DATA, emptySet(), emptySet(), UUID.randomUUID().toString())
        neg(PRIVATE_DATA, emptySet(), emptySet(), "")
        neg(PRIVATE_DATA, emptySet(), emptySet(), UUID.randomUUID().toString())
    }

    suspend fun checkCandidates(candidate: Candi) {
        val matches = getMarkerSource().match(candidate.areaType, candidate.prefixFreePath)
        matches shouldNotBe null

        if (candidate.matchType == NEG) {
            if (candidate.packages.isEmpty()) {
                withClue("Should not have any package matches, but got: $matches") {
                    matches shouldBe emptySet()
                }
            } else {
                for (pkg in candidate.packages) {
                    for (match in matches) {
                        withClue("There should be no match against $pkg, but there was: $matches") {
                            match.packageNames shouldNotContain pkg
                        }
                    }
                }
            }
        } else if (candidate.matchType == POS) {
            if (candidate.packages.isEmpty()) {
                matches shouldHaveAtLeastSize 1
            } else {
                for (pkg in candidate.packages) {
                    withClue("We should have matched $pkg but didn't: $matches") {
                        matches.any { it.packageNames.contains(pkg) } shouldBe true
                    }
                }
            }
            for (flag in candidate.flags) {
                if (candidate.packages.isEmpty()) {
                    withClue("Match didn't have require flag $flag: $matches") {
                        matches.any { it.flags.contains(flag) } shouldBe true
                    }
                } else {
                    for (pkg in candidate.packages) {
                        withClue("Match didn't have require flag $flag for $pkg: $matches") {
                            matches.any { it.packageNames.contains(pkg) && it.flags.contains(flag) } shouldBe true
                        }
                    }
                }
            }
        } else {
            throw IllegalStateException()
        }
    }

    data class Candi(
        val matchType: MatchType,
        val areaType: StorageArea.Type,
        val flags: Collection<Marker.Flag> = emptySet(),
        val packages: Collection<String> = emptySet(),
        val prefixFreePath: String,
    ) {
        enum class MatchType {
            POS, NEG
        }
    }

}
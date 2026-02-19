package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.clutter.Marker.Flag.*
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DebugManualMarkerSourceTest : BaseTest() {

    private var markerTestTool = MarkerSourceTestTool("./src/test/assets/clutter/db_debug_markers.json")

    @BeforeEach
    fun setup() = testEnv {
        markerTestTool.checkBasics()
    }

    @AfterEach
    fun teardown() {
    }

    private fun testEnv(block: suspend MarkerSourceTestTool.() -> Unit) {
        runTest { block(markerTestTool) }
    }

    @Test fun testPathAndRegexCorpse() = testEnv {
        neg(SDCARD, "sdm_test_nested_mixed")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_nested_mixed/sdm_test_mixed_nested_file_corpse")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_nested_mixed/sdm_test_mixed_nested_dir_corpse")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_nested_mixed/sdm_test_mixed_nested_file")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_nested_mixed/sdm_test_mixed_nested_dir")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_blocked_mixed_corpse")
        pos(SDCARD, "eu.thedarken.sdm.test.corpse", "sdm_test_blocked_mixed_corpse/sdm_test_mixed_nested_corpse")
        pos(
            SDCARD,
            "eu.thedarken.sdm.test.corpse",
            "sdm_test_blocked_mixed_corpse/sdm_test_mixed_blocking_child/sdm_test_mixed_cascaded_corpse"
        )
    }

    @Test fun testPathAndRegex() = testEnv {
        neg(SDCARD, "sdm_test_starts_with/sdm_starts_with_test_file")
        pos(SDCARD, "eu.thedarken.sdm.test", "sdm_test_starts_with_XYZ/sdm_starts_with_test_file")
    }

//    @Test fun testContainsAndRegexCorpse() {
//        addDefaultNegatives()
//        addCanidate(neg().loc(SDCARD).path("sdm_test_regex_contains_shouldnt_match_corpse").build())
//        addCanidate(
//            neg().loc(SDCARD).path("sdm_test_regex_contains_shouldnt_match_corpse/sdm_test_regex_contains_corpse")
//                .build()
//        )
//        addCanidate(neg().loc(SDCARD).path("sdm_test_regex_contains_parent_corpse").build())
//        addCanidate(neg().loc(SDCARD).path("sdm_test_regex_contains_corpse").build())
//        addCanidate(
//            pos().pkgs("eu.thedarken.sdm.test.corpse").loc(SDCARD)
//                .path("sdm_test_regex_contains_parent_corpse/sdm_test_regex_contains_corpse").build()
//        )
//        checkCandidates()
//    }
//
//    @Test fun testPathAndContainsAndRegex() {
//        addDefaultNegatives()
//        addCanidate(neg().loc(SDCARD).path("sdm_test_contains/sdm_test_path/sdm_test_regex_contains_corpse").build())
//        addCanidate(
//            neg().loc(SDCARD).path("sdm_test_path/sdm_test_does_not_contain/sdm_test_regex_contains_corpse").build()
//        )
//        addCanidate(
//            neg().loc(SDCARD).path("bad_sdm_test_path/sdm_test_contains/sdm_test_regex_contains_corpse").build()
//        )
//        addCanidate(
//            pos().pkgs("eu.thedarken.sdm.test.corpse").loc(SDCARD)
//                .path("sdm_test_path/sdm_test_contains/sdm_test_regex_contains_corpse").build()
//        )
//        checkCandidates()
//    }

    @Test fun testRegex() = testEnv {
        neg(SDCARD, "sdm_test_file_cache1/sdm_test_file_cache")
        neg(SDCARD, "sdm_test_file_cache")
        pos(SDCARD, "eu.thedarken.sdm.test", "sdm_test_file_cache1")
        pos(SDCARD, "eu.thedarken.sdm.test", "sdm_test_file_cache2")
        neg(SDCARD, "eu.thedarken.sdm.test", "sdm_test_file_cache22")
    }


    @Test fun testSensitivity() = testEnv {
        pos(SDCARD, "case.sensitivity.pkg", "INSENSITIVE")
        pos(SDCARD, "case.sensitivity.pkg", "INSENsITIVE")
        pos(SDCARD, "case.sensitivity.pkg", "insensitivE")
        pos(SDCARD, "case.sensitivity.pkg", "Insensitive")
        neg(SDCARD, "SENSITIVE")
        neg(SDCARD, "sensitive")
        pos(PRIVATE_DATA, "case.sensitivity.pkg", "SENSITIVE")
        pos(PRIVATE_DATA, "case.sensitivity.pkg", "sensitive")
        neg(PRIVATE_DATA, "sENSITIVE")
        neg(PRIVATE_DATA, "SENSITIVe")

        getMarkerSource().getMarkerForPkg("case.sensitivity.pkg".toPkgId()).distinct().size shouldBe 3
    }

    @Test fun `match regex pkgs`() = testEnv {
        neg(SDCARD, "regex.package.test", "RegexPackageTest")
        addMockPkg("regex.package.test")

        neg(SDCARD, "regex.package", "RegexPackageTest")
        pos(SDCARD, "regex.package.test", "RegexPackageTest")
    }

    @Test fun testCandidateSystem_non_specific() = testEnv {
        neg(SDCARD, "Folder")
        pos(SDCARD, emptySet(), emptySet(), "Folder1")
    }

    @Test fun testCandidateSystem_non_specific_flags() = testEnv {
        pos(SDCARD, setOf(CUSTODIAN), emptySet(), "Folder1")
        pos(SDCARD, setOf(COMMON), emptySet(), "Folder2")
        pos(SDCARD, setOf(KEEPER), emptySet(), "Folder3")
    }

    @Test fun testCandidateSystem_specific() = testEnv {
        neg(SDCARD, "unique1", "Folder2")
        pos(SDCARD, "unique1", "Folder1")
    }

    @Test fun testCandidateSystem_specific_flags() = testEnv {
        pos(SDCARD, CUSTODIAN, "unique1", "Folder1")
        pos(SDCARD, COMMON, "unique2", "Folder2")
        pos(SDCARD, KEEPER, "unique3", "Folder3")
    }

    @Test fun testMerging_shared1() = testEnv {
        neg(SDCARD, "Folder")
        pos(SDCARD, "shared1", "Folder1")
        pos(SDCARD, "shared1", "Folder2")
    }

    @Test fun testMerging_shared2() = testEnv {
        neg(SDCARD, "Folder")
        pos(SDCARD, "shared2", "Folder2")
        pos(SDCARD, "shared2", "Folder3")
    }

    @Test fun testMerging_unique1() = testEnv {
        neg(SDCARD, "unique1", "Folder2")
        neg(SDCARD, "unique1", "Folder3")
        pos(SDCARD, "unique1", "Folder1")
    }

    @Test fun testMerging_unique2() = testEnv {
        neg(SDCARD, "unique2", "Folder1")
        neg(SDCARD, "unique2", "Folder3")
        pos(SDCARD, "unique2", "Folder2")
    }

    @Test fun testMerging_unique3() = testEnv {
        neg(SDCARD, "unique3", "Folder1")
        neg(SDCARD, "unique3", "Folder2")
        pos(SDCARD, "unique3", "Folder3")
    }
}
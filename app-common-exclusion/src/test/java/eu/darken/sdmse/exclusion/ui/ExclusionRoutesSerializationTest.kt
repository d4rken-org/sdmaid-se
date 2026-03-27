package eu.darken.sdmse.exclusion.ui

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class ExclusionRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `PathExclusionEditorRoute with null options serialization round-trip`() {
        val original = PathExclusionEditorRoute(exclusionId = "exc-123")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-123"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PathExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `PathExclusionEditorRoute with options serialization round-trip`() {
        val original = PathExclusionEditorRoute(
            exclusionId = "exc-123",
            initial = PathExclusionEditorOptions(
                targetPath = LocalPath.build("/storage/emulated/0/DCIM"),
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-123",
                "initial": {
                    "targetPath": {
                        "file": "/storage/emulated/0/DCIM"
                    }
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PathExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `PkgExclusionEditorRoute with null options serialization round-trip`() {
        val original = PkgExclusionEditorRoute(exclusionId = "exc-456")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-456"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PkgExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `PkgExclusionEditorRoute with options serialization round-trip`() {
        val original = PkgExclusionEditorRoute(
            exclusionId = "exc-456",
            initial = PkgExclusionEditorOptions(
                targetPkgId = Pkg.Id("com.example.excluded"),
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-456",
                "initial": {
                    "targetPkgId": {
                        "name": "com.example.excluded"
                    }
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PkgExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SegmentExclusionEditorRoute with null options serialization round-trip`() {
        val original = SegmentExclusionEditorRoute(exclusionId = "exc-789")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-789"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SegmentExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SegmentExclusionEditorRoute with options serialization round-trip`() {
        val original = SegmentExclusionEditorRoute(
            exclusionId = "exc-789",
            initial = SegmentExclusionEditorOptions(
                targetSegments = listOf("Android", "data", "com.example"),
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "exclusionId": "exc-789",
                "initial": {
                    "targetSegments": [
                        "Android",
                        "data",
                        "com.example"
                    ]
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SegmentExclusionEditorRoute>(serialized)
        deserialized shouldBe original
    }
}

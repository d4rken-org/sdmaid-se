package eu.darken.sdmse.squeezer.core.tasks

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.FailureReason
import eu.darken.sdmse.stats.core.AffectedPath
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SqueezerTasksTest : BaseTest() {

    // === SqueezerScanTask Tests ===

    @Test
    fun `ScanTask default has null paths`() {
        val task = SqueezerScanTask()

        task.paths shouldBe null
    }

    @Test
    fun `ScanTask with custom paths`() {
        val customPaths = setOf(
            LocalPath.build("/storage/emulated/0/DCIM"),
            LocalPath.build("/storage/emulated/0/Pictures"),
        )

        val task = SqueezerScanTask(paths = customPaths)

        task.paths shouldNotBe null
        task.paths?.size shouldBe 2
        task.paths shouldBe customPaths
    }

    @Test
    fun `ScanTask Success stores correct values`() {
        val success = SqueezerScanTask.Success(
            itemCount = 50,
            totalSize = 500_000_000L,
            estimatedSavings = 175_000_000L,
        )

        success.shouldBeInstanceOf<SqueezerScanTask.Result>()
    }

    // === SqueezerProcessTask Tests ===

    @Test
    fun `ProcessTask default mode is All`() {
        val task = SqueezerProcessTask()

        task.mode.shouldBeInstanceOf<SqueezerProcessTask.TargetMode.All>()
        task.qualityOverride shouldBe null
    }

    @Test
    fun `ProcessTask with quality override`() {
        val task = SqueezerProcessTask(qualityOverride = 60)

        task.qualityOverride shouldBe 60
    }

    @Test
    fun `ProcessTask TargetMode All has unique UUID`() {
        val mode1 = SqueezerProcessTask.TargetMode.All()
        val mode2 = SqueezerProcessTask.TargetMode.All()

        mode1.id shouldNotBe mode2.id
    }

    @Test
    fun `ProcessTask TargetMode Selected with targets`() {
        val targets = setOf(
            CompressibleMedia.Id("/img1.jpg"),
            CompressibleMedia.Id("/img2.jpg"),
        )

        val mode = SqueezerProcessTask.TargetMode.Selected(targets = targets)

        mode.targets.size shouldBe 2
        mode.targets shouldContainAll targets
    }

    @Test
    fun `ProcessTask TargetMode Selected with empty targets`() {
        val mode = SqueezerProcessTask.TargetMode.Selected(targets = emptySet())

        mode.targets.shouldBeEmpty()
    }

    @Test
    fun `ProcessTask Success implements AffectedSpace`() {
        val success = SqueezerProcessTask.Success(
            affectedSpace = 3_500_000L,
            affectedPaths = setOf(LocalPath.build("/img1.jpg")),
            processedCount = 1,
        )

        success.affectedSpace shouldBe 3_500_000L
    }

    @Test
    fun `ProcessTask Success implements AffectedPaths`() {
        val paths = setOf(
            LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"),
            LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_002.jpg"),
        )

        val success = SqueezerProcessTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = paths,
            processedCount = 2,
        )

        success.affectedPaths.size shouldBe 2
        success.affectedPaths shouldContainAll paths
    }

    @Test
    fun `ProcessTask Success splits metadata aborts out of the generic failed count`() {
        // A metadata-unpreservable file counts in failedCount; it must be reported once, not
        // both as "failed" and as "metadata kept unchanged".
        val metadataOnly = SqueezerProcessTask.Success(
            affectedSpace = 0L,
            affectedPaths = emptySet(),
            processedCount = 0,
            failedCount = 1,
            failureReasons = mapOf(FailureReason.METADATA_UNPRESERVABLE to 1),
        )
        metadataOnly.metadataUnpreservableCount shouldBe 1
        metadataOnly.genericFailedCount shouldBe 0

        val mixed = SqueezerProcessTask.Success(
            affectedSpace = 0L,
            affectedPaths = emptySet(),
            processedCount = 0,
            failedCount = 3,
            failureReasons = mapOf(
                FailureReason.METADATA_UNPRESERVABLE to 1,
                FailureReason.IO_ERROR to 2,
            ),
        )
        mixed.metadataUnpreservableCount shouldBe 1
        mixed.genericFailedCount shouldBe 2

        val genericOnly = SqueezerProcessTask.Success(
            affectedSpace = 0L,
            affectedPaths = emptySet(),
            processedCount = 0,
            failedCount = 2,
            failureReasons = mapOf(FailureReason.IO_ERROR to 2),
        )
        genericOnly.metadataUnpreservableCount shouldBe 0
        genericOnly.genericFailedCount shouldBe 2
    }

    @Test
    fun `ProcessTask Success action is COMPRESSED`() {
        val success = SqueezerProcessTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = emptySet(),
            processedCount = 0,
        )

        success.action shouldBe AffectedPath.Action.COMPRESSED
    }

    // === Task Type Tests ===

    @Test
    fun `all tasks implement SqueezerTask interface`() {
        val scanTask: SqueezerTask = SqueezerScanTask()
        val processTask: SqueezerTask = SqueezerProcessTask()

        scanTask.shouldBeInstanceOf<SqueezerTask>()
        processTask.shouldBeInstanceOf<SqueezerTask>()
    }

    @Test
    fun `ProcessTask is Reportable`() {
        val processTask = SqueezerProcessTask()

        // ProcessTask implements Reportable for stats
        processTask.shouldBeInstanceOf<eu.darken.sdmse.stats.core.Reportable>()
    }

    @Test
    fun `quality override values - minimum`() {
        val task = SqueezerProcessTask(qualityOverride = 1)

        task.qualityOverride shouldBe 1
    }

    @Test
    fun `quality override values - maximum`() {
        val task = SqueezerProcessTask(qualityOverride = 100)

        task.qualityOverride shouldBe 100
    }
}

package eu.darken.sdmse.compressor.core.tasks

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.compressor.core.CompressibleMedia
import eu.darken.sdmse.stats.core.AffectedPath
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CompressorTasksTest : BaseTest() {

    // === CompressorScanTask Tests ===

    @Test
    fun `ScanTask default has null paths`() {
        val task = CompressorScanTask()

        task.paths shouldBe null
    }

    @Test
    fun `ScanTask with custom paths`() {
        val customPaths = setOf(
            LocalPath.build("/storage/emulated/0/DCIM"),
            LocalPath.build("/storage/emulated/0/Pictures"),
        )

        val task = CompressorScanTask(paths = customPaths)

        task.paths shouldNotBe null
        task.paths?.size shouldBe 2
        task.paths shouldBe customPaths
    }

    @Test
    fun `ScanTask Success stores correct values`() {
        val success = CompressorScanTask.Success(
            itemCount = 50,
            totalSize = 500_000_000L,
            estimatedSavings = 175_000_000L,
        )

        success.shouldBeInstanceOf<CompressorScanTask.Result>()
    }

    // === CompressorProcessTask Tests ===

    @Test
    fun `ProcessTask default mode is All`() {
        val task = CompressorProcessTask()

        task.mode.shouldBeInstanceOf<CompressorProcessTask.TargetMode.All>()
        task.qualityOverride shouldBe null
    }

    @Test
    fun `ProcessTask with quality override`() {
        val task = CompressorProcessTask(qualityOverride = 60)

        task.qualityOverride shouldBe 60
    }

    @Test
    fun `ProcessTask TargetMode All has unique UUID`() {
        val mode1 = CompressorProcessTask.TargetMode.All()
        val mode2 = CompressorProcessTask.TargetMode.All()

        mode1.id shouldNotBe mode2.id
    }

    @Test
    fun `ProcessTask TargetMode Selected with targets`() {
        val targets = setOf(
            CompressibleMedia.Id("/img1.jpg"),
            CompressibleMedia.Id("/img2.jpg"),
        )

        val mode = CompressorProcessTask.TargetMode.Selected(targets = targets)

        mode.targets.size shouldBe 2
        mode.targets shouldContainAll targets
    }

    @Test
    fun `ProcessTask TargetMode Selected with empty targets`() {
        val mode = CompressorProcessTask.TargetMode.Selected(targets = emptySet())

        mode.targets.shouldBeEmpty()
    }

    @Test
    fun `ProcessTask Success implements AffectedSpace`() {
        val success = CompressorProcessTask.Success(
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

        val success = CompressorProcessTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = paths,
            processedCount = 2,
        )

        success.affectedPaths.size shouldBe 2
        success.affectedPaths shouldContainAll paths
    }

    @Test
    fun `ProcessTask Success action is COMPRESSED`() {
        val success = CompressorProcessTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = emptySet(),
            processedCount = 0,
        )

        success.action shouldBe AffectedPath.Action.COMPRESSED
    }

    // === CompressorOneClickTask Tests ===

    @Test
    fun `OneClickTask default noop is true`() {
        val task = CompressorOneClickTask()

        task.noop shouldBe true
    }

    @Test
    fun `OneClickTask Success implements AffectedSpace`() {
        val success = CompressorOneClickTask.Success(
            affectedSpace = 5_000_000L,
            affectedPaths = emptySet(),
            processedCount = 10,
        )

        success.affectedSpace shouldBe 5_000_000L
    }

    @Test
    fun `OneClickTask Success implements AffectedPaths`() {
        val paths = setOf(
            LocalPath.build("/img1.jpg"),
            LocalPath.build("/img2.jpg"),
            LocalPath.build("/img3.jpg"),
        )

        val success = CompressorOneClickTask.Success(
            affectedSpace = 1_500_000L,
            affectedPaths = paths,
            processedCount = 3,
        )

        success.affectedPaths.size shouldBe 3
        success.affectedPaths shouldContainAll paths
    }

    @Test
    fun `OneClickTask Success action is COMPRESSED`() {
        val success = CompressorOneClickTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = emptySet(),
            processedCount = 0,
        )

        success.action shouldBe AffectedPath.Action.COMPRESSED
    }

    @Test
    fun `OneClickTask Success processedCount is correct`() {
        val success = CompressorOneClickTask.Success(
            affectedSpace = 10_000_000L,
            affectedPaths = emptySet(),
            processedCount = 25,
        )

        success.processedCount shouldBe 25
    }

    // === CompressorSchedulerTask Tests ===

    @Test
    fun `SchedulerTask with scheduleId`() {
        val task = CompressorSchedulerTask(scheduleId = "schedule-123")

        task.scheduleId shouldBe "schedule-123"
    }

    @Test
    fun `SchedulerTask with different scheduleId`() {
        val task = CompressorSchedulerTask(scheduleId = "daily-compression")

        task.scheduleId shouldBe "daily-compression"
    }

    @Test
    fun `SchedulerTask Success implements AffectedSpace`() {
        val success = CompressorSchedulerTask.Success(
            affectedSpace = 50_000_000L,
            affectedPaths = emptySet(),
            processedCount = 100,
        )

        success.affectedSpace shouldBe 50_000_000L
    }

    @Test
    fun `SchedulerTask Success action is COMPRESSED`() {
        val success = CompressorSchedulerTask.Success(
            affectedSpace = 1_000_000L,
            affectedPaths = emptySet(),
            processedCount = 0,
        )

        success.action shouldBe AffectedPath.Action.COMPRESSED
    }

    // === Task Type Tests ===

    @Test
    fun `all tasks implement CompressorTask interface`() {
        val scanTask: CompressorTask = CompressorScanTask()
        val processTask: CompressorTask = CompressorProcessTask()
        val oneClickTask: CompressorTask = CompressorOneClickTask()
        val schedulerTask: CompressorTask = CompressorSchedulerTask(scheduleId = "test-schedule")

        scanTask.shouldBeInstanceOf<CompressorTask>()
        processTask.shouldBeInstanceOf<CompressorTask>()
        oneClickTask.shouldBeInstanceOf<CompressorTask>()
        schedulerTask.shouldBeInstanceOf<CompressorTask>()
    }

    @Test
    fun `ProcessTask and OneClickTask are Reportable`() {
        val processTask = CompressorProcessTask()
        val oneClickTask = CompressorOneClickTask()
        val schedulerTask = CompressorSchedulerTask(scheduleId = "test-schedule")

        // These implement Reportable for stats
        processTask.shouldBeInstanceOf<eu.darken.sdmse.stats.core.Reportable>()
        oneClickTask.shouldBeInstanceOf<eu.darken.sdmse.stats.core.Reportable>()
        schedulerTask.shouldBeInstanceOf<eu.darken.sdmse.stats.core.Reportable>()
    }

    @Test
    fun `quality override values - minimum`() {
        val task = CompressorProcessTask(qualityOverride = 1)

        task.qualityOverride shouldBe 1
    }

    @Test
    fun `quality override values - maximum`() {
        val task = CompressorProcessTask(qualityOverride = 100)

        task.qualityOverride shouldBe 100
    }
}

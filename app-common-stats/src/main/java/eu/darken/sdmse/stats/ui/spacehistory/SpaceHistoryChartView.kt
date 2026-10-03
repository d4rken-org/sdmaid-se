package eu.darken.sdmse.stats.ui.spacehistory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.spToPx
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

class SpaceHistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    fun interface OnMarkerTapListener {
        fun onMarkerTapped(report: ReportEntity, screenX: Int, screenY: Int)
    }

    private data class MarkerPosition(
        val report: ReportEntity,
        val point: PointF,
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(1f).toFloat()
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(1.5f).toFloat()
    }

    private val selectedMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dateFormatter = Locale.getDefault().let {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(it, "MMMd"), it)
    }
    private val dateYearFormatter = Locale.getDefault().let {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(it, "yMMMd"), it)
    }

    private var snapshots: List<SpaceSnapshotEntity> = emptyList()
    private var times: LongArray = LongArray(0)
    private var used: LongArray = LongArray(0)
    private var minUsed: Long = 0L
    private var maxUsed: Long = 0L
    private var reports: List<ReportEntity> = emptyList()
    var isCompact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateTextSizes()
            invalidate()
        }

    private var markerPositions: List<MarkerPosition> = emptyList()
    private var selectedMarkerIndex: Int = -1
    private var markerTapListener: OnMarkerTapListener? = null
    private val hitRadiusPx = context.dpToPx(16f).toFloat()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.SpaceHistoryChartView).apply {
            try {
                isCompact = getBoolean(R.styleable.SpaceHistoryChartView_isCompact, false)
            } finally {
                recycle()
            }
        }
        updateColors()
        updateTextSizes()
    }

    fun setOnMarkerTapListener(listener: OnMarkerTapListener?) {
        markerTapListener = listener
    }

    fun setData(snapshots: List<SpaceSnapshotEntity>) {
        val sorted = snapshots.sortedBy { it.recordedAt }
        this.snapshots = sorted
        times = LongArray(sorted.size) { sorted[it].recordedAt.toEpochMilli() }
        used = LongArray(sorted.size) { sorted[it].spaceCapacity - sorted[it].spaceFree }
        minUsed = used.minOrNull() ?: 0L
        maxUsed = used.maxOrNull() ?: 0L
        selectedMarkerIndex = -1
        invalidate()
    }

    fun setReports(reports: List<ReportEntity>) {
        this.reports = reports
        selectedMarkerIndex = -1
        invalidate()
    }

    fun clearSelection() {
        if (selectedMarkerIndex != -1) {
            selectedMarkerIndex = -1
            invalidate()
        }
    }

    private var pendingTapMarkerIndex: Int = -1

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCompact || markerTapListener == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitIndex = findHitMarker(event.x, event.y)
                if (hitIndex >= 0) {
                    pendingTapMarkerIndex = hitIndex
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val downIndex = pendingTapMarkerIndex
                pendingTapMarkerIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                if (downIndex >= 0 && downIndex < markerPositions.size) {
                    val hitIndex = findHitMarker(event.x, event.y)
                    if (hitIndex == downIndex) {
                        selectedMarkerIndex = hitIndex
                        invalidate()
                        val marker = markerPositions[hitIndex]
                        val location = IntArray(2)
                        getLocationOnScreen(location)
                        markerTapListener?.onMarkerTapped(
                            marker.report,
                            location[0] + marker.point.x.toInt(),
                            location[1] + marker.point.y.toInt(),
                        )
                        return true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pendingTapMarkerIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun findHitMarker(touchX: Float, touchY: Float): Int {
        var bestIndex = -1
        var bestDist = Float.MAX_VALUE
        for ((index, marker) in markerPositions.withIndex()) {
            val dx = touchX - marker.point.x
            val dy = touchY - marker.point.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= hitRadiusPx && dist < bestDist) {
                bestDist = dist
                bestIndex = index
            }
        }
        return bestIndex
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (snapshots.isEmpty()) return

        val yLabelWidth = if (isCompact) context.dpToPx(48f).toFloat() else context.dpToPx(60f).toFloat()
        val xLabelHeight = if (isCompact) context.dpToPx(18f).toFloat() else context.dpToPx(22f).toFloat()
        val topPadding = if (isCompact) context.dpToPx(4f).toFloat() else context.dpToPx(8f).toFloat()

        val chartLeft = paddingLeft + yLabelWidth
        val chartTop = paddingTop + topPadding
        val chartRight = width - paddingRight.toFloat()
        val chartBottom = height - paddingBottom.toFloat() - xLabelHeight

        if (chartRight <= chartLeft || chartBottom <= chartTop) return

        val minTime = times.first()
        val maxTime = max(times.last(), minTime + 1L)

        if (!isCompact) {
            drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom)
        }

        val xs = FloatArray(times.size) { pointX(times[it], it, minTime, maxTime, chartLeft, chartRight) }
        val kept = SpaceHistoryChartMath.downsampleIndices(xs, used)

        val linePath = Path()
        val fillPath = Path()
        kept.forEachIndexed { keptIndex, index ->
            val x = xs[index]
            val y = pointY(used[index].toDouble(), chartTop, chartBottom)
            if (keptIndex == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val endX = xs[kept.last()]
        fillPath.lineTo(endX, chartBottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        drawMarkers(canvas, chartLeft, chartTop, chartRight, chartBottom, minTime, maxTime)

        if (isCompact) {
            drawCompactLabels(canvas, chartLeft, chartTop, chartRight, chartBottom)
        } else {
            drawLabels(canvas, chartLeft, chartTop, chartRight, chartBottom)
        }
    }

    private fun pointX(
        time: Long,
        index: Int,
        minTime: Long,
        maxTime: Long,
        chartLeft: Float,
        chartRight: Float,
    ): Float {
        val width = chartRight - chartLeft
        return if (minTime == maxTime) {
            val fraction = if (snapshots.size <= 1) 0f else index.toFloat() / (snapshots.size - 1).toFloat()
            chartLeft + (fraction * width)
        } else {
            val fraction = (time - minTime).toFloat() / (maxTime - minTime).toFloat()
            chartLeft + (fraction * width)
        }
    }

    private fun pointY(
        value: Double,
        chartTop: Float,
        chartBottom: Float,
    ): Float {
        val height = chartBottom - chartTop
        val fraction = SpaceHistoryChartMath.yFraction(value, minUsed, maxUsed - minUsed)
        return chartBottom - (fraction * height)
    }

    private fun formatDates(first: Instant, last: Instant): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val start = first.atZone(zone)
        val end = last.atZone(zone)
        val includeYear = SpaceHistoryChartMath.includeYear(start.toLocalDate(), end.toLocalDate(), LocalDate.now(zone))
        val formatter = if (includeYear) dateYearFormatter else dateFormatter
        return start.format(formatter) to end.format(formatter)
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val lines = 3
        repeat(lines + 1) { index ->
            val y = top + ((bottom - top) / lines.toFloat() * index)
            canvas.drawLine(left, y, right, y, gridPaint)
        }
    }

    private fun drawLabels(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val midValue = minUsed + (maxUsed - minUsed) / 2
        val labelX = left - context.dpToPx(8f)

        canvas.drawText(formatBytes(maxUsed), labelX.toFloat(), top + labelPaint.textSize, labelPaint)
        canvas.drawText(
            formatBytes(midValue),
            labelX.toFloat(),
            top + ((bottom - top) / 2f) + (labelPaint.textSize / 2f),
            labelPaint,
        )
        canvas.drawText(formatBytes(minUsed), labelX.toFloat(), bottom, labelPaint)

        val (start, end) = formatDates(snapshots.first().recordedAt, snapshots.last().recordedAt)
        val xLabelY = bottom + context.dpToPx(16f)

        xLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(start, left, xLabelY.toFloat(), xLabelPaint)

        xLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(end, right, xLabelY.toFloat(), xLabelPaint)
    }

    private fun drawCompactLabels(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val labelX = left - context.dpToPx(4f)

        canvas.drawText(formatBytes(maxUsed), labelX.toFloat(), top + labelPaint.textSize, labelPaint)
        canvas.drawText(formatBytes(minUsed), labelX.toFloat(), bottom, labelPaint)

        val (start, end) = formatDates(snapshots.first().recordedAt, snapshots.last().recordedAt)
        val xLabelY = bottom + context.dpToPx(12f)

        xLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(start, left, xLabelY.toFloat(), xLabelPaint)

        xLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(end, right, xLabelY.toFloat(), xLabelPaint)
    }

    private fun drawMarkers(
        canvas: Canvas,
        chartLeft: Float,
        chartTop: Float,
        chartRight: Float,
        chartBottom: Float,
        minTime: Long,
        maxTime: Long,
    ) {
        if (reports.isEmpty() || snapshots.size < 2) {
            markerPositions = emptyList()
            return
        }

        val markerRadius = if (isCompact) context.dpToPx(3f).toFloat() else context.dpToPx(4f).toFloat()
        val selectedRadius = markerRadius * 1.5f
        val chartWidth = chartRight - chartLeft

        val positions = mutableListOf<MarkerPosition>()

        for (report in reports) {
            val reportTime = report.endAt.toEpochMilli()
            if (reportTime < minTime || reportTime > maxTime) continue

            val fraction = (reportTime - minTime).toFloat() / (maxTime - minTime).toFloat()
            val x = chartLeft + fraction * chartWidth
            val usedValue = SpaceHistoryChartMath.interpolateUsed(times, used, reportTime) ?: continue
            val y = pointY(usedValue, chartTop, chartBottom)

            positions.add(MarkerPosition(report, PointF(x, y)))
        }

        markerPositions = positions

        for ((posIndex, marker) in positions.withIndex()) {
            val isSelected = posIndex == selectedMarkerIndex
            if (isSelected) {
                canvas.drawCircle(marker.point.x, marker.point.y, selectedRadius, selectedMarkerPaint)
                canvas.drawCircle(marker.point.x, marker.point.y, selectedRadius, markerStrokePaint)
            } else {
                canvas.drawCircle(marker.point.x, marker.point.y, markerRadius, markerPaint)
                canvas.drawCircle(marker.point.x, marker.point.y, markerRadius, markerStrokePaint)
            }
        }
    }

    private fun formatBytes(value: Long): String = ByteFormatter.formatSize(context, value).first

    private fun updateTextSizes() {
        val textSize = if (isCompact) context.spToPx(11f) else context.spToPx(12f)
        labelPaint.textSize = textSize
        xLabelPaint.textSize = textSize
    }

    fun setLabelColor(@androidx.annotation.ColorInt color: Int) {
        labelPaint.color = color
        xLabelPaint.color = color
        invalidate()
    }

    private fun updateColors() {
        val primary = context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
        val surfaceVariant = context.getColorForAttr(com.google.android.material.R.attr.colorSurfaceVariant)
        val textSecondary = context.getColorForAttr(android.R.attr.textColorSecondary)
        val tertiary = context.getColorForAttr(com.google.android.material.R.attr.colorTertiary)
        val surface = context.getColorForAttr(com.google.android.material.R.attr.colorSurface)

        linePaint.color = primary
        fillPaint.color = (primary and 0x00FFFFFF) or 0x33000000
        gridPaint.color = surfaceVariant
        labelPaint.color = textSecondary
        xLabelPaint.color = textSecondary
        markerPaint.color = tertiary
        markerStrokePaint.color = surface
        selectedMarkerPaint.color = (tertiary and 0x00FFFFFF) or 0xCC000000.toInt()
    }
}

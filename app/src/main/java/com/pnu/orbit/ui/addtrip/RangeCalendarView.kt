package com.pnu.orbit.ui.addtrip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.pnu.orbit.R
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

class RangeCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var onDateClicked: ((Long) -> Unit)? = null

    private val calendar = Calendar.getInstance(Locale.US)
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textAlign = Paint.Align.CENTER
        textSize = 14f.sp
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orbit_text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = 12f.sp
    }
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black)
        textAlign = Paint.Align.CENTER
        textSize = 14f.sp
        isFakeBoldText = true
    }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orbit_surface_light)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orbit_accent)
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f.dp
        color = ContextCompat.getColor(context, R.color.orbit_primary)
    }

    private var displayedMonthMillis = startOfMonth(System.currentTimeMillis())
    private var startDateMillis: Long? = null
    private var endDateMillis: Long? = null

    fun setMonth(monthStartMillis: Long) {
        displayedMonthMillis = startOfMonth(monthStartMillis)
        invalidate()
    }

    fun setRange(startMillis: Long?, endMillis: Long?) {
        startDateMillis = startMillis?.let(::startOfDay)
        endDateMillis = endMillis?.let(::startOfDay)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawWeekdays(canvas)
        drawDays(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val day = dayFromTouch(event.x, event.y) ?: return true
        onDateClicked?.invoke(dateForDay(day))
        return true
    }

    private fun drawWeekdays(canvas: Canvas) {
        val cellWidth = width / DAYS_IN_WEEK.toFloat()
        val baseline = 23f.dp
        WEEKDAYS.forEachIndexed { index, label ->
            val x = cellWidth * index + cellWidth / 2f
            canvas.drawText(label, x, baseline, mutedPaint)
        }
    }

    private fun drawDays(canvas: Canvas) {
        val cellWidth = width / DAYS_IN_WEEK.toFloat()
        val weekdayHeight = 34f.dp
        val cellHeight = (height - weekdayHeight) / ROW_COUNT.toFloat()
        val firstOffset = firstDayOffset()
        val daysInMonth = daysInMonth()
        val today = startOfDay(System.currentTimeMillis())

        for (day in 1..daysInMonth) {
            val cellIndex = firstOffset + day - 1
            val row = cellIndex / DAYS_IN_WEEK
            val column = cellIndex % DAYS_IN_WEEK
            val left = column * cellWidth
            val top = weekdayHeight + row * cellHeight
            val centerX = left + cellWidth / 2f
            val centerY = top + cellHeight / 2f
            val dateMillis = dateForDay(day)
            val inRange = isInSelectedRange(dateMillis)
            val isStartOrEnd = isSameDay(dateMillis, startDateMillis) ||
                isSameDay(dateMillis, endDateMillis)

            if (inRange) {
                val rangeRect = RectF(
                    left + 4f.dp,
                    top + 5f.dp,
                    left + cellWidth - 4f.dp,
                    top + cellHeight - 5f.dp,
                )
                canvas.drawRoundRect(rangeRect, 16f.dp, 16f.dp, rangePaint)
            }

            if (isSameDay(dateMillis, today)) {
                canvas.drawCircle(centerX, centerY, 15f.dp, todayPaint)
            }

            if (isStartOrEnd) {
                canvas.drawCircle(centerX, centerY, 17f.dp, selectedPaint)
            }

            val textPaint = if (isStartOrEnd) selectedTextPaint else dayPaint
            canvas.drawText(
                day.toString(),
                centerX,
                centerY - (textPaint.descent() + textPaint.ascent()) / 2f,
                textPaint,
            )
        }
    }

    private fun dayFromTouch(x: Float, y: Float): Int? {
        val weekdayHeight = 34f.dp
        if (y < weekdayHeight || width <= 0 || height <= weekdayHeight) return null

        val cellWidth = width / DAYS_IN_WEEK.toFloat()
        val cellHeight = (height - weekdayHeight) / ROW_COUNT.toFloat()
        val column = floor(x / cellWidth).toInt().coerceIn(0, DAYS_IN_WEEK - 1)
        val row = floor((y - weekdayHeight) / cellHeight).toInt().coerceIn(0, ROW_COUNT - 1)
        val day = row * DAYS_IN_WEEK + column - firstDayOffset() + 1
        return day.takeIf { it in 1..daysInMonth() }
    }

    private fun isInSelectedRange(dateMillis: Long): Boolean {
        val start = startDateMillis ?: return false
        val end = endDateMillis ?: return false
        return dateMillis in minOf(start, end)..maxOf(start, end)
    }

    private fun isSameDay(dateMillis: Long, otherMillis: Long?): Boolean =
        otherMillis != null && startOfDay(dateMillis) == startOfDay(otherMillis)

    private fun firstDayOffset(): Int {
        calendar.timeInMillis = displayedMonthMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    private fun daysInMonth(): Int {
        calendar.timeInMillis = displayedMonthMillis
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun dateForDay(day: Int): Long {
        calendar.timeInMillis = displayedMonthMillis
        calendar.set(Calendar.DAY_OF_MONTH, day)
        return startOfDay(calendar.timeInMillis)
    }

    private fun startOfMonth(millis: Long): Long {
        calendar.timeInMillis = millis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startOfDay(millis: Long): Long {
        calendar.timeInMillis = millis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity

    companion object {
        private const val DAYS_IN_WEEK = 7
        private const val ROW_COUNT = 6
        private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
    }
}

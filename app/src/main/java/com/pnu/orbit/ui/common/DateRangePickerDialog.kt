package com.pnu.orbit.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.pnu.orbit.R
import com.pnu.orbit.ui.addtrip.RangeCalendarView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/**
 * Shows a single calendar dialog for picking a travel date range: tapping a date sets the start,
 * tapping again sets the end (tapping a third time starts a new range) - the same range-select
 * gesture used across the app's calendars. [onConfirm] receives the final start/end as
 * start-of-day epoch millis (end defaults to start when only one date was tapped).
 */
fun showDateRangePickerDialog(
    context: Context,
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit,
) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_date_range_picker, null)
    val calendarView = dialogView.findViewById<RangeCalendarView>(R.id.rangeCalendarView)
    val monthTitle = dialogView.findViewById<TextView>(R.id.calendarMonthTitle)
    val rangeText = dialogView.findViewById<TextView>(R.id.dateRangeText)

    var dialogStartMillis = initialStartMillis
    var dialogEndMillis = initialEndMillis
    var displayedMonth = startOfMonthMillis(initialStartMillis ?: System.currentTimeMillis())

    fun refresh() {
        calendarView.setMonth(displayedMonth)
        calendarView.setRange(dialogStartMillis, dialogEndMillis)
        monthTitle.text = millisToLocalDate(displayedMonth).format(monthFormatter)
        val start = dialogStartMillis
        val end = dialogEndMillis
        rangeText.text = when {
            start == null -> "Tap a date to select the start"
            end == null -> "Start: ${millisToLocalDate(start)} — tap the end date"
            else -> "${millisToLocalDate(minOf(start, end))} -> ${millisToLocalDate(maxOf(start, end))}"
        }
    }

    calendarView.onDateClicked = { clickedMillis ->
        val start = dialogStartMillis
        val end = dialogEndMillis
        when {
            start == null || end != null -> {
                dialogStartMillis = clickedMillis
                dialogEndMillis = null
            }
            clickedMillis < start -> {
                dialogStartMillis = clickedMillis
                dialogEndMillis = start
            }
            else -> dialogEndMillis = clickedMillis
        }
        refresh()
    }
    dialogView.findViewById<MaterialButton>(R.id.buttonPreviousMonth).setOnClickListener {
        displayedMonth = shiftMonth(displayedMonth, -1)
        refresh()
    }
    dialogView.findViewById<MaterialButton>(R.id.buttonNextMonth).setOnClickListener {
        displayedMonth = shiftMonth(displayedMonth, 1)
        refresh()
    }

    refresh()

    val dialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .create()

    dialogView.findViewById<MaterialButton>(R.id.buttonCancelRange).setOnClickListener {
        dialog.dismiss()
    }
    dialogView.findViewById<MaterialButton>(R.id.buttonConfirmRange).setOnClickListener {
        val start = dialogStartMillis
        if (start == null) {
            Toast.makeText(context, "Select a start date.", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val end = dialogEndMillis ?: start
        onConfirm(minOf(start, end), maxOf(start, end))
        dialog.dismiss()
    }
    dialog.show()
}

private fun startOfMonthMillis(millis: Long): Long =
    millisToLocalDate(millis).withDayOfMonth(1).toStartOfDayMillis()

private fun shiftMonth(millis: Long, delta: Int): Long =
    millisToLocalDate(millis).plusMonths(delta.toLong()).toStartOfDayMillis()

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.toStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

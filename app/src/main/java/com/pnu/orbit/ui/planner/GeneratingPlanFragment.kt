package com.pnu.orbit.ui.planner

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.PlannerAccommodation
import com.pnu.orbit.domain.model.PlannerPlace
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.addtrip.PlaceSearchActivity
import com.pnu.orbit.ui.common.UiState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class GeneratingPlanFragment : Fragment() {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })

    private lateinit var layoutInputs: View
    private lateinit var layoutLoading: View
    private lateinit var txtLoadingEmoji: TextView
    private lateinit var txtLoading: TextView
    private lateinit var txtError: TextView
    private lateinit var btnStartDate: MaterialButton
    private lateinit var btnEndDate: MaterialButton
    private lateinit var regionContainer: LinearLayout
    private lateinit var lodgingContainer: LinearLayout
    private lateinit var chipGroupStyle: ChipGroup
    private lateinit var chipGroupTransport: ChipGroup
    private lateinit var btnArrivalTime: MaterialButton
    private lateinit var btnDepartureTime: MaterialButton
    private lateinit var btnGeneratePlan: MaterialButton

    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var arrivalTime: String? = null
    private var departureTime: String? = null
    private val regions = mutableListOf<PlannerPlace>()
    private val lodgingsByDay = mutableMapOf<Int, PlannerPlace>()
    private var pendingPlaceTarget: PlaceTarget = PlaceTarget.Region
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var loadingDotCount = 0
    private var loadingActive = false

    private val placeSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult
        val name = result.data?.getStringExtra(PlaceSearchActivity.EXTRA_PLACE_NAME) ?: return@registerForActivityResult
        val lat = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LAT, 0.0)
        val lng = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LNG, 0.0)
        val place = PlannerPlace(name = name, latitude = lat, longitude = lng)

        when (val target = pendingPlaceTarget) {
            PlaceTarget.Region -> {
                regions.add(place)
                renderRegions()
                renderLodgingRows()
            }
            is PlaceTarget.Lodging -> {
                lodgingsByDay[target.day] = place
                renderLodgingRows()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_generating_plan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutInputs = view.findViewById(R.id.layoutInputs)
        layoutLoading = view.findViewById(R.id.layoutLoading)
        txtLoadingEmoji = view.findViewById(R.id.txtLoadingEmoji)
        txtLoading = view.findViewById(R.id.txtLoading)
        txtError = view.findViewById(R.id.txtError)
        btnStartDate = view.findViewById(R.id.btnStartDate)
        btnEndDate = view.findViewById(R.id.btnEndDate)
        regionContainer = view.findViewById(R.id.regionContainer)
        lodgingContainer = view.findViewById(R.id.lodgingContainer)
        chipGroupStyle = view.findViewById(R.id.chipGroupStyle)
        chipGroupTransport = view.findViewById(R.id.chipGroupTransport)
        btnArrivalTime = view.findViewById(R.id.btnArrivalTime)
        btnDepartureTime = view.findViewById(R.id.btnDepartureTime)
        btnGeneratePlan = view.findViewById(R.id.btnGeneratePlan)

        btnStartDate.setOnClickListener { showDatePicker(isStart = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStart = false) }
        btnArrivalTime.setOnClickListener {
            showTimeWheelDialog(
                title = "Arrival time",
                currentValue = arrivalTime,
                defaultHour = 12,
                onSet = { value ->
                    arrivalTime = value
                    updateTimeButtons()
                },
                onClear = {
                    arrivalTime = null
                    updateTimeButtons()
                },
            )
        }
        btnDepartureTime.setOnClickListener {
            showTimeWheelDialog(
                title = "Return time",
                currentValue = departureTime,
                defaultHour = 12,
                onSet = { value ->
                    departureTime = value
                    updateTimeButtons()
                },
                onClear = {
                    departureTime = null
                    updateTimeButtons()
                },
            )
        }
        view.findViewById<MaterialButton>(R.id.btnAddRegion).setOnClickListener {
            pendingPlaceTarget = PlaceTarget.Region
            launchPlaceSearch()
        }
        btnGeneratePlan.setOnClickListener { submitPlanRequest() }

        renderRegions()
        renderLodgingRows()

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            renderPlanState(state)
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        val initial = if (isStart) startDate else endDate
        val now = initial ?: LocalDate.now()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val selected = LocalDate.of(year, month + 1, day)
                if (isStart) {
                    startDate = selected
                    if (endDate != null && endDate!!.isBefore(selected)) endDate = selected
                } else {
                    endDate = selected
                    if (startDate != null && startDate!!.isAfter(selected)) startDate = selected
                }
                updateDateButtons()
                renderLodgingRows()
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth,
        ).show()
    }

    private fun updateDateButtons() {
        btnStartDate.text = startDate?.toString() ?: "Start date"
        btnEndDate.text = endDate?.toString() ?: "End date"
    }

    private fun updateTimeButtons() {
        btnArrivalTime.text = arrivalTime?.let { "Arrival $it" } ?: "Arrival time"
        btnDepartureTime.text = departureTime?.let { "Return $it" } ?: "Return time"
    }

    private fun showTimeWheelDialog(
        title: String,
        currentValue: String?,
        defaultHour: Int,
        onSet: (String) -> Unit,
        onClear: () -> Unit,
    ) {
        val minutes = arrayOf("00", "10", "20", "30", "40", "50")
        val parts = currentValue?.split(":").orEmpty()
        val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
        val initialMinuteValue = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val initialMinuteIndex = (initialMinuteValue / 10).coerceIn(0, minutes.lastIndex)

        val pickerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 12, 24, 0)
        }

        val hourPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 23
            value = initialHour
            setFormatter { value -> "%02d".format(value) }
            wrapSelectorWheel = true
        }
        val minutePicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = minutes.lastIndex
            displayedValues = minutes
            value = initialMinuteIndex
            wrapSelectorWheel = true
        }
        val separator = TextView(requireContext()).apply {
            text = ":"
            textSize = 24f
            setTextColor(resources.getColor(R.color.white, null))
            setPadding(12, 0, 12, 0)
        }

        pickerRow.addView(hourPicker)
        pickerRow.addView(separator)
        pickerRow.addView(minutePicker)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(pickerRow)
            .setNegativeButton("Clear") { _, _ -> onClear() }
            .setNeutralButton("Cancel", null)
            .setPositiveButton("Set") { _, _ ->
                onSet("%02d:%s".format(hourPicker.value, minutes[minutePicker.value]))
            }
            .show()
    }

    private fun renderRegions() {
        regionContainer.removeAllViews()
        if (regions.isEmpty()) {
            regionContainer.addView(labelText("No regions selected yet."))
            return
        }

        regions.forEachIndexed { index, place ->
            val row = horizontalRow()
            row.addView(labelText("${index + 1}. ${place.name}", weight = 1f))
            row.addView(actionButton("Remove") {
                regions.removeAt(index)
                renderRegions()
                renderLodgingRows()
            })
            regionContainer.addView(row)
        }
    }

    private fun renderLodgingRows() {
        lodgingContainer.removeAllViews()
        val nights = selectedNights()
        if (nights <= 0) {
            lodgingContainer.addView(
                labelText(
                    if (selectedDays() == 1) {
                        "No overnight stay for a 1-day trip."
                    } else {
                        "Select dates to add optional lodging."
                    },
                ),
            )
            return
        }

        (1..nights).forEach { night ->
            val row = horizontalRow()
            val lodgingName = lodgingsByDay[night]?.name ?: "Skip lodging"
            row.addView(labelText("Night $night: $lodgingName", weight = 1f))
            row.addView(actionButton("Search") {
                pendingPlaceTarget = PlaceTarget.Lodging(night)
                launchPlaceSearch()
            })
            row.addView(actionButton("Same previous") {
                val previous = lodgingsByDay[night - 1]
                if (previous == null) {
                    Toast.makeText(requireContext(), "No previous lodging is set.", Toast.LENGTH_SHORT).show()
                } else {
                    lodgingsByDay[night] = previous
                    renderLodgingRows()
                }
            })
            if (lodgingsByDay.containsKey(night)) {
                row.addView(actionButton("Clear") {
                    lodgingsByDay.remove(night)
                    renderLodgingRows()
                })
            }
            lodgingContainer.addView(row)
        }
    }

    private fun submitPlanRequest() {
        val start = startDate
        val end = endDate
        val days = selectedDays()
        val selectedStyles = selectedStyleLabels()

        when {
            start == null || end == null || days <= 0 -> showError("Travel dates are required.")
            regions.isEmpty() -> showError("At least one city or region is required.")
            selectedStyles.isEmpty() -> showError("Select at least one style category.")
            else -> {
                txtError.visibility = View.GONE
                val destination = regions.joinToString(" -> ") { it.name }
                viewModel.generatePlan(
                    destination = destination,
                    days = days,
                    style = selectedStyles.joinToString(", "),
                    latitude = regions.firstOrNull()?.latitude,
                    longitude = regions.firstOrNull()?.longitude,
                    startDate = start.toString(),
                    endDate = end.toString(),
                    regions = regions.toList(),
                    accommodations = lodgingAssumptions(days),
                    arrivalTime = arrivalTime,
                    departureTime = departureTime,
                    transportMode = selectedTransport(),
                )
            }
        }
    }

    private fun lodgingAssumptions(days: Int): List<PlannerAccommodation> {
        if (lodgingsByDay.isEmpty()) return emptyList()
        val firstLodging = lodgingsByDay.values.firstOrNull()
        return (1 until days).mapNotNull { night ->
            val lodging = lodgingsByDay[night] ?: firstLodging ?: return@mapNotNull null
            PlannerAccommodation(
                day = night,
                name = lodging.name,
                latitude = lodging.latitude,
                longitude = lodging.longitude,
            )
        }
    }

    private fun renderPlanState(state: UiState<TravelPlan>) {
        when (state) {
            UiState.Loading -> {
                layoutInputs.visibility = View.GONE
                layoutLoading.visibility = View.VISIBLE
                btnGeneratePlan.isEnabled = false
                startLoadingAnimation()
            }
            is UiState.Error -> {
                layoutInputs.visibility = View.VISIBLE
                layoutLoading.visibility = View.GONE
                btnGeneratePlan.isEnabled = true
                stopLoadingAnimation()
                showError(state.message)
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
            else -> {
                layoutInputs.visibility = View.VISIBLE
                layoutLoading.visibility = View.GONE
                btnGeneratePlan.isEnabled = true
                stopLoadingAnimation()
            }
        }
    }

    private fun selectedStyleLabels(): List<String> =
        chipGroupStyle.children
            .filterIsInstance<Chip>()
            .filter { it.isChecked }
            .map { it.text.toString() }
            .toList()

    private fun selectedTransport(): String? =
        view?.findViewById<Chip>(chipGroupTransport.checkedChipId)?.text?.toString()

    private fun selectedDays(): Int {
        val start = startDate ?: return 0
        val end = endDate ?: return 0
        return ChronoUnit.DAYS.between(start, end).toInt() + 1
    }

    private fun selectedNights(): Int = (selectedDays() - 1).coerceAtLeast(0)

    private fun startLoadingAnimation() {
        if (loadingActive) return
        loadingActive = true
        loadingDotCount = 0
        animateLoadingText()
        floatLoadingEmoji(up = true)
    }

    private fun stopLoadingAnimation() {
        loadingActive = false
        loadingHandler.removeCallbacksAndMessages(null)
        if (::txtLoadingEmoji.isInitialized) {
            txtLoadingEmoji.animate().cancel()
            txtLoadingEmoji.translationY = 0f
        }
    }

    private fun animateLoadingText() {
        if (!loadingActive) return
        val dots = ".".repeat(loadingDotCount)
        txtLoading.text = "AI is building your itinerary$dots"
        loadingDotCount = (loadingDotCount + 1) % 4
        loadingHandler.postDelayed({ animateLoadingText() }, 430L)
    }

    private fun floatLoadingEmoji(up: Boolean) {
        if (!loadingActive) return
        txtLoadingEmoji.animate()
            .translationY(if (up) -14f else 8f)
            .setDuration(900L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { floatLoadingEmoji(!up) }
            .start()
    }

    override fun onDestroyView() {
        stopLoadingAnimation()
        super.onDestroyView()
    }

    private fun launchPlaceSearch() {
        placeSearchLauncher.launch(Intent(requireContext(), PlaceSearchActivity::class.java))
    }

    private fun showError(message: String) {
        txtError.text = message
        txtError.visibility = View.VISIBLE
    }

    private fun horizontalRow(): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }

    private fun labelText(text: String, weight: Float? = null): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.orbit_text_secondary, null))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                if (weight == null) LinearLayout.LayoutParams.WRAP_CONTENT else 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight ?: 0f,
            )
        }

    private fun actionButton(text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            this.text = text
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(14, 4, 14, 4)
            setOnClickListener { onClick() }
        }

    private sealed class PlaceTarget {
        object Region : PlaceTarget()
        data class Lodging(val day: Int) : PlaceTarget()
    }
}

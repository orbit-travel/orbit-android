package com.pnu.orbit.ui.addtrip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.R
import com.pnu.orbit.data.local.asset.Airport
import com.pnu.orbit.data.local.asset.AirportDataSource
import com.pnu.orbit.domain.model.TransportType
import com.pnu.orbit.ui.common.UiState
import com.pnu.orbit.util.IntentKeys
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddTripActivity : AppCompatActivity() {
    private val viewModel: AddTripViewModel by viewModels()
    private val airportDataSource by lazy { AirportDataSource(applicationContext) }
    private val timelineAdapter = TripTimelineAdapter(
        object : TripTimelineAdapter.Callbacks {
            override fun onTransportTypeChanged(segment: TransportSegmentDraft) {
                viewModel.updateSegment(
                    segment.copy(
                        departureName = "",
                        departureLat = null,
                        departureLng = null,
                        arrivalName = "",
                        arrivalLat = null,
                        arrivalLng = null,
                    ),
                )
            }

            override fun onTransportEndpointRequested(
                segment: TransportSegmentDraft,
                isDeparture: Boolean,
            ) {
                if (segment.type == TransportType.FLIGHT) {
                    showAirportSearch(segment, isDeparture)
                } else {
                    launchPlaceSearch(PlaceSearchTarget.SegmentEndpoint(segment.draftId, isDeparture))
                }
            }

            override fun onPhotoChanged(photo: PhotoDraft) {
                viewModel.updatePhoto(photo)
            }

            override fun onPhotoLocationRequested(photo: PhotoDraft) {
                launchPlaceSearch(PlaceSearchTarget.PhotoLocation(photo.draftId))
            }

            override fun onTimelineItemDeleted(itemId: Long) {
                viewModel.deleteTimelineItem(itemId)
            }
        },
    )

    private var currentTimeline: List<TimelineDraft> = emptyList()
    private var airports: List<Airport> = emptyList()
    private var pendingPlaceTarget: PlaceSearchTarget? = null

    private lateinit var scrollView: ScrollView
    private lateinit var tripTitleInput: EditText
    private lateinit var rangeCalendar: RangeCalendarView
    private lateinit var monthTitle: TextView
    private lateinit var dateRangeText: TextView
    private lateinit var statusText: TextView
    private lateinit var timelineList: RecyclerView

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_COUNT),
    ) { uris ->
        viewModel.addPhotoBlock(uris)
    }

    private val placeSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val target = data?.toPlaceSearchTarget() ?: pendingPlaceTarget
        pendingPlaceTarget = null
        if (result.resultCode != Activity.RESULT_OK || data == null || target == null) return@registerForActivityResult

        val name = data.getStringExtra(PlaceSearchActivity.EXTRA_PLACE_NAME).orEmpty()
        val lat = data.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LAT, Double.NaN)
        val lng = data.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LNG, Double.NaN)
        if (name.isBlank() || lat.isNaN() || lng.isNaN()) return@registerForActivityResult
        applySelectedPlace(name, lat, lng, target)
        scrollToPlaceSearchTarget(target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_trip)

        scrollView = findViewById(R.id.addTripScrollView)
        tripTitleInput = findViewById(R.id.inputTripTitle)
        rangeCalendar = findViewById(R.id.rangeCalendar)
        monthTitle = findViewById(R.id.calendarMonthTitle)
        dateRangeText = findViewById(R.id.dateRangeText)
        statusText = findViewById(R.id.addTripStatus)

        timelineList = findViewById<RecyclerView>(R.id.timelineList).apply {
            layoutManager = LinearLayoutManager(this@AddTripActivity)
            adapter = timelineAdapter
        }

        rangeCalendar.onDateClicked = viewModel::onDateClicked
        findViewById<Button>(R.id.buttonPreviousMonth).setOnClickListener {
            viewModel.moveDisplayedMonth(-1)
        }
        findViewById<Button>(R.id.buttonNextMonth).setOnClickListener {
            viewModel.moveDisplayedMonth(1)
        }
        findViewById<Button>(R.id.buttonAddTimelineItem).setOnClickListener {
            showAddItemDialog()
        }
        findViewById<Button>(R.id.buttonSaveTrip).setOnClickListener {
            currentFocus?.clearFocus()
            viewModel.saveTrip(tripTitleInput.text.toString())
        }

        viewModel.dateRange.observe(this, ::renderDateRange)
        viewModel.timeline.observe(this) { timeline ->
            val shouldScrollDown = timeline.size > currentTimeline.size
            currentTimeline = timeline
            timelineAdapter.submitList(timeline)
            if (shouldScrollDown) {
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
        viewModel.saveState.observe(this, ::renderSaveState)
    }

    private fun renderDateRange(range: DateRangeDraft) {
        monthTitle.text = monthFormat.format(Date(range.displayedMonthMillis))
        rangeCalendar.setMonth(range.displayedMonthMillis)
        rangeCalendar.setRange(range.startDateMillis, range.endDateMillis)

        dateRangeText.text = when {
            range.startDateMillis == null -> getString(R.string.date_range_empty)
            range.endDateMillis == null -> getString(
                R.string.date_range_start_only,
                dateFormat.format(Date(range.startDateMillis)),
            )
            else -> getString(
                R.string.date_range_selected,
                dateFormat.format(Date(minOf(range.startDateMillis, range.endDateMillis))),
                dateFormat.format(Date(maxOf(range.startDateMillis, range.endDateMillis))),
            )
        }
    }

    private fun showAddItemDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.add_item_title)
            .setItems(
                arrayOf(
                    getString(R.string.add_item_transport),
                    getString(R.string.add_item_photos),
                ),
            ) { _, which ->
                if (which == 0) {
                    viewModel.addSegment()
                } else {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
            .show()
    }

    private fun showAirportSearch(segment: TransportSegmentDraft, isDeparture: Boolean) {
        lifecycleScope.launch {
            if (airports.isEmpty()) {
                statusText.text = getString(R.string.airport_search_loading)
                airports = airportDataSource.loadAirports()
                statusText.text = ""
            }
            openAirportDialog(segment, isDeparture)
        }
    }

    private fun openAirportDialog(segment: TransportSegmentDraft, isDeparture: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val queryInput = EditText(this).apply {
            hint = getString(R.string.airport_search_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val listView = ListView(this)
        container.addView(queryInput)
        container.addView(listView)

        var filtered = airports.take(MAX_AIRPORT_RESULTS)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            filtered.map { it.displayName }.toMutableList(),
        )
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.airport_search_title)
            .setView(container)
            .create()

        fun updateResults(query: String) {
            val normalized = query.trim().lowercase()
            filtered = if (normalized.isBlank()) {
                airports.take(MAX_AIRPORT_RESULTS)
            } else {
                airports.asSequence()
                    .filter { airport ->
                        airport.iataCode.lowercase().contains(normalized) ||
                            airport.name.lowercase().contains(normalized) ||
                            airport.ident.lowercase().contains(normalized) ||
                            airport.countryCode.lowercase().contains(normalized) ||
                            airport.municipality.orEmpty().lowercase().contains(normalized)
                    }
                    .take(MAX_AIRPORT_RESULTS)
                    .toList()
            }
            adapter.clear()
            adapter.addAll(
                if (filtered.isEmpty()) {
                    listOf(getString(R.string.airport_search_empty))
                } else {
                    filtered.map { it.displayName }
                },
            )
        }

        queryInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateResults(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        listView.setOnItemClickListener { _, _, position, _ ->
            val airport = filtered.getOrNull(position) ?: return@setOnItemClickListener
            applyAirport(segment, airport, isDeparture)
            dialog.dismiss()
        }
        dialog.setOnShowListener { queryInput.requestFocus() }
        dialog.show()
    }

    private fun applyAirport(
        segment: TransportSegmentDraft,
        airport: Airport,
        isDeparture: Boolean,
    ) {
        val updated = if (isDeparture) {
            segment.copy(
                departureName = airport.displayName,
                departureLat = airport.lat,
                departureLng = airport.lng,
            )
        } else {
            segment.copy(
                arrivalName = airport.displayName,
                arrivalLat = airport.lat,
                arrivalLng = airport.lng,
            )
        }
        viewModel.updateSegment(updated)
    }

    private fun launchPlaceSearch(target: PlaceSearchTarget) {
        val apiKey = BuildConfig.PLACES_API_KEY.ifBlank { BuildConfig.MAPS_API_KEY }
        if (apiKey.isBlank()) {
            Toast.makeText(this, R.string.places_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        pendingPlaceTarget = target
        placeSearchLauncher.launch(
            Intent(this, PlaceSearchActivity::class.java).apply {
                putPlaceSearchTarget(target)
            },
        )
    }

    private fun Intent.putPlaceSearchTarget(target: PlaceSearchTarget) {
        when (target) {
            is PlaceSearchTarget.SegmentEndpoint -> {
                putExtra(PlaceSearchActivity.EXTRA_TARGET_TYPE, PlaceSearchActivity.TARGET_TYPE_SEGMENT)
                putExtra(PlaceSearchActivity.EXTRA_TARGET_ID, target.segmentDraftId)
                putExtra(PlaceSearchActivity.EXTRA_TARGET_IS_DEPARTURE, target.isDeparture)
            }
            is PlaceSearchTarget.PhotoLocation -> {
                putExtra(PlaceSearchActivity.EXTRA_TARGET_TYPE, PlaceSearchActivity.TARGET_TYPE_PHOTO)
                putExtra(PlaceSearchActivity.EXTRA_TARGET_ID, target.photoDraftId)
            }
        }
    }

    private fun Intent.toPlaceSearchTarget(): PlaceSearchTarget? {
        val targetId = getLongExtra(PlaceSearchActivity.EXTRA_TARGET_ID, Long.MIN_VALUE)
        if (targetId == Long.MIN_VALUE) return null
        return when (getStringExtra(PlaceSearchActivity.EXTRA_TARGET_TYPE)) {
            PlaceSearchActivity.TARGET_TYPE_SEGMENT -> PlaceSearchTarget.SegmentEndpoint(
                segmentDraftId = targetId,
                isDeparture = getBooleanExtra(PlaceSearchActivity.EXTRA_TARGET_IS_DEPARTURE, true),
            )
            PlaceSearchActivity.TARGET_TYPE_PHOTO -> PlaceSearchTarget.PhotoLocation(
                photoDraftId = targetId,
            )
            else -> null
        }
    }

    private fun applySelectedPlace(
        label: String,
        lat: Double,
        lng: Double,
        target: PlaceSearchTarget,
    ) {
        when (target) {
            is PlaceSearchTarget.SegmentEndpoint -> {
                val segment = currentTimeline
                    .filterIsInstance<TransportTimelineDraft>()
                    .map { it.segment }
                    .firstOrNull { it.draftId == target.segmentDraftId }
                    ?: return
                val updated = if (target.isDeparture) {
                    segment.copy(
                        departureName = label,
                        departureLat = lat,
                        departureLng = lng,
                    )
                } else {
                    segment.copy(
                        arrivalName = label,
                        arrivalLat = lat,
                        arrivalLng = lng,
                    )
                }
                viewModel.updateSegment(updated)
            }
            is PlaceSearchTarget.PhotoLocation -> {
                val photo = currentTimeline
                    .filterIsInstance<PhotoTimelineDraft>()
                    .flatMap { it.photoBlock.photos }
                    .firstOrNull { it.draftId == target.photoDraftId }
                    ?: return
                viewModel.updatePhoto(
                    photo.copy(
                        locationName = label,
                        lat = lat,
                        lng = lng,
                    ),
                )
            }
        }
    }

    private fun scrollToPlaceSearchTarget(target: PlaceSearchTarget) {
        val position = when (target) {
            is PlaceSearchTarget.SegmentEndpoint -> currentTimeline.indexOfFirst { item ->
                item is TransportTimelineDraft && item.segment.draftId == target.segmentDraftId
            }
            is PlaceSearchTarget.PhotoLocation -> currentTimeline.indexOfFirst { item ->
                item is PhotoTimelineDraft &&
                    item.photoBlock.photos.any { photo -> photo.draftId == target.photoDraftId }
            }
        }
        if (position < 0) return

        timelineList.post {
            scrollToTimelinePosition(position)
        }
    }

    private fun scrollToTimelinePosition(position: Int) {
        val holder = timelineList.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            val offset = (12 * resources.displayMetrics.density).toInt()
            val targetY = timelineList.top + holder.itemView.top - offset
            scrollView.smoothScrollTo(0, maxOf(0, targetY))
            return
        }

        timelineList.scrollToPosition(position)
        timelineList.post {
            timelineList.findViewHolderForAdapterPosition(position)?.let { boundHolder ->
                val offset = (12 * resources.displayMetrics.density).toInt()
                val targetY = timelineList.top + boundHolder.itemView.top - offset
                scrollView.smoothScrollTo(0, maxOf(0, targetY))
            }
        }
    }

    private fun renderSaveState(state: UiState<Long>) {
        when (state) {
            UiState.Empty -> statusText.text = ""
            UiState.Loading -> statusText.text = getString(R.string.save_loading)
            is UiState.Error -> {
                statusText.text = state.message
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
            is UiState.Success -> {
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                val destination = currentTimeline
                    .filterIsInstance<TransportTimelineDraft>()
                    .lastOrNull()
                    ?.segment
                    ?.arrivalName
                    .orEmpty()
                val result = Intent()
                    .putExtra(IntentKeys.EXTRA_TRIP_ID, state.data)
                    .putExtra(IntentKeys.EXTRA_TRIP_TITLE, tripTitleInput.text.toString().trim())
                    .putExtra(IntentKeys.EXTRA_TRIP_DESTINATION, destination)
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }
    }

    private sealed interface PlaceSearchTarget {
        data class SegmentEndpoint(
            val segmentDraftId: Long,
            val isDeparture: Boolean,
        ) : PlaceSearchTarget

        data class PhotoLocation(
            val photoDraftId: Long,
        ) : PlaceSearchTarget
    }

    companion object {
        private const val MAX_PICK_COUNT = 20
        private const val MAX_AIRPORT_RESULTS = 80
        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    }
}

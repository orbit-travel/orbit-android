package com.pnu.orbit.ui.record

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.ui.addtrip.AddTripActivity
import com.pnu.orbit.ui.common.UiState
import com.pnu.orbit.ui.detail.TravelDetailActivity
import com.pnu.orbit.util.IntentKeys

class TravelRecordFragment : Fragment(), OnMapReadyCallback {
    private val viewModel: TravelRecordViewModel by viewModels()
    private lateinit var adapter: TripPreviewAdapter
    private lateinit var statusText: TextView
    private lateinit var tripRecyclerView: RecyclerView
    private lateinit var mapPanel: View
    private lateinit var mapKeyPlaceholder: TextView
    private lateinit var recordActions: View
    private lateinit var toggleTripsButton: Button
    private lateinit var labelMy: TextView
    private lateinit var labelFriends: TextView
    private lateinit var labelWorld: TextView
    private var googleMap: GoogleMap? = null
    private var selectedEarth = EarthSelection.MY

    private val addTripLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val title = result.data?.getStringExtra(IntentKeys.EXTRA_TRIP_TITLE).orEmpty()
            statusText.text = "AddTripActivity result received: $title"
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val comment = result.data?.getStringExtra(IntentKeys.EXTRA_UPDATED_COMMENT).orEmpty()
            statusText.text = "TravelDetailActivity result received: $comment"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_record, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.recordStatus)
        mapPanel = view.findViewById(R.id.mapPanel)
        mapKeyPlaceholder = view.findViewById(R.id.mapKeyPlaceholder)
        recordActions = view.findViewById(R.id.recordActions)
        toggleTripsButton = view.findViewById(R.id.buttonToggleTrips)
        labelMy = view.findViewById(R.id.labelMy)
        labelFriends = view.findViewById(R.id.labelFriends)
        labelWorld = view.findViewById(R.id.labelWorld)
        adapter = TripPreviewAdapter { trip -> openDetail(trip) }

        tripRecyclerView = view.findViewById<RecyclerView>(R.id.tripRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TravelRecordFragment.adapter
        }

        setupEarthHub(view)
        ensureMapFragment()

        view.findViewById<Button>(R.id.buttonAddTrip).setOnClickListener {
            addTripLauncher.launch(Intent(requireContext(), AddTripActivity::class.java))
        }
        toggleTripsButton.setOnClickListener {
            toggleTripList()
        }

        viewModel.trips.observe(viewLifecycleOwner) { state -> renderState(state) }
        viewModel.loadFallbackTrips()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            mapType = GoogleMap.MAP_TYPE_SATELLITE
            uiSettings.isCompassEnabled = true
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isRotateGesturesEnabled = true
            uiSettings.isTiltGesturesEnabled = true
        }

        mapKeyPlaceholder.visibility = if (BuildConfig.MAPS_API_KEY.isBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        moveMapToSelectedEarth(animate = false)
    }

    private fun renderState(state: UiState<List<Trip>>) {
        when (state) {
            UiState.Empty -> {
                adapter.submitList(emptyList())
                statusText.text = "Swipe a globe to rotate. Tap one to unfold the satellite map."
            }
            is UiState.Error -> statusText.text = state.message
            UiState.Loading -> statusText.text = "Loading travel records..."
            is UiState.Success -> {
                adapter.submitList(state.data)
                statusText.text = "Swipe a globe to rotate. Tap one to unfold the satellite map."
            }
        }
    }

    private fun setupEarthHub(view: View) {
        val earthMy = view.findViewById<LowPolyEarthView>(R.id.earthMy).apply {
            setEarthKind(LowPolyEarthView.EarthKind.MY)
        }
        val earthFriends = view.findViewById<LowPolyEarthView>(R.id.earthFriends).apply {
            setEarthKind(LowPolyEarthView.EarthKind.FRIENDS)
        }
        val earthWorld = view.findViewById<LowPolyEarthView>(R.id.earthWorld).apply {
            setEarthKind(LowPolyEarthView.EarthKind.WORLD)
        }

        earthMy.setOnClickListener { selectEarth(EarthSelection.MY, earthMy) }
        earthFriends.setOnClickListener { selectEarth(EarthSelection.FRIENDS, earthFriends) }
        earthWorld.setOnClickListener { selectEarth(EarthSelection.WORLD, earthWorld) }
        updateEarthLabels()
    }

    private fun selectEarth(selection: EarthSelection, earthView: View) {
        selectedEarth = selection
        updateEarthLabels()
        statusText.text = "${selection.label} Earth satellite view"

        earthView.animate()
            .scaleX(1.28f)
            .scaleY(1.28f)
            .translationY(-10f)
            .setDuration(170L)
            .withEndAction {
                earthView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(190L)
                    .start()
            }
            .start()

        unfoldMapPanel()
        moveMapToSelectedEarth(animate = true)
    }

    private fun updateEarthLabels() {
        val active = ContextCompat.getColor(requireContext(), R.color.orbit_primary)
        val inactive = ContextCompat.getColor(requireContext(), R.color.orbit_text_secondary)
        labelMy.setTextColor(if (selectedEarth == EarthSelection.MY) active else inactive)
        labelFriends.setTextColor(if (selectedEarth == EarthSelection.FRIENDS) active else inactive)
        labelWorld.setTextColor(if (selectedEarth == EarthSelection.WORLD) active else inactive)
    }

    private fun unfoldMapPanel() {
        if (mapPanel.visibility == View.VISIBLE) return

        recordActions.alpha = 0f
        recordActions.translationY = 18f
        recordActions.visibility = View.VISIBLE

        mapPanel.alpha = 0f
        mapPanel.pivotY = 0f
        mapPanel.scaleY = 0.74f
        mapPanel.visibility = View.VISIBLE

        mapPanel.animate()
            .alpha(1f)
            .scaleY(1f)
            .setDuration(320L)
            .start()

        recordActions.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .start()
    }

    private fun toggleTripList() {
        if (tripRecyclerView.visibility == View.VISIBLE) {
            tripRecyclerView.animate()
                .alpha(0f)
                .translationY(16f)
                .setDuration(180L)
                .withEndAction {
                    tripRecyclerView.visibility = View.GONE
                    toggleTripsButton.setText(R.string.travel_list)
                }
                .start()
        } else {
            unfoldMapPanel()
            tripRecyclerView.alpha = 0f
            tripRecyclerView.translationY = 18f
            tripRecyclerView.visibility = View.VISIBLE
            tripRecyclerView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .withEndAction {
                    toggleTripsButton.setText(R.string.travel_list_hide)
                }
                .start()
        }
    }

    private fun ensureMapFragment() {
        val existing = childFragmentManager.findFragmentById(R.id.mapFragmentContainer)
        val mapFragment = existing as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.mapFragmentContainer, it)
                .commitNow()
        }
        mapFragment.getMapAsync(this)
    }

    private fun moveMapToSelectedEarth(animate: Boolean) {
        val map = googleMap ?: return
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(selectedEarth.cameraTarget, selectedEarth.zoom)
        if (animate && mapPanel.visibility == View.VISIBLE) {
            map.animateCamera(cameraUpdate)
        } else {
            map.moveCamera(cameraUpdate)
        }
    }

    private fun openDetail(trip: Trip) {
        val intent = Intent(requireContext(), TravelDetailActivity::class.java)
            .putExtra(IntentKeys.EXTRA_TRIP_ID, trip.id)
            .putExtra(IntentKeys.EXTRA_TRIP_TITLE, trip.title)
            .putExtra(IntentKeys.EXTRA_TRIP_DESTINATION, trip.destination)
        detailLauncher.launch(intent)
    }

    private enum class EarthSelection(
        val label: String,
        val cameraTarget: LatLng,
        val zoom: Float,
    ) {
        MY("My", LatLng(35.1796, 129.0756), 10.5f),
        FRIENDS("Friends", LatLng(37.5665, 126.9780), 9.5f),
        WORLD("World", LatLng(34.6937, 135.5023), 5.2f),
    }
}

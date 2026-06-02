package com.pnu.orbit.ui.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.gms.maps.model.MapStyleOptions
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
    private lateinit var earthStage: View
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
    private var currentLocationTarget: LatLng? = null
    private var activeLocationListener: LocationListener? = null
    private var hasRequestedLocationPermission = false

    private val addTripLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) Unit
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) Unit
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasLocationPermission()) {
            refreshCurrentLocation()
        } else {
            Toast.makeText(
                requireContext(),
                "Location permission is needed to center the map.",
                Toast.LENGTH_SHORT,
            ).show()
            moveMapToSelectedEarth(animate = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasRequestedLocationPermission = savedInstanceState
            ?.getBoolean(STATE_LOCATION_PERMISSION_REQUESTED)
            ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_record, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        earthStage = view.findViewById(R.id.earthStage)
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
        requestLocationPermissionIfNeeded()

        view.findViewById<Button>(R.id.buttonAddTrip).setOnClickListener {
            addTripLauncher.launch(Intent(requireContext(), AddTripActivity::class.java))
        }
        toggleTripsButton.setOnClickListener {
            toggleTripList()
        }

        viewModel.trips.observe(viewLifecycleOwner) { state -> renderState(state) }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            refreshCurrentLocation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCATION_PERMISSION_REQUESTED, hasRequestedLocationPermission)
    }

    override fun onDestroyView() {
        removeActiveLocationListener()
        googleMap = null
        super.onDestroyView()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            mapType = GoogleMap.MAP_TYPE_NORMAL
            applyNebulaMapStyle()
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
        if (hasLocationPermission()) {
            refreshCurrentLocation()
        } else {
            moveMapToSelectedEarth(animate = false)
        }
    }

    private fun GoogleMap.applyNebulaMapStyle() {
        try {
            val success = setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style_nebula,
                ),
            )
            if (!success) {
                Log.e(TAG, "Map style parsing failed.")
            }
        } catch (exception: Resources.NotFoundException) {
            Log.e(TAG, "Map style resource not found.", exception)
        }
    }

    private fun renderState(state: UiState<List<Trip>>) {
        when (state) {
            UiState.Empty -> adapter.submitList(emptyList())
            is UiState.Error -> adapter.submitList(emptyList())
            UiState.Loading -> Unit
            is UiState.Success -> adapter.submitList(state.data)
        }
    }

    private fun setupEarthHub(view: View) {
        val earthMy = view.findViewById<EarthModelView>(R.id.earthMy).apply {
            setEarthVariant(EarthModelView.EarthVariant.MY)
        }
        val earthFriends = view.findViewById<EarthModelView>(R.id.earthFriends).apply {
            setEarthVariant(EarthModelView.EarthVariant.FRIENDS)
        }
        val earthWorld = view.findViewById<EarthModelView>(R.id.earthWorld).apply {
            setEarthVariant(EarthModelView.EarthVariant.WORLD)
        }

        earthMy.setOnClickListener { selectEarth(EarthSelection.MY, earthMy) }
        earthFriends.setOnClickListener { selectEarth(EarthSelection.FRIENDS, earthFriends) }
        earthWorld.setOnClickListener { selectEarth(EarthSelection.WORLD, earthWorld) }
        updateEarthLabels()
    }

    private fun selectEarth(selection: EarthSelection, earthView: View) {
        selectedEarth = selection
        updateEarthLabels()

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
        if (hasLocationPermission()) {
            refreshCurrentLocation(animate = true)
        } else {
            requestLocationPermissionIfNeeded()
            moveMapToSelectedEarth(animate = true)
        }
    }

    private fun updateEarthLabels() {
        labelMy.setTextColor(ContextCompat.getColor(requireContext(), R.color.nebula_my))
        labelFriends.setTextColor(ContextCompat.getColor(requireContext(), R.color.nebula_friends))
        labelWorld.setTextColor(ContextCompat.getColor(requireContext(), R.color.nebula_world))
    }

    private fun unfoldMapPanel() {
        if (mapPanel.visibility == View.VISIBLE) return

        earthStage.animate()
            .alpha(0f)
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(260L)
            .withEndAction { earthStage.visibility = View.GONE }
            .start()

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
        val cameraTarget = currentLocationTarget ?: selectedEarth.cameraTarget
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(cameraTarget, selectedEarth.zoom)
        if (animate && mapPanel.visibility == View.VISIBLE) {
            map.animateCamera(cameraUpdate)
        } else {
            map.moveCamera(cameraUpdate)
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            refreshCurrentLocation()
            return
        }
        if (hasRequestedLocationPermission) return

        hasRequestedLocationPermission = true
        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentLocation(animate: Boolean = mapPanel.visibility == View.VISIBLE) {
        if (!hasLocationPermission()) return

        googleMap?.apply {
            isMyLocationEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
        }

        val locationManager = requireContext()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        findBestLastKnownLocation(locationManager)?.let { location ->
            updateCurrentLocationTarget(location, animate)
        }
        requestSingleLocationUpdate(locationManager, animate)
    }

    @SuppressLint("MissingPermission")
    private fun findBestLastKnownLocation(locationManager: LocationManager): Location? =
        locationManager.getProviders(true)
            .ifEmpty {
                listOf(
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER,
                )
            }
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { location -> location.time }

    @SuppressLint("MissingPermission")
    private fun requestSingleLocationUpdate(locationManager: LocationManager, animate: Boolean) {
        val provider = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: return

        removeActiveLocationListener()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateCurrentLocationTarget(location, animate)
                removeActiveLocationListener()
            }

            override fun onProviderDisabled(provider: String) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        activeLocationListener = listener

        @Suppress("DEPRECATION")
        runCatching {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }.onFailure { error ->
            Log.w(TAG, "Failed to request current location.", error)
            removeActiveLocationListener()
        }
    }

    private fun updateCurrentLocationTarget(location: Location, animate: Boolean) {
        currentLocationTarget = LatLng(location.latitude, location.longitude)
        moveMapToSelectedEarth(animate)
    }

    private fun removeActiveLocationListener() {
        val listener = activeLocationListener ?: return
        val locationManager = context
            ?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeLocationListener = null
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
        MY("My", LatLng(35.1796, 129.0756), 4.45f),
        FRIENDS("Friends", LatLng(35.1796, 129.0756), 4.45f),
        WORLD("World", LatLng(35.1796, 129.0756), 4.45f),
    }

    companion object {
        private const val TAG = "TravelRecordFragment"
        private const val STATE_LOCATION_PERMISSION_REQUESTED = "state_location_permission_requested"
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

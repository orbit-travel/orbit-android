package com.pnu.orbit.ui.addtrip

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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.R

class PlaceSearchActivity : AppCompatActivity(), OnMapReadyCallback {
    private val searchHandler = Handler(Looper.getMainLooper())
    private lateinit var placesClient: PlacesClient
    private lateinit var sessionToken: AutocompleteSessionToken
    private lateinit var queryInput: EditText
    private lateinit var statusText: TextView
    private lateinit var placeholder: TextView
    private lateinit var resultAdapter: ArrayAdapter<String>
    private var googleMap: GoogleMap? = null
    private var latestQuery: String = ""
    private var candidates: List<PlaceCandidate> = emptyList()
    private var currentLocationTarget: LatLng? = null
    private var activeLocationListener: LocationListener? = null
    private var hasRequestedLocationPermission = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasLocationPermission()) {
            refreshCurrentLocation(animate = true)
        } else {
            moveMapToInitialLocation(animate = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasRequestedLocationPermission = savedInstanceState
            ?.getBoolean(STATE_LOCATION_PERMISSION_REQUESTED)
            ?: false
        setContentView(R.layout.activity_place_search)

        queryInput = findViewById(R.id.inputPlaceSearch)
        statusText = findViewById(R.id.placeSearchStatus)
        placeholder = findViewById(R.id.placeMapPlaceholder)
        resultAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf(getString(R.string.place_search_empty)),
        )
        findViewById<ListView>(R.id.placeResultList).apply {
            adapter = resultAdapter
            setOnItemClickListener { _, _, position, _ ->
                candidates.getOrNull(position)?.let(::returnCandidate)
            }
        }

        if (!initializePlaces()) {
            Toast.makeText(this, R.string.places_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        sessionToken = AutocompleteSessionToken.newInstance()
        setupMap()
        setupSearchInput()
    }

    override fun onDestroy() {
        searchHandler.removeCallbacksAndMessages(SEARCH_TOKEN)
        removeActiveLocationListener()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCATION_PERMISSION_REQUESTED, hasRequestedLocationPermission)
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
            setOnMarkerClickListener { marker ->
                (marker.tag as? PlaceCandidate)?.let(::returnCandidate)
                true
            }
        }
        if (candidates.isNotEmpty()) {
            renderMapMarkers(candidates)
        } else if (hasLocationPermission()) {
            moveMapToInitialLocation(animate = false)
            refreshCurrentLocation()
        } else {
            moveMapToInitialLocation(animate = false)
            requestLocationPermissionIfNeeded()
        }
    }

    private fun GoogleMap.applyNebulaMapStyle() {
        try {
            val success = setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this@PlaceSearchActivity,
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

    private fun initializePlaces(): Boolean {
        val apiKey = BuildConfig.PLACES_API_KEY.ifBlank { BuildConfig.MAPS_API_KEY }
        if (apiKey.isBlank()) return false
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        placesClient = Places.createClient(this)
        return true
    }

    private fun setupMap() {
        placeholder.visibility = if (BuildConfig.MAPS_API_KEY.isBlank()) View.VISIBLE else View.GONE
        if (BuildConfig.MAPS_API_KEY.isBlank()) return

        val existing = supportFragmentManager.findFragmentById(R.id.placeMapContainer)
        val mapFragment = existing as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            supportFragmentManager.beginTransaction()
                .replace(R.id.placeMapContainer, it)
                .commitNow()
        }
        mapFragment.getMapAsync(this)
    }

    private fun setupSearchInput() {
        queryInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    searchHandler.removeCallbacksAndMessages(SEARCH_TOKEN)
                    searchHandler.postAtTime(
                        { searchPlaces(query) },
                        SEARCH_TOKEN,
                        SystemClock.uptimeMillis() + SEARCH_DELAY_MS,
                    )
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        queryInput.requestFocus()
    }

    private fun searchPlaces(query: String) {
        latestQuery = query
        if (query.trim().length < MIN_QUERY_LENGTH) {
            candidates = emptyList()
            renderCandidates(emptyList())
            statusText.text = getString(R.string.place_search_helper)
            googleMap?.clear()
            moveMapToInitialLocation(animate = true)
            return
        }

        statusText.text = getString(R.string.place_search_loading)
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(sessionToken)
            .build()
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                if (query != latestQuery) return@addOnSuccessListener
                val predictions = response.autocompletePredictions.take(MAX_RESULTS)
                if (predictions.isEmpty()) {
                    candidates = emptyList()
                    renderCandidates(emptyList())
                    statusText.text = getString(R.string.place_search_empty)
                } else {
                    fetchPredictionPlaces(query, predictions)
                }
            }
            .addOnFailureListener { error ->
                if (query != latestQuery) return@addOnFailureListener
                candidates = emptyList()
                renderCandidates(emptyList())
                statusText.text = "${getString(R.string.place_search_failed)} ${error.message.orEmpty()}"
            }
    }

    private fun fetchPredictionPlaces(
        query: String,
        predictions: List<AutocompletePrediction>,
    ) {
        val pendingCandidates = MutableList<PlaceCandidate?>(predictions.size) { null }
        var completed = 0
        predictions.forEachIndexed { index, prediction ->
            val request = FetchPlaceRequest.builder(
                prediction.placeId,
                listOf(
                    Place.Field.ID,
                    Place.Field.DISPLAY_NAME,
                    Place.Field.FORMATTED_ADDRESS,
                    Place.Field.LOCATION,
                ),
            ).setSessionToken(sessionToken)
                .build()

            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    if (query != latestQuery) return@addOnSuccessListener
                    pendingCandidates[index] = response.place.toCandidate(prediction)
                }
                .addOnCompleteListener {
                    if (query != latestQuery) return@addOnCompleteListener
                    completed += 1
                    if (completed == predictions.size) {
                        candidates = pendingCandidates.filterNotNull()
                        renderCandidates(candidates)
                        renderMapMarkers(candidates)
                        statusText.text = if (candidates.isEmpty()) {
                            getString(R.string.place_search_empty)
                        } else {
                            getString(R.string.place_search_helper)
                        }
                    }
                }
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun refreshCurrentLocation(animate: Boolean = false) {
        if (!hasLocationPermission()) return

        googleMap?.apply {
            isMyLocationEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
        if (shouldUseCurrentLocationCamera()) {
            moveMapToInitialLocation(animate)
        }
    }

    private fun shouldUseCurrentLocationCamera(): Boolean =
        candidates.isEmpty() && latestQuery.trim().length < MIN_QUERY_LENGTH

    private fun moveMapToInitialLocation(animate: Boolean) {
        val map = googleMap ?: return
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
            currentLocationTarget ?: DEFAULT_CAMERA,
            INITIAL_ZOOM,
        )
        if (animate) {
            map.animateCamera(cameraUpdate)
        } else {
            map.moveCamera(cameraUpdate)
        }
    }

    private fun removeActiveLocationListener() {
        val listener = activeLocationListener ?: return
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeLocationListener = null
    }

    private fun renderCandidates(items: List<PlaceCandidate>) {
        resultAdapter.clear()
        resultAdapter.addAll(
            if (items.isEmpty()) {
                listOf(getString(R.string.place_search_empty))
            } else {
                items.map { it.label }
            },
        )
    }

    private fun renderMapMarkers(items: List<PlaceCandidate>) {
        val map = googleMap ?: return
        map.clear()
        if (items.isEmpty()) return

        items.forEach { item ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(item.latLng)
                    .title(item.name)
                    .snippet(item.address)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
            )
            marker?.tag = item
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(items.first().latLng, RESULT_ZOOM))
    }

    private fun Place.toCandidate(prediction: AutocompletePrediction): PlaceCandidate? {
        val location = location ?: return null
        val name = displayName ?: prediction.getPrimaryText(null).toString()
        val address = formattedAddress ?: prediction.getSecondaryText(null).toString()
        val label = if (address.isBlank()) name else "$name\n$address"
        return PlaceCandidate(
            name = name,
            address = address,
            label = label,
            latLng = location,
        )
    }

    private fun returnCandidate(candidate: PlaceCandidate) {
        val result = Intent()
            .putExtra(EXTRA_PLACE_NAME, candidate.name)
            .putExtra(EXTRA_PLACE_LAT, candidate.latLng.latitude)
            .putExtra(EXTRA_PLACE_LNG, candidate.latLng.longitude)
            .copyTargetExtras()
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun Intent.copyTargetExtras(): Intent {
        val targetType = this@PlaceSearchActivity.intent.getStringExtra(EXTRA_TARGET_TYPE)
        val targetId = this@PlaceSearchActivity.intent.getLongExtra(EXTRA_TARGET_ID, Long.MIN_VALUE)
        if (targetType != null && targetId != Long.MIN_VALUE) {
            putExtra(EXTRA_TARGET_TYPE, targetType)
            putExtra(EXTRA_TARGET_ID, targetId)
            putExtra(
                EXTRA_TARGET_IS_DEPARTURE,
                this@PlaceSearchActivity.intent.getBooleanExtra(EXTRA_TARGET_IS_DEPARTURE, true),
            )
        }
        return this
    }

    private data class PlaceCandidate(
        val name: String,
        val address: String,
        val label: String,
        val latLng: LatLng,
    )

    companion object {
        private const val TAG = "PlaceSearchActivity"

        const val EXTRA_PLACE_NAME = "extra_place_name"
        const val EXTRA_PLACE_LAT = "extra_place_lat"
        const val EXTRA_PLACE_LNG = "extra_place_lng"
        const val EXTRA_TARGET_TYPE = "extra_target_type"
        const val EXTRA_TARGET_ID = "extra_target_id"
        const val EXTRA_TARGET_IS_DEPARTURE = "extra_target_is_departure"
        const val TARGET_TYPE_SEGMENT = "segment"
        const val TARGET_TYPE_PHOTO = "photo"

        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_RESULTS = 8
        private const val SEARCH_DELAY_MS = 300L
        private const val STATE_LOCATION_PERMISSION_REQUESTED = "state_location_permission_requested"
        private const val INITIAL_ZOOM = 4.45f
        private const val RESULT_ZOOM = 12f
        private val SEARCH_TOKEN = Any()
        private val DEFAULT_CAMERA = LatLng(35.1796, 129.0756)
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

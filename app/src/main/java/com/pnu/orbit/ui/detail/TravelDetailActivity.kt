package com.pnu.orbit.ui.detail

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.R
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TransportType
import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.map.PlaceCoordinateResolver
import com.pnu.orbit.ui.common.SimpleTextAdapter
import com.pnu.orbit.util.IntentKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TravelDetailActivity : AppCompatActivity(), OnMapReadyCallback {
    private val repository by lazy { RepositoryProvider.tripRepository(applicationContext) }
    private val detailAdapter = SimpleTextAdapter()
    private var googleMap: GoogleMap? = null
    private var loadedTrip: Trip? = null
    private var loadedSegments: List<TransportSegment> = emptyList()
    private var loadedPhotos: List<TravelPhoto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel_detail)

        val tripId = intent.getLongExtra(IntentKeys.EXTRA_TRIP_ID, -1L)
        val title = intent.getStringExtra(IntentKeys.EXTRA_TRIP_TITLE).orEmpty()
        val destination = intent.getStringExtra(IntentKeys.EXTRA_TRIP_DESTINATION).orEmpty()

        findViewById<TextView>(R.id.detailTitle).text =
            title.ifBlank { getString(R.string.detail_title_fallback) }
        findViewById<TextView>(R.id.detailStatus).text =
            getString(R.string.detail_status_intent, tripId, destination.ifBlank { "unknown" })
        findViewById<RecyclerView>(R.id.detailPhotoList).apply {
            layoutManager = LinearLayoutManager(this@TravelDetailActivity)
            adapter = detailAdapter
        }
        findViewById<Button>(R.id.buttonFinishDetail).setOnClickListener {
            val result = Intent().putExtra(IntentKeys.EXTRA_UPDATED_COMMENT, "detail_checked")
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        ensureMapFragment()
        loadTripDetail(tripId)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isCompassEnabled = true
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = true
        }
        renderMap()
    }

    private fun ensureMapFragment() {
        val placeholder = findViewById<TextView>(R.id.mapPlaceholder)
        placeholder.text = if (BuildConfig.MAPS_API_KEY.isBlank()) {
            getString(R.string.detail_map_key_missing)
        } else {
            getString(R.string.detail_route_placeholder)
        }
        placeholder.visibility = if (BuildConfig.MAPS_API_KEY.isBlank()) View.VISIBLE else View.GONE

        val existing = supportFragmentManager.findFragmentById(R.id.detailMapFragmentContainer)
        val mapFragment = existing as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            supportFragmentManager.beginTransaction()
                .replace(R.id.detailMapFragmentContainer, it)
                .commitNow()
        }
        mapFragment.getMapAsync(this)
    }

    private fun loadTripDetail(tripId: Long) {
        if (tripId <= 0L) {
            detailAdapter.submitList(listOf(getString(R.string.detail_no_trip)))
            return
        }

        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val trip = repository.getTrip(tripId)
                val segments = repository.getSegments(tripId)
                val photos = repository.observePhotos(tripId).first()
                Triple(trip, segments, photos)
            }
            loadedTrip = detail.first
            loadedSegments = detail.second
            loadedPhotos = detail.third
            renderSummary()
            renderMap()
        }
    }

    private fun renderSummary() {
        val trip = loadedTrip
        if (trip == null) {
            detailAdapter.submitList(listOf(getString(R.string.detail_no_trip)))
            return
        }

        findViewById<TextView>(R.id.detailTitle).text = trip.title
        val segmentItems = loadedSegments.map { segment ->
            getString(
                R.string.detail_segment_summary,
                segment.type.label,
                segment.departureName,
                segment.arrivalName,
            )
        }
        val photoItems = if (loadedPhotos.isEmpty()) {
            listOf(getString(R.string.detail_no_photos))
        } else {
            loadedPhotos.mapIndexed { index, photo ->
                val text = photo.comment
                    ?: photo.locationName
                    ?: if (photo.hasLocation) "${photo.lat}, ${photo.lng}" else getString(R.string.detail_photo_unlocated)
                getString(R.string.detail_photo_summary, index + 1, text)
            }
        }
        detailAdapter.submitList(segmentItems + photoItems)
    }

    private fun renderMap() {
        val map = googleMap ?: return
        val trip = loadedTrip ?: return
        map.clear()

        val bounds = LatLngBounds.Builder()
        var hasBounds = false
        fun include(latLng: LatLng) {
            bounds.include(latLng)
            hasBounds = true
        }

        loadedSegments.forEachIndexed { index, segment ->
            val start = segment.departureLatLng ?: PlaceCoordinateResolver.resolve(segment.departureName)
            val end = segment.arrivalLatLng ?: PlaceCoordinateResolver.resolve(segment.arrivalName)
            map.addPolyline(
                PolylineOptions()
                    .add(start, end)
                    .color(segment.type.polylineColor)
                    .width(6f),
            )
            map.addMarker(
                MarkerOptions()
                    .position(start)
                    .title("${segment.type.label} ${index + 1}")
                    .snippet(segment.departureName)
                    .icon(BitmapDescriptorFactory.defaultMarker(segment.type.markerHue)),
            )
            map.addMarker(
                MarkerOptions()
                    .position(end)
                    .title("${segment.type.label} ${index + 1}")
                    .snippet(segment.arrivalName)
                    .icon(BitmapDescriptorFactory.defaultMarker(segment.type.markerHue)),
            )
            include(start)
            include(end)
        }

        loadedPhotos.forEachIndexed { index, photo ->
            val location = photoLocation(photo, index, trip)
            map.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(photo.comment ?: "Photo ${index + 1}")
                    .snippet(photo.locationName ?: photo.uri)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
            )
            include(location)
        }

        if (!hasBounds) {
            include(PlaceCoordinateResolver.resolve(trip.destination))
        }

        val cameraUpdate = if (hasBounds) {
            CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)
        } else {
            CameraUpdateFactory.newLatLngZoom(PlaceCoordinateResolver.resolve(trip.destination), 10f)
        }
        runCatching { map.moveCamera(cameraUpdate) }
            .onFailure {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        PlaceCoordinateResolver.resolve(trip.destination),
                        10f,
                    ),
                )
            }
    }

    private fun photoLocation(photo: TravelPhoto, index: Int, trip: Trip): LatLng {
        if (photo.hasLocation) {
            return LatLng(photo.lat ?: 0.0, photo.lng ?: 0.0)
        }

        val previous = loadedPhotos.take(index).lastOrNull { it.hasLocation }
        val next = loadedPhotos.drop(index + 1).firstOrNull { it.hasLocation }
        if (previous != null && next != null) {
            return midpoint(
                LatLng(previous.lat ?: 0.0, previous.lng ?: 0.0),
                LatLng(next.lat ?: 0.0, next.lng ?: 0.0),
            )
        }

        val segment = loadedSegments.firstOrNull { it.id == photo.segmentId }
        if (segment != null) {
            return midpoint(
                segment.departureLatLng ?: PlaceCoordinateResolver.resolve(segment.departureName),
                segment.arrivalLatLng ?: PlaceCoordinateResolver.resolve(segment.arrivalName),
            )
        }

        return PlaceCoordinateResolver.resolve(trip.destination)
    }

    private fun midpoint(start: LatLng, end: LatLng): LatLng =
        LatLng((start.latitude + end.latitude) / 2.0, (start.longitude + end.longitude) / 2.0)

    private val TransportSegment.departureLatLng: LatLng?
        get() = if (departureLat != null && departureLng != null) {
            LatLng(departureLat, departureLng)
        } else {
            null
        }

    private val TransportSegment.arrivalLatLng: LatLng?
        get() = if (arrivalLat != null && arrivalLng != null) {
            LatLng(arrivalLat, arrivalLng)
        } else {
            null
        }

    private val TransportType.label: String
        get() = when (this) {
            TransportType.FLIGHT -> getString(R.string.transport_flight)
            TransportType.TRAIN -> getString(R.string.transport_train)
            TransportType.CAR -> getString(R.string.transport_car)
        }

    private val TransportType.markerHue: Float
        get() = when (this) {
            TransportType.FLIGHT -> BitmapDescriptorFactory.HUE_ORANGE
            TransportType.TRAIN -> BitmapDescriptorFactory.HUE_GREEN
            TransportType.CAR -> BitmapDescriptorFactory.HUE_VIOLET
        }

    private val TransportType.polylineColor: Int
        get() = when (this) {
            TransportType.FLIGHT -> 0xFFFFD166.toInt()
            TransportType.TRAIN -> 0xFF98DFAF.toInt()
            TransportType.CAR -> 0xFF64D2FF.toInt()
        }
}

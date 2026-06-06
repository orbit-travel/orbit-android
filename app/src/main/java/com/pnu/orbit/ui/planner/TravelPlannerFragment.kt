package com.pnu.orbit.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.common.UiState

import android.content.res.Resources
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.map.PlaceCoordinateResolver

class TravelPlannerFragment : Fragment(), OnMapReadyCallback {
    private val viewModel: TravelPlannerViewModel by viewModels()
    private val adapter = PlanDayAdapter()
    private lateinit var statusText: TextView
    private lateinit var mapPanel: View
    private lateinit var mapKeyPlaceholder: TextView
    private lateinit var viewPager: ViewPager2
    private var googleMap: GoogleMap? = null
    private var currentPlan: TravelPlan? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_planner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.plannerStatus)
        mapPanel = view.findViewById(R.id.plannerMapPanel)
        mapKeyPlaceholder = view.findViewById(R.id.plannerMapKeyPlaceholder)
        
        viewPager = view.findViewById(R.id.dayPlanPager)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateMapForDay(position)
            }
        })

        view.findViewById<Button>(R.id.buttonGeneratePlan).setOnClickListener {
            val destination = view.findViewById<EditText>(R.id.inputDestination).text.toString()
            val days = view.findViewById<EditText>(R.id.inputDays).text.toString().toIntOrNull() ?: 1
            val style = view.findViewById<EditText>(R.id.inputStyle).text.toString()
            viewModel.generatePlan(destination, days, style)
        }

        viewModel.plan.observe(viewLifecycleOwner) { state -> renderState(state) }
    }

    override fun onDestroyView() {
        googleMap = null
        super.onDestroyView()
    }

    private fun renderState(state: UiState<TravelPlan>) {
        when (state) {
            UiState.Empty -> {
                currentPlan = null
                mapPanel.visibility = View.GONE
                adapter.submitList(emptyList())
                statusText.text = getString(R.string.planner_empty)
            }
            is UiState.Error -> {
                currentPlan = null
                mapPanel.visibility = View.GONE
                statusText.text = state.message
            }
            UiState.Loading -> {
                currentPlan = null
                mapPanel.visibility = View.GONE
                statusText.text = getString(R.string.planner_loading)
            }
            is UiState.Success -> {
                currentPlan = state.data
                mapPanel.visibility = View.VISIBLE
                ensureMapFragment()
                adapter.submitList(state.data.dayPlans)
                statusText.text = if (state.data.isFallback) {
                    getString(R.string.planner_fallback)
                } else {
                    getString(R.string.planner_success)
                }
            }
        }
    }

    private fun ensureMapFragment() {
        val existing = childFragmentManager.findFragmentById(R.id.plannerMapFragmentContainer)
        val mapFragment = existing as? SupportMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.plannerMapFragmentContainer, it)
                .commitNow()
        }
        mapFragment.getMapAsync(this)
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

        updateMapForDay(viewPager.currentItem)
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
                Log.e("TravelPlannerFragment", "Map style parsing failed.")
            }
        } catch (exception: Resources.NotFoundException) {
            Log.e("TravelPlannerFragment", "Map style resource not found.", exception)
        }
    }

    private fun updateMapForDay(pageIndex: Int) {
        val map = googleMap ?: return
        map.clear()

        val plan = currentPlan ?: return
        val dayPlan = plan.dayPlans.getOrNull(pageIndex) ?: return

        val points = dayPlan.attractions.map { attr ->
            if (attr.latitude != null && attr.longitude != null) {
                LatLng(attr.latitude, attr.longitude)
            } else {
                PlaceCoordinateResolver.resolve(attr.name)
            }
        }

        if (points.isEmpty()) return

        // Draw connecting polyline for route (동선)
        val polylineOptions = PolylineOptions()
            .addAll(points)
            .color(ContextCompat.getColor(requireContext(), R.color.orbit_primary))
            .width(8f)
            .geodesic(true)
        map.addPolyline(polylineOptions)

        // Draw markers
        dayPlan.attractions.forEachIndexed { index, attr ->
            val latLng = points[index]
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("${attr.sequence}. ${attr.name}")
                    .snippet(attr.description)
            )
        }

        // Focus camera bounds
        if (points.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        } else {
            val builder = LatLngBounds.builder()
            points.forEach { builder.include(it) }
            runCatching {
                val bounds = builder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }.onFailure {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 11f))
            }
        }
    }
}

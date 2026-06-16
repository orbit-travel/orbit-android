package com.pnu.orbit.ui.planner

import android.app.AlertDialog
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
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
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.map.LabeledMarkerFactory
import com.pnu.orbit.map.PlaceCoordinateResolver
import com.pnu.orbit.domain.model.TimeType
import com.pnu.orbit.ui.common.UiState

class GeneratedItemFragment : Fragment(), OnMapReadyCallback {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })
    private val adapter by lazy {
        PlanDayAdapter(
            onMoveUp = { dayNum, sequence -> viewModel.moveAttractionUp(dayNum, sequence) },
            onMoveDown = { dayNum, sequence -> viewModel.moveAttractionDown(dayNum, sequence) },
            onDelete = { dayNum, sequence -> viewModel.deleteAttraction(dayNum, sequence) },
            onTimeUpdated = { dayNum, sequence, timeType, start, end, approx ->
                viewModel.updateAttractionTime(dayNum, sequence, timeType, start, end, approx)
            },
            onMoveDay = { currentDay, sequence, targetDay ->
                viewModel.moveAttractionToDay(currentDay, sequence, targetDay)
            },
            onAddPlace = { dayNum ->
                showAddPlaceDialog(dayNum)
            },
            onCardClick = { attraction ->
                val latLng = if (attraction.latitude != null && attraction.longitude != null) {
                    LatLng(attraction.latitude, attraction.longitude)
                } else {
                    PlaceCoordinateResolver.resolve(attraction.name)
                }
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        )
    }
    
    private lateinit var btnBackToGenerate: ImageButton
    private lateinit var btnApprovePlan: Button
    private lateinit var generatedTitle: TextView
    private lateinit var plannerStatus: TextView
    private lateinit var mapPanel: View
    private lateinit var mapKeyPlaceholder: TextView
    private lateinit var viewPager: ViewPager2
    
    private var googleMap: GoogleMap? = null
    private var currentPlan: TravelPlan? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_generated_item, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBackToGenerate = view.findViewById(R.id.btnBackToGenerate)
        btnApprovePlan = view.findViewById(R.id.btnApprovePlan)
        generatedTitle = view.findViewById(R.id.generatedTitle)
        plannerStatus = view.findViewById(R.id.plannerStatus)
        mapPanel = view.findViewById(R.id.plannerMapPanel)
        mapKeyPlaceholder = view.findViewById(R.id.plannerMapKeyPlaceholder)
        
        viewPager = view.findViewById(R.id.dayPlanPager)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateMapForDay(position)
            }
        })

        btnBackToGenerate.setOnClickListener {
            viewModel.startNewPlan()
        }

        btnApprovePlan.setOnClickListener {
            viewModel.navigateToConfirm()
        }

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            renderPlanState(state)
        }
    }

    override fun onDestroyView() {
        googleMap = null
        super.onDestroyView()
    }

    private fun renderPlanState(state: UiState<TravelPlan>) {
        when (state) {
            is UiState.Success -> {
                currentPlan = state.data
                generatedTitle.text = "${state.data.destination} 여행 계획"
                mapPanel.visibility = View.VISIBLE
                ensureMapFragment()
                adapter.submitList(state.data.dayPlans)
                updateMapForDay(viewPager.currentItem)
                
                plannerStatus.text = if (state.data.isFallback) {
                    getString(R.string.planner_fallback)
                } else {
                    getString(R.string.planner_success)
                }
            }
            else -> {
                // If not success (e.g. Empty or Loading or Error), do nothing here as parent coordinates navigation
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
                Log.e("GeneratedItemFragment", "Map style parsing failed.")
            }
        } catch (exception: Resources.NotFoundException) {
            Log.e("GeneratedItemFragment", "Map style resource not found.", exception)
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

        // Draw labelled markers (category pictogram + place name) so each stop is readable at a
        // glance without tapping it.
        val ctx = context
        dayPlan.attractions.forEachIndexed { index, attr ->
            val latLng = points[index]
            val category = determineCategory(attr.name, attr.description)
            val details = getCategoryDetails(category)

            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("${attr.sequence}. ${attr.name}")
                .snippet(attr.description)

            if (ctx != null) {
                markerOptions
                    .icon(
                        LabeledMarkerFactory.create(
                            context = ctx,
                            label = "${attr.sequence}. ${attr.name}",
                            accentColor = details.second,
                            iconRes = details.first,
                        ),
                    )
                    .anchor(LabeledMarkerFactory.ANCHOR_U, LabeledMarkerFactory.ANCHOR_V)
                    .zIndex((dayPlan.attractions.size - index).toFloat())
            } else {
                getBitmapDescriptorFromVector(details.first, details.second)?.let { markerOptions.icon(it) }
            }
            map.addMarker(markerOptions)
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

    private fun getBitmapDescriptorFromVector(vectorResId: Int, colorTint: Int): com.google.android.gms.maps.model.BitmapDescriptor? {
        val context = context ?: return null
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        val wrappedDrawable = androidx.core.graphics.drawable.DrawableCompat.wrap(vectorDrawable).mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(wrappedDrawable, colorTint)
        
        val width = 56
        val height = 56
        wrappedDrawable.setBounds(0, 0, width, height)
        
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        wrappedDrawable.draw(canvas)
        
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun showAddPlaceDialog(dayNum: Int) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_place, null)
        val etPlaceName = dialogView.findViewById<EditText>(R.id.etPlaceName)
        val spPlaceCategory = dialogView.findViewById<Spinner>(R.id.spPlaceCategory)
        val etPlaceLatitude = dialogView.findViewById<EditText>(R.id.etPlaceLatitude)
        val etPlaceLongitude = dialogView.findViewById<EditText>(R.id.etPlaceLongitude)
        val btnPickOnMap = dialogView.findViewById<Button>(R.id.btnPickOnMap)

        // Pre-populate coordinates with current day's first attraction coordinates if available
        currentPlan?.dayPlans?.find { it.day == dayNum }?.attractions?.firstOrNull { it.latitude != null && it.longitude != null }?.let { attr ->
            etPlaceLatitude.setText(attr.latitude.toString())
            etPlaceLongitude.setText(attr.longitude.toString())
        }

        // Set up spinner
        val categories = AttractionCategory.values()
        val categoryDisplayNames = categories.map { it.displayName }.toTypedArray()
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categoryDisplayNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spPlaceCategory.adapter = spinnerAdapter

        // Set up map picker button
        btnPickOnMap.setOnClickListener {
            val mapDialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_map_picker, null)
            val mapView = mapDialogView.findViewById<com.google.android.gms.maps.MapView>(R.id.mapPickerView)
            
            var selectedLatLng: LatLng? = null

            mapView.onCreate(null)
            mapView.onResume()
            mapView.getMapAsync { map ->
                map.mapType = GoogleMap.MAP_TYPE_NORMAL
                
                // Camera focus logic
                val currentLat = etPlaceLatitude.text.toString().toDoubleOrNull()
                val currentLng = etPlaceLongitude.text.toString().toDoubleOrNull()
                if (currentLat != null && currentLng != null) {
                    val pos = LatLng(currentLat, currentLng)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
                    map.addMarker(MarkerOptions().position(pos))
                    selectedLatLng = pos
                } else {
                    currentPlan?.dayPlans?.flatMap { it.attractions }?.firstOrNull { it.latitude != null && it.longitude != null }?.let { attr ->
                        val pos = LatLng(attr.latitude!!, attr.longitude!!)
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 13f))
                    } ?: run {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.1795, 129.0756), 11f))
                    }
                }

                map.setOnMapClickListener { latLng ->
                    map.clear()
                    map.addMarker(MarkerOptions().position(latLng))
                    selectedLatLng = latLng
                }
            }

            AlertDialog.Builder(requireContext())
                .setView(mapDialogView)
                .setPositiveButton("선택 완료") { _, _ ->
                    selectedLatLng?.let { latLng ->
                        etPlaceLatitude.setText(latLng.latitude.toString())
                        etPlaceLongitude.setText(latLng.longitude.toString())
                    }
                    mapView.onPause()
                    mapView.onDestroy()
                }
                .setNegativeButton("취소") { _, _ ->
                    mapView.onPause()
                    mapView.onDestroy()
                }
                .setOnDismissListener {
                    mapView.onPause()
                    mapView.onDestroy()
                }
                .show()
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("추가") { _, _ ->
                val name = etPlaceName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val categoryIndex = spPlaceCategory.selectedItemPosition
                val selectedCategory = categories.getOrNull(categoryIndex) ?: AttractionCategory.ACTIVITY
                val lat = etPlaceLatitude.text.toString().toDoubleOrNull() ?: 0.0
                val lng = etPlaceLongitude.text.toString().toDoubleOrNull() ?: 0.0

                viewModel.addCustomAttraction(
                    dayNum = dayNum,
                    name = name,
                    categoryName = selectedCategory.displayName,
                    lat = lat,
                    lng = lng
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

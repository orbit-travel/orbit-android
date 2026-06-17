package com.pnu.orbit.ui.planner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.button.MaterialButton
import com.pnu.orbit.BuildConfig
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.map.LabeledMarkerFactory
import com.pnu.orbit.map.PlaceCoordinateResolver
import com.pnu.orbit.ui.addtrip.PlaceSearchActivity
import com.pnu.orbit.ui.common.UiState

class GeneratedItemFragment : Fragment(), OnMapReadyCallback {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })
    private val adapter by lazy {
        PlanDayAdapter(
            onDelete = { dayNum, sequence -> viewModel.deleteAttraction(dayNum, sequence) },
            onTimeUpdated = { dayNum, sequence, timeType, start, end, approx ->
                viewModel.updateAttractionTime(dayNum, sequence, timeType, start, end, approx)
            },
            onMoveDay = { currentDay, sequence, targetDay ->
                viewModel.moveAttractionToDay(currentDay, sequence, targetDay)
            },
            onAddPlace = { dayNum ->
                pendingAddPlaceDay = dayNum
                placeSearchLauncher.launch(Intent(requireContext(), PlaceSearchActivity::class.java))
            },
            onCardClick = { attraction -> focusMap(attraction.name, attraction.latitude, attraction.longitude) },
            onDetailsClick = { attraction -> openPlaceDetails(attraction.name) },
            onReorder = { dayNum, fromIndex, toIndex ->
                viewModel.reorderAttraction(dayNum, fromIndex, dayNum, toIndex)
            },
        )
    }

    private lateinit var btnBackToGenerate: ImageButton
    private lateinit var btnApprovePlan: Button
    private lateinit var generatedTitle: EditText
    private lateinit var plannerStatus: TextView
    private lateinit var btnEditPlan: MaterialButton
    private lateinit var btnDeletePlan: MaterialButton
    private lateinit var btnToggleMap: MaterialButton
    private lateinit var dayArrowLeft: TextView
    private lateinit var dayArrowRight: TextView
    private lateinit var mapPanel: View
    private lateinit var mapKeyPlaceholder: TextView
    private lateinit var viewPager: ViewPager2

    private var googleMap: GoogleMap? = null
    private var currentPlan: TravelPlan? = null
    private var pendingAddPlaceDay: Int? = null
    private var currentEditable = true
    private var pendingMapFocus: LatLng? = null

    private val placeSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult
        val day = pendingAddPlaceDay ?: return@registerForActivityResult
        val name = result.data?.getStringExtra(PlaceSearchActivity.EXTRA_PLACE_NAME) ?: return@registerForActivityResult
        val lat = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LAT, 0.0) ?: 0.0
        val lng = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LNG, 0.0) ?: 0.0
        viewModel.addCustomAttraction(day, name, "Manual", lat, lng)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_generated_item, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBackToGenerate = view.findViewById(R.id.btnBackToGenerate)
        btnApprovePlan = view.findViewById(R.id.btnApprovePlan)
        generatedTitle = view.findViewById(R.id.generatedTitle)
        plannerStatus = view.findViewById(R.id.plannerStatus)
        plannerStatus.visibility = View.GONE
        btnEditPlan = view.findViewById(R.id.btnEditPlan)
        btnDeletePlan = view.findViewById(R.id.btnDeletePlan)
        btnToggleMap = view.findViewById(R.id.btnToggleMap)
        dayArrowLeft = view.findViewById(R.id.dayArrowLeft)
        dayArrowRight = view.findViewById(R.id.dayArrowRight)
        mapPanel = view.findViewById(R.id.plannerMapPanel)
        mapKeyPlaceholder = view.findViewById(R.id.plannerMapKeyPlaceholder)
        viewPager = view.findViewById(R.id.dayPlanPager)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateMapForDay(position)
                    updateDayArrows()
                }
            },
        )

        btnBackToGenerate.setOnClickListener { viewModel.startNewPlan() }
        btnApprovePlan.setOnClickListener {
            viewModel.updatePlanTitle(generatedTitle.text.toString())
            viewModel.saveCurrentPlan(generatedTitle.text.toString())
        }
        btnToggleMap.setOnClickListener {
            setMapVisible(mapPanel.visibility != View.VISIBLE)
        }
        btnEditPlan.setOnClickListener { viewModel.enableCurrentPlanEditing() }
        btnDeletePlan.setOnClickListener { confirmDeletePlan() }
        dayArrowLeft.setOnClickListener {
            if (viewPager.currentItem > 0) viewPager.currentItem = viewPager.currentItem - 1
        }
        dayArrowRight.setOnClickListener {
            val count = currentPlan?.dayPlans?.size ?: 0
            if (viewPager.currentItem < count - 1) viewPager.currentItem = viewPager.currentItem + 1
        }

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            renderPlanState(state)
        }
        viewModel.isPlanEditable.observe(viewLifecycleOwner) { editable ->
            currentEditable = editable
            applyEditMode(editable)
        }
        viewModel.loadedSavedPlan.observe(viewLifecycleOwner) {
            updatePlanActions()
        }
    }

    override fun onDestroyView() {
        googleMap = null
        super.onDestroyView()
    }

    private fun renderPlanState(state: UiState<TravelPlan>) {
        if (state !is UiState.Success) return

        currentPlan = state.data
        if (generatedTitle.text.toString() != state.data.destination) {
            generatedTitle.setText(state.data.destination)
        }
        adapter.submitList(state.data.dayPlans)
        adapter.setEditable(currentEditable)
        updateMapForDay(viewPager.currentItem)
        updateDayArrows()
        plannerStatus.text = ""
        plannerStatus.visibility = View.GONE
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
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isRotateGesturesEnabled = true
            uiSettings.isTiltGesturesEnabled = true
        }

        mapKeyPlaceholder.visibility = if (BuildConfig.MAPS_API_KEY.isBlank()) View.VISIBLE else View.GONE
        updateMapForDay(viewPager.currentItem)
        pendingMapFocus?.let { target ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
            pendingMapFocus = null
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
            if (!success) Log.e("GeneratedItemFragment", "Map style parsing failed.")
        } catch (exception: Resources.NotFoundException) {
            Log.e("GeneratedItemFragment", "Map style resource not found.", exception)
        }
    }

    private fun updateMapForDay(pageIndex: Int) {
        val map = googleMap ?: return
        map.clear()
        updateDayArrows()

        val dayPlan = currentPlan?.dayPlans?.getOrNull(pageIndex) ?: return
        val points = dayPlan.attractions.map { attraction ->
            if (attraction.latitude != null && attraction.longitude != null) {
                LatLng(attraction.latitude, attraction.longitude)
            } else {
                PlaceCoordinateResolver.resolve(attraction.name)
            }
        }

        if (points.isEmpty()) return

        val context = context
        dayPlan.attractions.forEachIndexed { index, attraction ->
            val category = determineCategory(attraction.name, attraction.description)
            val details = getCategoryDetails(category)
            val markerOptions = MarkerOptions()
                .position(points[index])
                .title("${attraction.sequence}. ${attraction.name}")
                .snippet(attraction.description)

            if (context != null) {
                markerOptions
                    .icon(
                        LabeledMarkerFactory.create(
                            context = context,
                            label = "${attraction.sequence}. ${attraction.name}",
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

        if (points.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        } else {
            val builder = LatLngBounds.builder()
            points.forEach { builder.include(it) }
            runCatching {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
            }.onFailure {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 11f))
            }
        }
    }

    private fun focusMap(name: String, latitude: Double?, longitude: Double?) {
        val latLng = if (latitude != null && longitude != null) {
            LatLng(latitude, longitude)
        } else {
            PlaceCoordinateResolver.resolve(name)
        }
        setMapVisible(true)
        val map = googleMap
        if (map == null) {
            pendingMapFocus = latLng
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    private fun openPlaceDetails(placeName: String) {
        val query = Uri.encode(placeName)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
        startActivity(intent)
    }

    private fun applyEditMode(editable: Boolean) {
        generatedTitle.isEnabled = editable
        generatedTitle.isFocusable = editable
        generatedTitle.isFocusableInTouchMode = editable
        adapter.setEditable(editable)
        updatePlanActions()
    }

    private fun updatePlanActions() {
        val isSavedPlan = viewModel.loadedSavedPlan.value != null
        btnEditPlan.visibility = if (isSavedPlan && !currentEditable) View.VISIBLE else View.GONE
        btnDeletePlan.visibility = if (isSavedPlan) View.VISIBLE else View.GONE
        btnApprovePlan.visibility = if (!isSavedPlan || currentEditable) View.VISIBLE else View.GONE
        btnApprovePlan.text = if (isSavedPlan) "Save changes" else "Save"
    }

    private fun updateDayArrows() {
        val count = currentPlan?.dayPlans?.size ?: 0
        val index = viewPager.currentItem
        dayArrowLeft.visibility = if (count > 1 && index > 0) View.VISIBLE else View.GONE
        dayArrowRight.visibility = if (count > 1 && index < count - 1) View.VISIBLE else View.GONE
    }

    private fun setMapVisible(visible: Boolean) {
        mapPanel.visibility = if (visible) View.VISIBLE else View.GONE
        btnToggleMap.text = if (visible) "Hide map" else "Map"
        if (visible) {
            ensureMapFragment()
            updateMapForDay(viewPager.currentItem)
        }
    }

    private fun confirmDeletePlan() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete itinerary")
            .setMessage("Delete this saved itinerary?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteCurrentSavedPlan()
            }
            .show()
    }

    private fun getBitmapDescriptorFromVector(
        vectorResId: Int,
        colorTint: Int,
    ): com.google.android.gms.maps.model.BitmapDescriptor? {
        val context = context ?: return null
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        val wrappedDrawable = androidx.core.graphics.drawable.DrawableCompat.wrap(vectorDrawable).mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(wrappedDrawable, colorTint)
        val bitmap = android.graphics.Bitmap.createBitmap(56, 56, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        wrappedDrawable.setBounds(0, 0, 56, 56)
        wrappedDrawable.draw(canvas)
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

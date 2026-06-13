package com.pnu.orbit.ui.planner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.ui.addtrip.PlaceSearchActivity
import com.pnu.orbit.ui.common.UiState

class GeneratingPlanFragment : Fragment() {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: RecommendationCardAdapter

    private lateinit var layoutInputs: View
    private lateinit var layoutRecommendations: View
    private lateinit var progressBar: ProgressBar
    private lateinit var txtError: TextView

    private lateinit var inputDestination: EditText
    private lateinit var inputDays: EditText
    private lateinit var chipGroupStyle: ChipGroup
    private lateinit var chipGroupRadius: ChipGroup
    private lateinit var btnFindRecommendations: MaterialButton

    private lateinit var recommendationsPager: ViewPager2
    private lateinit var txtRecommendationSummary: TextView
    private lateinit var txtSelectedCount: TextView
    private lateinit var btnConfirmItinerary: MaterialButton
    private lateinit var btnBackToInputs: ImageButton

    private var selectedPlaceName: String? = null
    private var selectedPlaceLat: Double? = null
    private var selectedPlaceLng: Double? = null

    private val placeSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val name = result.data?.getStringExtra(PlaceSearchActivity.EXTRA_PLACE_NAME)
            val lat = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LAT, 0.0)
            val lng = result.data?.getDoubleExtra(PlaceSearchActivity.EXTRA_PLACE_LNG, 0.0)

            if (name != null) {
                selectedPlaceName = name
                selectedPlaceLat = lat
                selectedPlaceLng = lng
                inputDestination.setText(name)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_generating_plan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutInputs = view.findViewById(R.id.layoutInputs)
        layoutRecommendations = view.findViewById(R.id.layoutRecommendations)
        progressBar = view.findViewById(R.id.progressBar)
        txtError = view.findViewById(R.id.txtError)

        inputDestination = view.findViewById(R.id.inputDestination)
        inputDays = view.findViewById(R.id.inputDays)
        chipGroupStyle = view.findViewById(R.id.chipGroupStyle)
        chipGroupRadius = view.findViewById(R.id.chipGroupRadius)
        btnFindRecommendations = view.findViewById(R.id.btnFindRecommendations)

        recommendationsPager = view.findViewById(R.id.recommendationsPager)
        txtRecommendationSummary = view.findViewById(R.id.txtRecommendationSummary)
        txtSelectedCount = view.findViewById(R.id.txtSelectedCount)
        btnConfirmItinerary = view.findViewById(R.id.btnConfirmItinerary)
        btnBackToInputs = view.findViewById(R.id.btnBackToInputs)

        adapter = RecommendationCardAdapter { attraction, isSelected ->
            viewModel.toggleRecommendationSelected(attraction, isSelected)
        }
        recommendationsPager.adapter = adapter

        inputDestination.setOnClickListener {
            val intent = Intent(requireContext(), PlaceSearchActivity::class.java)
            placeSearchLauncher.launch(intent)
        }

        btnFindRecommendations.setOnClickListener {
            val destination = inputDestination.text.toString()
            if (destination.isBlank() || selectedPlaceLat == null || selectedPlaceLng == null) {
                Toast.makeText(requireContext(), "Please select a destination from search.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnFindRecommendations.isEnabled = false

            val days = inputDays.text.toString().toIntOrNull() ?: 1
            val selectedStyles = mutableListOf<String>()
            if (view.findViewById<Chip>(R.id.chipExtreme).isChecked) selectedStyles.add("extreme")
            if (view.findViewById<Chip>(R.id.chipFood).isChecked) selectedStyles.add("food")
            if (view.findViewById<Chip>(R.id.chipNature).isChecked) selectedStyles.add("nature")
            if (view.findViewById<Chip>(R.id.chipCulture).isChecked) selectedStyles.add("culture")
            val styleString = selectedStyles.joinToString(", ").ifBlank { "general" }

            val checkedRadiusId = chipGroupRadius.checkedChipId
            val radiusKm = when (checkedRadiusId) {
                R.id.chipRadius1 -> 1.0
                R.id.chipRadius15 -> 1.5
                R.id.chipRadius3 -> 3.0
                R.id.chipRadius5 -> 5.0
                else -> 3.0
            }

            viewModel.fetchRecommendations(
                destination = destination,
                style = styleString,
                latitude = selectedPlaceLat!!,
                longitude = selectedPlaceLng!!,
                radiusKm = radiusKm
            )
        }

        btnBackToInputs.setOnClickListener {
            viewModel.startNewPlan()
        }

        btnConfirmItinerary.setOnClickListener {
            val selected = viewModel.selectedRecommendations.value?.toList().orEmpty()
            if (selected.isEmpty()) return@setOnClickListener
            btnConfirmItinerary.isEnabled = false

            val destination = inputDestination.text.toString()
            val days = inputDays.text.toString().toIntOrNull() ?: 1
            val selectedStyles = mutableListOf<String>()
            if (view.findViewById<Chip>(R.id.chipExtreme).isChecked) selectedStyles.add("extreme")
            if (view.findViewById<Chip>(R.id.chipFood).isChecked) selectedStyles.add("food")
            if (view.findViewById<Chip>(R.id.chipNature).isChecked) selectedStyles.add("nature")
            if (view.findViewById<Chip>(R.id.chipCulture).isChecked) selectedStyles.add("culture")
            val styleString = selectedStyles.joinToString(", ").ifBlank { "general" }

            viewModel.confirmAndBuildPlan(selected, destination, days, styleString)
        }

        viewModel.recommendations.observe(viewLifecycleOwner) { state ->
            renderRecommendationsState(state)
        }

        viewModel.selectedRecommendations.observe(viewLifecycleOwner) { selected ->
            adapter.updateSelected(selected)
            txtSelectedCount.text = "선택된 장소: ${selected.size}개"
            btnConfirmItinerary.isEnabled = selected.isNotEmpty()
        }

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Error) {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                btnConfirmItinerary.isEnabled = true
            }
        }
    }


    private fun renderRecommendationsState(state: UiState<List<Attraction>>) {
        when (state) {
            UiState.Empty -> {
                layoutInputs.visibility = View.VISIBLE
                layoutRecommendations.visibility = View.GONE
                progressBar.visibility = View.GONE
                txtError.visibility = View.GONE
                btnFindRecommendations.isEnabled = true
            }
            UiState.Loading -> {
                layoutInputs.visibility = View.GONE
                layoutRecommendations.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                txtError.visibility = View.GONE
            }
            is UiState.Error -> {
                layoutInputs.visibility = View.VISIBLE
                layoutRecommendations.visibility = View.GONE
                progressBar.visibility = View.GONE
                txtError.visibility = View.VISIBLE
                txtError.text = state.message
                btnFindRecommendations.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
            is UiState.Success -> {
                layoutInputs.visibility = View.GONE
                layoutRecommendations.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                txtError.visibility = View.GONE
                btnFindRecommendations.isEnabled = true
                adapter.submitList(state.data)
                
                val checkedRadiusId = chipGroupRadius.checkedChipId
                val radiusText = when (checkedRadiusId) {
                    R.id.chipRadius1 -> "1km"
                    R.id.chipRadius15 -> "1.5km"
                    R.id.chipRadius3 -> "3km"
                    R.id.chipRadius5 -> "5km"
                    else -> "3km"
                }
                txtRecommendationSummary.text = "${selectedPlaceName ?: ""} (${inputDays.text}일, $radiusText)"
            }
        }
    }
}

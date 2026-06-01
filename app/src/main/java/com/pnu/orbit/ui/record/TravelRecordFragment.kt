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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.ui.addtrip.AddTripActivity
import com.pnu.orbit.ui.common.UiState
import com.pnu.orbit.ui.detail.TravelDetailActivity
import com.pnu.orbit.util.IntentKeys

class TravelRecordFragment : Fragment() {
    private val viewModel: TravelRecordViewModel by viewModels()
    private lateinit var adapter: TripPreviewAdapter
    private lateinit var statusText: TextView

    private val addTripLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val title = result.data?.getStringExtra(IntentKeys.EXTRA_TRIP_TITLE).orEmpty()
            statusText.text = "AddTripActivity result 수신: $title"
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val comment = result.data?.getStringExtra(IntentKeys.EXTRA_UPDATED_COMMENT).orEmpty()
            statusText.text = "TravelDetailActivity result 수신: $comment"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_record, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.recordStatus)
        adapter = TripPreviewAdapter { trip -> openDetail(trip) }

        view.findViewById<RecyclerView>(R.id.tripRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TravelRecordFragment.adapter
        }

        view.findViewById<Button>(R.id.buttonAddTrip).setOnClickListener {
            addTripLauncher.launch(Intent(requireContext(), AddTripActivity::class.java))
        }
        view.findViewById<Button>(R.id.buttonOpenDetail).setOnClickListener {
            openDetail(
                Trip(
                    id = 1L,
                    title = "샘플 상세",
                    startPlace = "부산대",
                    destination = "해운대",
                    startDate = 0L,
                    endDate = 0L,
                    coverPhotoUri = null,
                    memo = "Intent demo",
                    photoCount = 4,
                ),
            )
        }

        viewModel.trips.observe(viewLifecycleOwner) { state -> renderState(state) }
        viewModel.loadFallbackTrips()
    }

    private fun renderState(state: UiState<List<Trip>>) {
        when (state) {
            UiState.Empty -> {
                adapter.submitList(emptyList())
                statusText.text = "아직 저장된 여행 기록이 없습니다."
            }
            is UiState.Error -> statusText.text = state.message
            UiState.Loading -> statusText.text = "여행 기록을 불러오는 중..."
            is UiState.Success -> {
                adapter.submitList(state.data)
                statusText.text = "샘플 여행 ${state.data.size}개 표시 중. Room 연결은 다음 구현 단계입니다."
            }
        }
    }

    private fun openDetail(trip: Trip) {
        val intent = Intent(requireContext(), TravelDetailActivity::class.java)
            .putExtra(IntentKeys.EXTRA_TRIP_ID, trip.id)
            .putExtra(IntentKeys.EXTRA_TRIP_TITLE, trip.title)
            .putExtra(IntentKeys.EXTRA_TRIP_DESTINATION, trip.destination)
        detailLauncher.launch(intent)
    }
}

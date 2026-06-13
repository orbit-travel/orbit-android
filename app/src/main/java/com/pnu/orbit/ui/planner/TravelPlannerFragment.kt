package com.pnu.orbit.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.pnu.orbit.R
import com.pnu.orbit.ui.common.UiState

class TravelPlannerFragment : Fragment() {
    private val viewModel: TravelPlannerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_planner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) {
                showChildFragment(GeneratedItemFragment())
            } else {
                showChildFragment(GeneratingPlanFragment())
            }
        }
    }

    private fun showChildFragment(fragment: Fragment) {
        val current = childFragmentManager.findFragmentById(R.id.plannerFragmentContainer)
        if (current != null && current::class == fragment::class) {
            return
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.plannerFragmentContainer, fragment)
            .commit()
    }
}

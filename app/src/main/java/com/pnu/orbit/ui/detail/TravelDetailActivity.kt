package com.pnu.orbit.ui.detail

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import com.pnu.orbit.ui.common.SimpleTextAdapter
import com.pnu.orbit.util.IntentKeys

class TravelDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel_detail)

        val tripId = intent.getLongExtra(IntentKeys.EXTRA_TRIP_ID, -1L)
        val title = intent.getStringExtra(IntentKeys.EXTRA_TRIP_TITLE).orEmpty()
        val destination = intent.getStringExtra(IntentKeys.EXTRA_TRIP_DESTINATION).orEmpty()

        findViewById<TextView>(R.id.detailTitle).text = title.ifBlank { "여행 상세" }
        findViewById<TextView>(R.id.detailStatus).text =
            "Intent 수신: tripId=$tripId, destination=${destination.ifBlank { "unknown" }}"

        val detailAdapter = SimpleTextAdapter().apply {
            submitList(
                listOf(
                    "지도 API 연결 예정: 출발지-도착지 Polyline",
                    "사진 EXIF GPS가 있으면 마커 표시",
                    "사진 메모/ML 태그는 Room에 저장",
                ),
            )
        }
        findViewById<RecyclerView>(R.id.detailPhotoList).apply {
            layoutManager = LinearLayoutManager(this@TravelDetailActivity)
            adapter = detailAdapter
        }

        findViewById<Button>(R.id.buttonFinishDetail).setOnClickListener {
            val result = Intent().putExtra(IntentKeys.EXTRA_UPDATED_COMMENT, "detail_checked")
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}

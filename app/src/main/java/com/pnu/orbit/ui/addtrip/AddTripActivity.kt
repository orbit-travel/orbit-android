package com.pnu.orbit.ui.addtrip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import com.pnu.orbit.ui.common.SimpleTextAdapter
import com.pnu.orbit.util.IntentKeys

class AddTripActivity : AppCompatActivity() {
    private val selectedUris = mutableListOf<Uri>()
    private val photoAdapter = SimpleTextAdapter()

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_COUNT),
    ) { uris ->
        selectedUris.clear()
        selectedUris.addAll(uris)
        renderSelectedPhotos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_trip)

        findViewById<RecyclerView>(R.id.selectedPhotoList).apply {
            layoutManager = LinearLayoutManager(this@AddTripActivity)
            adapter = photoAdapter
        }

        findViewById<Button>(R.id.buttonPickPhotos).setOnClickListener {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<Button>(R.id.buttonSaveTrip).setOnClickListener {
            saveTripResult()
        }

        renderSelectedPhotos()
    }

    private fun renderSelectedPhotos() {
        val items = if (selectedUris.isEmpty()) {
            listOf("선택된 사진 없음")
        } else {
            selectedUris.mapIndexed { index, uri -> "${index + 1}. $uri" }
        }
        photoAdapter.submitList(items)
    }

    private fun saveTripResult() {
        val titleInput = findViewById<EditText>(R.id.inputTripTitle)
        val destinationInput = findViewById<EditText>(R.id.inputDestination)
        val title = titleInput.text.toString().trim()
        val destination = destinationInput.text.toString().trim()

        if (title.isBlank()) {
            titleInput.error = "여행 제목을 입력하세요"
            return
        }
        if (destination.isBlank()) {
            destinationInput.error = "도착지를 입력하세요"
            return
        }

        val result = Intent()
            .putExtra(IntentKeys.EXTRA_TRIP_ID, System.currentTimeMillis())
            .putExtra(IntentKeys.EXTRA_TRIP_TITLE, title)
            .putExtra(IntentKeys.EXTRA_TRIP_DESTINATION, destination)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        private const val MAX_PICK_COUNT = 20
    }
}

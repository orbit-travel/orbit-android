package com.pnu.orbit.ui.addtrip

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A MediaStore-backed photo grid. Unlike the system photo picker, the URIs it returns are real
 * MediaStore items, so (with ACCESS_MEDIA_LOCATION) [PhotoMetadataReader] can read their original
 * GPS EXIF. The caller must ensure read-images permission is granted before launching this.
 */
class GalleryPickerActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var doneButton: Button
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView

    private var maxCount = 1
    private lateinit var adapter: GalleryImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_picker)

        maxCount = intent.getIntExtra(EXTRA_MAX_COUNT, 1).coerceAtLeast(1)

        titleText = findViewById(R.id.galleryPickerTitle)
        doneButton = findViewById(R.id.galleryPickerDone)
        emptyText = findViewById(R.id.galleryPickerEmpty)
        recyclerView = findViewById(R.id.galleryRecyclerView)

        adapter = GalleryImageAdapter(
            maxCount = maxCount,
            onSinglePicked = { uri -> finishWith(listOf(uri)) },
            onSelectionChanged = ::updateTitle,
            onMaxReached = {
                Toast.makeText(
                    this,
                    getString(R.string.gallery_picker_max_reached, maxCount),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN)
        recyclerView.adapter = adapter

        // Single-select returns on tap, so the Done button is only meaningful for multi-select.
        doneButton.visibility = if (maxCount > 1) View.VISIBLE else View.GONE
        doneButton.setOnClickListener { finishWith(adapter.selectedUris) }

        updateTitle()
        loadImages()
    }

    private fun loadImages() {
        lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) { queryImages() }
            adapter.submit(uris)
            emptyText.visibility = if (uris.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun queryImages(): List<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
        val result = mutableListOf<Uri>()
        runCatching {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    result.add(ContentUris.withAppendedId(collection, id))
                }
            }
        }
        return result
    }

    private fun updateTitle() {
        titleText.text = if (maxCount > 1) {
            getString(R.string.gallery_picker_selected_count, adapter.selectedCount)
        } else {
            getString(R.string.gallery_picker_title)
        }
    }

    private fun finishWith(uris: List<Uri>) {
        if (uris.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent().putStringArrayListExtra(
            EXTRA_RESULT_URIS,
            ArrayList(uris.map { it.toString() }),
        )
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_MAX_COUNT = "extra_max_count"
        const val EXTRA_RESULT_URIS = "extra_result_uris"
        private const val GRID_SPAN = 3
    }
}

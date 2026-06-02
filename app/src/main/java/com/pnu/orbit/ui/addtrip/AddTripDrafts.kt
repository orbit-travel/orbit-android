package com.pnu.orbit.ui.addtrip

import android.net.Uri
import com.pnu.orbit.domain.model.PhotoTag
import com.pnu.orbit.domain.model.TransportType

data class DateRangeDraft(
    val displayedMonthMillis: Long,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
)

data class TransportSegmentDraft(
    val draftId: Long,
    val type: TransportType = TransportType.FLIGHT,
    val departureName: String = "",
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    val arrivalName: String = "",
    val arrivalLat: Double? = null,
    val arrivalLng: Double? = null,
)

data class PhotoDraft(
    val draftId: Long,
    val uri: Uri,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val locationName: String?,
    val comment: String = "",
    val tag: PhotoTag = PhotoTag.UNKNOWN,
)

data class PhotoBlockDraft(
    val draftId: Long,
    val photos: List<PhotoDraft>,
)

sealed interface TimelineDraft {
    val draftId: Long
}

data class TransportTimelineDraft(
    val segment: TransportSegmentDraft,
) : TimelineDraft {
    override val draftId: Long = segment.draftId
}

data class PhotoTimelineDraft(
    val photoBlock: PhotoBlockDraft,
) : TimelineDraft {
    override val draftId: Long = photoBlock.draftId
}

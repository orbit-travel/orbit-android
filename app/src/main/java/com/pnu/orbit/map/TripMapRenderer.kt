package com.pnu.orbit.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TransportType
import com.pnu.orbit.domain.model.TravelPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws a trip onto an existing [GoogleMap]:
 *  - transport legs as mode-coloured lines (flight = sky-blue dashed) with a moving mode icon,
 *  - accommodations as hotel pins,
 *  - photos as small stacked cards that fan out on tap (the stacked card hides while expanded and
 *    the cards merge back into it on the next camera move) and open a detail view via [onPhotoClick],
 *  - the whole itinerary linked in order by a thin yellow route with a soft light pulse that
 *    repeatedly travels the path so the visiting order reads at a glance; the pulse skips straight
 *    across flight legs instead of tracing the flight arc.
 * Marker and line sizes scale with the current zoom level.
 */
class TripMapRenderer(
    private val context: Context,
    private val map: GoogleMap,
    private val scope: CoroutineScope,
) {
    /** Invoked when a single photo card is tapped. */
    var onPhotoClick: ((TravelPhoto) -> Unit)? = null

    private val scalables = mutableListOf<Scalable>()
    private val polylineInfos = mutableListOf<PolyInfo>()
    private val animators = mutableListOf<ValueAnimator>()
    private val fanAnimators = mutableListOf<ValueAnimator>()
    private val groupByMarker = mutableMapOf<Marker, PhotoGroup>()
    private val fannedPhotoByMarker = mutableMapOf<Marker, TravelPhoto>()
    private val fannedMarkers = mutableListOf<Marker>()
    private var expandedGroup: PhotoGroup? = null
    private var expandedMarker: Marker? = null
    private var photoBitmaps: Map<String, Bitmap?> = emptyMap()
    private var renderJob: Job? = null
    private var currentScale = 1f

    private val density = context.resources.displayMetrics.density

    private class Scalable(val marker: Marker, val base: Bitmap)
    private class PolyInfo(val polyline: Polyline, val baseWidthPx: Float)

    private data class PhotoGroup(
        val center: LatLng,
        val photos: List<TravelPhoto>,
    )

    fun clear() {
        renderJob?.cancel()
        renderJob = null
        animators.forEach { it.cancel() }
        animators.clear()
        fanAnimators.forEach { it.cancel() }
        fanAnimators.clear()
        fannedMarkers.forEach { it.remove() }
        fannedMarkers.clear()
        fannedPhotoByMarker.clear()
        expandedGroup = null
        expandedMarker = null
        scalables.forEach { it.marker.remove() }
        scalables.clear()
        polylineInfos.forEach { it.polyline.remove() }
        polylineInfos.clear()
        groupByMarker.clear()
        runCatching {
            map.setOnMarkerClickListener(null)
            map.setOnCameraIdleListener(null)
            map.setOnCameraMoveStartedListener(null)
        }
    }

    fun render(
        segments: List<TransportSegment>,
        photos: List<TravelPhoto>,
        fallbackDestination: String,
    ) {
        clear()
        renderJob = scope.launch {
            currentScale = scaleForZoom(map.cameraPosition.zoom)

            val locations = resolvePhotoLocations(photos, segments, fallbackDestination)
            val groups = groupPhotos(photos, locations)
            photoBitmaps = withContext(Dispatchers.IO) {
                photos.map { it.uri }.distinct().associateWith { uri -> loadBitmap(uri) }
            }

            val orderedPoints = mutableListOf<LatLng>()
            val skipNextYellow = mutableListOf<Boolean>()
            // flightLeg[i] == true means the leg from orderedPoints[i] to [i+1] is a flight.
            val flightLeg = mutableListOf<Boolean>()
            val cameraPoints = mutableListOf<LatLng>()

            fun addPoint(point: LatLng, transportInternal: Boolean, flight: Boolean = false) {
                orderedPoints.add(point)
                skipNextYellow.add(transportInternal)
                flightLeg.add(flight)
                cameraPoints.add(point)
            }

            val sortedSegments = segments.sortedBy { it.sortOrder }
            val photosBySegment = photos.groupBy { it.segmentId }

            photosBySegment[null]?.sortedBy { it.takenAt ?: Long.MAX_VALUE }?.forEach { photo ->
                locations[photo.id]?.let { addPoint(it, false) }
            }

            sortedSegments.forEach { segment ->
                if (segment.type == TransportType.ACCOMMODATION) {
                    val point = segment.departureLatLng()
                        ?: PlaceCoordinateResolver.resolve(segment.departureName.ifBlank { fallbackDestination })
                    addPoint(point, false)
                    addHotelMarker(point)
                } else {
                    val start = segment.departureLatLng()
                        ?: PlaceCoordinateResolver.resolve(segment.departureName.ifBlank { fallbackDestination })
                    val end = segment.arrivalLatLng()
                        ?: PlaceCoordinateResolver.resolve(segment.arrivalName.ifBlank { fallbackDestination })
                    drawTransportLeg(segment.type, start, end)
                    addPoint(start, transportInternal = true, flight = segment.type == TransportType.FLIGHT)
                    addPoint(end, transportInternal = false)
                }
                photosBySegment[segment.id]?.sortedBy { it.takenAt ?: Long.MAX_VALUE }?.forEach { photo ->
                    locations[photo.id]?.let { addPoint(it, false) }
                }
            }

            drawYellowConnectors(orderedPoints, skipNextYellow)
            startRoutePulse(orderedPoints, flightLeg)
            renderPhotoGroups(groups)
            groups.forEach { cameraPoints.add(it.center) }

            installInteractions()
            applyScale()
            fitCamera(cameraPoints, fallbackDestination)
        }
    }

    // region transport / accommodation -------------------------------------------------------

    private fun drawTransportLeg(type: TransportType, start: LatLng, end: LatLng) {
        val color = type.routeColor()
        val isFlight = type == TransportType.FLIGHT
        val sLng = start.longitude
        val eLng = unwrapLng(sLng, end.longitude)
        val cLat = (start.latitude + end.latitude) / 2.0 + -(eLng - sLng) * ARC_LIFT
        val cLng = (sLng + eLng) / 2.0 + (end.latitude - start.latitude) * ARC_LIFT

        val path = if (isFlight) {
            (0..ARC_STEPS).map { step ->
                bezier(start.latitude, sLng, cLat, cLng, end.latitude, eLng, step.toFloat() / ARC_STEPS)
            }
        } else {
            listOf(start, LatLng(end.latitude, eLng))
        }

        val options = PolylineOptions()
            .addAll(path)
            .color(color)
            .width(dpf(2.6f))
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
        if (isFlight) {
            options.pattern(listOf(Dash(dpf(10f)), Gap(dpf(7f))))
        }
        map.addPolyline(options)?.let { polylineInfos.add(PolyInfo(it, dpf(2.6f))) }

        addDot(start, color)
        addDot(LatLng(end.latitude, eLng), color)

        val mover = map.addMarker(
            MarkerOptions()
                .position(start)
                .icon(BitmapDescriptorFactory.fromBitmap(transportIconBase(type, color)))
                .anchor(0.5f, 0.5f)
                .zIndex(50f),
        ) ?: return
        scalables.add(Scalable(mover, transportIconBase(type, color)))

        val duration = travelDuration(haversine(start.latitude, sLng, end.latitude, eLng))
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                val point = if (isFlight) {
                    bezier(start.latitude, sLng, cLat, cLng, end.latitude, eLng, t)
                } else {
                    LatLng(start.latitude + (end.latitude - start.latitude) * t, sLng + (eLng - sLng) * t)
                }
                runCatching { mover.position = point }
            }
            start()
        }
        animators.add(animator)
    }

    private fun addDot(position: LatLng, color: Int) {
        map.addMarker(
            MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(dotBase(color)))
                .anchor(0.5f, 0.5f)
                .zIndex(20f),
        )?.let { scalables.add(Scalable(it, dotBase(color))) }
    }

    private fun addHotelMarker(point: LatLng) {
        val base = hotelBase(0xFFC084FC.toInt())
        map.addMarker(
            MarkerOptions()
                .position(point)
                .icon(BitmapDescriptorFactory.fromBitmap(base))
                .anchor(0.5f, 0.5f)
                .zIndex(45f),
        )?.let { scalables.add(Scalable(it, base)) }
    }

    // endregion

    // region yellow route + pulse ------------------------------------------------------------

    private fun drawYellowConnectors(points: List<LatLng>, skipNext: List<Boolean>) {
        for (i in 0 until points.size - 1) {
            if (skipNext[i]) continue
            val options = PolylineOptions()
                .add(points[i], points[i + 1])
                .color(ROUTE_YELLOW)
                .width(dpf(2.2f))
                .startCap(RoundCap())
                .endCap(RoundCap())
            map.addPolyline(options)?.let { polylineInfos.add(PolyInfo(it, dpf(2.2f))) }
        }
    }

    private fun startRoutePulse(points: List<LatLng>, flightLeg: List<Boolean>) {
        if (points.size < 2) return
        // Build a continuous, unwrapped path so the pulse moves smoothly across the antimeridian.
        val latArr = DoubleArray(points.size)
        val lngArr = DoubleArray(points.size)
        latArr[0] = points[0].latitude
        lngArr[0] = points[0].longitude
        for (i in 1 until points.size) {
            latArr[i] = points[i].latitude
            lngArr[i] = unwrapLng(lngArr[i - 1], points[i].longitude)
        }
        // Flight legs contribute zero length so the pulse skips straight across them instead of
        // tracing the flight arc; the moving plane icon already conveys the flight itself.
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val legLength = if (flightLeg.getOrElse(i - 1) { false }) {
                0.0
            } else {
                haversine(latArr[i - 1], lngArr[i - 1], latArr[i], lngArr[i])
            }
            cum[i] = cum[i - 1] + legLength
        }
        val total = cum.last()
        if (total <= 0.0) return

        val base = glowBase()
        val glow = map.addMarker(
            MarkerOptions()
                .position(points[0])
                .icon(BitmapDescriptorFactory.fromBitmap(base))
                .anchor(0.5f, 0.5f)
                .zIndex(15f)
                .flat(true),
        ) ?: return
        scalables.add(Scalable(glow, base))

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (total / 1000.0 * 130.0).toLong().coerceIn(4000L, 14000L)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val target = (va.animatedValue as Float) * total
                var i = 0
                while (i < cum.size - 1 && cum[i + 1] < target) i++
                val span = (cum[i + 1] - cum[i]).coerceAtLeast(1e-6)
                val f = ((target - cum[i]) / span).coerceIn(0.0, 1.0)
                val lat = latArr[i] + (latArr[i + 1] - latArr[i]) * f
                val lng = lngArr[i] + (lngArr[i + 1] - lngArr[i]) * f
                runCatching { glow.position = LatLng(lat, lng) }
            }
            start()
        }
        animators.add(animator)
    }

    // endregion

    // region photos --------------------------------------------------------------------------

    private fun renderPhotoGroups(groups: List<PhotoGroup>) {
        groups.forEach { group ->
            val top = group.photos.first()
            val base = buildPhotoCardBitmap(photoBitmaps[top.uri], captionFor(top), group.photos.size)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(group.center)
                    .icon(BitmapDescriptorFactory.fromBitmap(base))
                    .anchor(0.5f, 1f)
                    .zIndex(40f),
            ) ?: return@forEach
            scalables.add(Scalable(marker, base))
            groupByMarker[marker] = group
        }
    }

    private fun installInteractions() {
        map.setOnMarkerClickListener { marker ->
            val group = groupByMarker[marker]
            when {
                group != null && group.photos.size == 1 -> onPhotoClick?.invoke(group.photos.first())
                group != null -> if (expandedGroup == group) collapseFan() else expandFan(marker, group)
                else -> fannedPhotoByMarker[marker]?.let { onPhotoClick?.invoke(it) }
            }
            true
        }
        map.setOnCameraMoveStartedListener { collapseFan() }
        map.setOnCameraIdleListener {
            val scale = scaleForZoom(map.cameraPosition.zoom)
            if (abs(scale - currentScale) > 0.04f) {
                currentScale = scale
                applyScale()
            }
        }
    }

    private fun expandFan(marker: Marker, group: PhotoGroup) {
        collapseFan()
        expandedGroup = group
        expandedMarker = marker
        // Hide the stacked card so only the unfolded originals are visible while expanded.
        runCatching { marker.isVisible = false }
        val projection = map.projection
        val center = projection.toScreenLocation(group.center)
        val count = group.photos.size
        val radius = dp(70f) + dp(12f) * count
        group.photos.forEachIndexed { index, photo ->
            val fraction = if (count == 1) 0.5 else index.toDouble() / (count - 1)
            val angle = Math.toRadians(-200.0 + 220.0 * fraction)
            val targetPoint = android.graphics.Point(
                (center.x + radius * cos(angle)).toInt(),
                (center.y + radius * sin(angle)).toInt(),
            )
            val target = projection.fromScreenLocation(targetPoint)
            val base = buildPhotoCardBitmap(photoBitmaps[photo.uri], captionFor(photo), 1)
            val child = map.addMarker(
                MarkerOptions()
                    .position(group.center)
                    .icon(BitmapDescriptorFactory.fromBitmap(scaleBitmap(base, currentScale)))
                    .anchor(0.5f, 1f)
                    .zIndex(60f + index),
            ) ?: return@forEachIndexed
            fannedMarkers.add(child)
            fannedPhotoByMarker[child] = photo
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 240L
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener { va ->
                    val t = va.animatedValue as Float
                    runCatching {
                        child.position = LatLng(
                            group.center.latitude + (target.latitude - group.center.latitude) * t,
                            group.center.longitude + (target.longitude - group.center.longitude) * t,
                        )
                    }
                }
                start()
            }.also { fanAnimators.add(it) }
        }
    }

    private fun collapseFan() {
        // Stop any in-flight fan animation so it can't fight the collapse over marker positions.
        fanAnimators.forEach { it.cancel() }
        fanAnimators.clear()
        val markers = fannedMarkers.toList()
        val center = expandedGroup?.center
        val stackMarker = expandedMarker
        // Reset state up front so a re-entrant expand/collapse during the animation stays consistent.
        fannedMarkers.clear()
        fannedPhotoByMarker.clear()
        expandedGroup = null
        expandedMarker = null
        // Bring the stacked card back so the fanned cards visually merge into it.
        runCatching { stackMarker?.isVisible = true }
        if (markers.isEmpty()) return
        if (center == null) {
            markers.forEach { runCatching { it.remove() } }
            return
        }
        markers.forEach { child ->
            val from = child.position
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    val t = va.animatedValue as Float
                    runCatching {
                        child.position = LatLng(
                            from.latitude + (center.latitude - from.latitude) * t,
                            from.longitude + (center.longitude - from.longitude) * t,
                        )
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        runCatching { child.remove() }
                    }
                })
                start()
            }.also { fanAnimators.add(it) }
        }
    }

    private fun resolvePhotoLocations(
        photos: List<TravelPhoto>,
        segments: List<TransportSegment>,
        fallbackDestination: String,
    ): Map<Long, LatLng> {
        val result = mutableMapOf<Long, LatLng>()
        photos.forEachIndexed { index, photo ->
            val location = when {
                photo.hasLocation -> LatLng(photo.lat!!, photo.lng!!)
                else -> {
                    val previous = photos.take(index).lastOrNull { it.hasLocation }
                    val next = photos.drop(index + 1).firstOrNull { it.hasLocation }
                    when {
                        previous != null && next != null -> midpoint(
                            LatLng(previous.lat!!, previous.lng!!),
                            LatLng(next.lat!!, next.lng!!),
                        )
                        else -> {
                            val segment = segments.firstOrNull { it.id == photo.segmentId }
                            if (segment != null) {
                                midpoint(
                                    segment.departureLatLng() ?: PlaceCoordinateResolver.resolve(segment.departureName),
                                    segment.arrivalLatLng() ?: PlaceCoordinateResolver.resolve(segment.arrivalName),
                                )
                            } else {
                                PlaceCoordinateResolver.resolve(fallbackDestination)
                            }
                        }
                    }
                }
            }
            result[photo.id] = location
        }
        return result
    }

    private fun groupPhotos(photos: List<TravelPhoto>, locations: Map<Long, LatLng>): List<PhotoGroup> {
        val groups = mutableListOf<MutableList<TravelPhoto>>()
        val centers = mutableListOf<LatLng>()
        photos.forEach { photo ->
            val location = locations[photo.id] ?: return@forEach
            val idx = centers.indexOfFirst { haversine(it.latitude, it.longitude, location.latitude, location.longitude) < GROUP_THRESHOLD_M }
            if (idx >= 0) {
                groups[idx].add(photo)
            } else {
                groups.add(mutableListOf(photo))
                centers.add(location)
            }
        }
        return groups.mapIndexed { i, members -> PhotoGroup(centers[i], members) }
    }

    // endregion

    // region scaling -------------------------------------------------------------------------

    private fun applyScale() {
        scalables.forEach { it.marker.setIcon(BitmapDescriptorFactory.fromBitmap(scaleBitmap(it.base, currentScale))) }
        polylineInfos.forEach { it.polyline.width = (it.baseWidthPx * currentScale).coerceAtLeast(1.5f) }
    }

    private fun scaleBitmap(base: Bitmap, scale: Float): Bitmap {
        if (abs(scale - 1f) < 0.01f) return base
        val w = (base.width * scale).toInt().coerceAtLeast(1)
        val h = (base.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(base, w, h, true)
    }

    private fun scaleForZoom(zoom: Float): Float = (zoom / 5.5f).coerceIn(0.6f, 1.25f)

    // endregion

    // region bitmap builders -----------------------------------------------------------------

    private fun loadBitmap(uri: String): Bitmap? = runCatching {
        Glide.with(context)
            .asBitmap()
            .load(Uri.parse(uri))
            .centerCrop()
            .override(PHOTO_PX, (PHOTO_PX * 0.78f).toInt())
            .submit()
            .get()
    }.getOrNull()

    private fun buildPhotoCardBitmap(photo: Bitmap?, caption: String, count: Int): Bitmap {
        val view = LayoutInflater.from(context).inflate(R.layout.view_photo_marker, null)
        val image = view.findViewById<ImageView>(R.id.photoMarkerImage)
        val captionView = view.findViewById<TextView>(R.id.photoMarkerCaption)
        if (photo != null) image.setImageBitmap(photo)
        if (caption.isBlank()) captionView.visibility = View.GONE else captionView.text = caption
        val card = viewToBitmap(view)
        if (count <= 1) return card

        val offset = dp(4f)
        val out = Bitmap.createBitmap(card.width + offset * 2, card.height + offset * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for (i in 2 downTo 1) {
            val o = (offset * i).toFloat()
            fillPaint.color = Color.parseColor("#FFFDFDFB")
            strokePaint.color = Color.parseColor("#FFE2DED2")
            val rect = RectF(o, o, o + card.width, o + card.height)
            canvas.drawRoundRect(rect, dpf(8f), dpf(8f), fillPaint)
            canvas.drawRoundRect(rect, dpf(8f), dpf(8f), strokePaint)
        }
        canvas.drawBitmap(card, 0f, 0f, null)
        drawBadge(canvas, card.width.toFloat(), count)
        return out
    }

    private fun drawBadge(canvas: Canvas, cardRight: Float, count: Int) {
        val r = dpf(9f)
        val cx = cardRight - dpf(2f)
        val cy = dpf(2f) + r
        fillPaint.color = ROUTE_YELLOW
        canvas.drawCircle(cx, cy, r, fillPaint)
        textPaint.color = Color.parseColor("#FF2A2A2A")
        textPaint.textSize = dpf(10f)
        textPaint.isFakeBoldText = true
        val text = if (count > 9) "9+" else count.toString()
        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, cx, cy + bounds.height() / 2f, textPaint)
    }

    private fun viewToBitmap(view: View): Bitmap {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bmp = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun transportIconBase(type: TransportType, color: Int): Bitmap {
        val sizePx = dp(34f)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = sizePx / 2f
        fillPaint.color = Color.WHITE
        canvas.drawCircle(r, r, r, fillPaint)
        fillPaint.color = color
        canvas.drawCircle(r, r, r - dpf(2.5f), fillPaint)
        val iconRes = when (type) {
            TransportType.FLIGHT -> R.drawable.ic_marker_flight
            TransportType.TRAIN -> R.drawable.ic_marker_train
            TransportType.CAR -> R.drawable.ic_marker_car
            TransportType.ACCOMMODATION -> R.drawable.ic_marker_hotel
        }
        drawIcon(canvas, iconRes, sizePx, 0.52f)
        return bmp
    }

    private fun hotelBase(color: Int): Bitmap {
        val sizePx = dp(32f)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = sizePx / 2f
        fillPaint.color = Color.WHITE
        canvas.drawCircle(r, r, r, fillPaint)
        fillPaint.color = color
        canvas.drawCircle(r, r, r - dpf(2.5f), fillPaint)
        drawIcon(canvas, R.drawable.ic_marker_hotel, sizePx, 0.54f)
        return bmp
    }

    private fun drawIcon(canvas: Canvas, iconRes: Int, sizePx: Int, ratio: Float) {
        val drawable = ContextCompat.getDrawable(context, iconRes)!!.mutate()
        DrawableCompat.setTint(drawable, Color.WHITE)
        val iconSize = (sizePx * ratio).toInt()
        val left = (sizePx - iconSize) / 2
        drawable.setBounds(left, left, left + iconSize, left + iconSize)
        drawable.draw(canvas)
    }

    private fun dotBase(color: Int): Bitmap {
        val sizePx = dp(10f)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = sizePx / 2f
        fillPaint.color = Color.WHITE
        canvas.drawCircle(r, r, r, fillPaint)
        fillPaint.color = color
        canvas.drawCircle(r, r, r - dpf(2f), fillPaint)
        return bmp
    }

    private fun glowBase(): Bitmap {
        val sizePx = dp(26f)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = sizePx / 2f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.shader = RadialGradient(
            r, r, r,
            intArrayOf(0xFFFFF6C8.toInt(), 0x88FFE08A.toInt(), 0x00FFE08A),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(r, r, r, glowPaint)
        return bmp
    }

    // endregion

    // region camera & geometry ---------------------------------------------------------------

    private fun fitCamera(points: List<LatLng>, fallbackDestination: String) {
        val unique = points.distinct()
        when {
            unique.isEmpty() -> map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(PlaceCoordinateResolver.resolve(fallbackDestination), 9f),
            )
            unique.size == 1 -> map.animateCamera(CameraUpdateFactory.newLatLngZoom(unique.first(), 11f))
            else -> {
                val builder = LatLngBounds.Builder()
                unique.forEach { builder.include(it) }
                runCatching {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), dp(64f)))
                }.onFailure {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(unique.first(), 9f))
                }
            }
        }
    }

    private fun captionFor(photo: TravelPhoto): String = photo.comment?.takeIf { it.isNotBlank() }.orEmpty()

    private fun TransportSegment.departureLatLng(): LatLng? =
        if (departureLat != null && departureLng != null) LatLng(departureLat, departureLng) else null

    private fun TransportSegment.arrivalLatLng(): LatLng? =
        if (arrivalLat != null && arrivalLng != null) LatLng(arrivalLat, arrivalLng) else null

    private fun TransportType.routeColor(): Int = when (this) {
        TransportType.FLIGHT -> 0xFF7DD3FC.toInt()
        TransportType.TRAIN -> 0xFF66BB6A.toInt()
        TransportType.CAR -> 0xFF42A5F5.toInt()
        TransportType.ACCOMMODATION -> 0xFFC084FC.toInt()
    }

    private fun midpoint(a: LatLng, b: LatLng): LatLng =
        LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

    private fun bezier(
        aLat: Double, aLng: Double,
        cLat: Double, cLng: Double,
        bLat: Double, bLng: Double,
        t: Float,
    ): LatLng {
        val mt = 1 - t
        return LatLng(
            mt * mt * aLat + 2 * mt * t * cLat + t * t * bLat,
            mt * mt * aLng + 2 * mt * t * cLng + t * t * bLng,
        )
    }

    private fun unwrapLng(fromLng: Double, toLng: Double): Double {
        var adjusted = toLng
        while (adjusted - fromLng > 180.0) adjusted -= 360.0
        while (adjusted - fromLng < -180.0) adjusted += 360.0
        return adjusted
    }

    private fun haversine(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val meanLat = Math.toRadians((aLat + bLat) / 2.0)
        val x = dLng * cos(meanLat)
        return sqrt(x * x + dLat * dLat) * 6_371_000.0
    }

    private fun travelDuration(distanceMeters: Double): Long =
        (distanceMeters / 1000.0 * 22.0).toLong().coerceIn(3500L, 9000L)

    private fun dp(value: Float): Int = (value * density).toInt()

    private fun dpf(value: Float): Float = value * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // endregion

    companion object {
        private const val ARC_STEPS = 64
        private const val ARC_LIFT = 0.16
        private const val GROUP_THRESHOLD_M = 350.0
        private const val PHOTO_PX = 300
        private val ROUTE_YELLOW = 0xFFFFD166.toInt()
    }
}

package com.pnu.orbit.ui.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LowPolyEarthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var kind = EarthKind.MY
    private var rotationLon = 18f
    private var rotationLat = -8f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val spinRunnable = object : Runnable {
        override fun run() {
            rotationLon = (rotationLon + 0.18f) % 360f
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setEarthKind(kind: EarthKind) {
        this.kind = kind
        rotationLon = when (kind) {
            EarthKind.MY -> 18f
            EarthKind.FRIENDS -> 134f
            EarthKind.WORLD -> 248f
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postOnAnimation(spinRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(spinRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val radius = size * 0.38f
        val cx = width / 2f
        val cy = height / 2f

        drawGlow(canvas, cx, cy, radius)
        canvas.save()
        path.reset()
        path.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(path)
        drawOceanFacets(canvas, cx, cy, radius)
        drawLandMasses(canvas, cx, cy, radius)
        drawPolarCaps(canvas, cx, cy, radius)
        drawSphereLight(canvas, cx, cy, radius)
        canvas.restore()
        drawRim(canvas, cx, cy, radius)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    dragging = true
                }
                rotationLon = (rotationLon + dx * 0.45f) % 360f
                rotationLat = (rotationLat - dy * 0.25f).coerceIn(-28f, 28f)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.shader = RadialGradient(
            cx + radius * 0.16f,
            cy - radius * 0.1f,
            radius * 1.7f,
            intArrayOf(glowColor(), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * 1.55f, paint)
        paint.shader = null
    }

    private fun drawOceanFacets(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val style = palette()
        for (lat in -80 until 80 step 20) {
            for (lon in -180 until 180 step 30) {
                drawTriangle(
                    canvas,
                    listOf(
                        project(lat.toFloat(), lon.toFloat(), cx, cy, radius),
                        project((lat + 20).toFloat(), lon.toFloat(), cx, cy, radius),
                        project(lat.toFloat(), (lon + 30).toFloat(), cx, cy, radius),
                    ),
                    blend(style.oceanDark, style.oceanLight, shade(lat, lon)),
                )
                drawTriangle(
                    canvas,
                    listOf(
                        project((lat + 20).toFloat(), lon.toFloat(), cx, cy, radius),
                        project((lat + 20).toFloat(), (lon + 30).toFloat(), cx, cy, radius),
                        project(lat.toFloat(), (lon + 30).toFloat(), cx, cy, radius),
                    ),
                    blend(style.oceanDark, style.oceanLight, shade(lat + 9, lon + 13)),
                )
            }
        }
    }

    private fun drawLandMasses(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        landPolygons.forEachIndexed { index, polygon ->
            val points = polygon.mapNotNull { project(it.lat, it.lon, cx, cy, radius) }
            if (points.size < 3) return@forEachIndexed

            val landPath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }

            canvas.save()
            canvas.translate(radius * 0.018f, radius * 0.026f)
            paint.color = Color.argb(96, 29, 84, 38)
            paint.style = Paint.Style.FILL
            canvas.drawPath(landPath, paint)
            canvas.restore()

            paint.color = blend(palette().landDark, palette().landLight, 0.42f + index * 0.08f)
            canvas.drawPath(landPath, paint)
            drawLandFacets(canvas, points, index)
        }
    }

    private fun drawLandFacets(canvas: Canvas, points: List<ScreenPoint>, index: Int) {
        val center = PointF(
            points.sumOf { it.x.toDouble() }.toFloat() / points.size,
            points.sumOf { it.y.toDouble() }.toFloat() / points.size,
        )
        for (i in points.indices) {
            val next = points[(i + 1) % points.size]
            path.reset()
            path.moveTo(center.x, center.y)
            path.lineTo(points[i].x, points[i].y)
            path.lineTo(next.x, next.y)
            path.close()
            paint.color = blend(
                palette().landDark,
                palette().landLight,
                0.25f + ((i + index) % 4) * 0.14f,
            )
            paint.alpha = 180
            canvas.drawPath(path, paint)
            paint.alpha = 255
        }
    }

    private fun drawPolarCaps(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        listOf(72f, -72f).forEach { lat ->
            val center = project(lat, rotationLon, cx, cy, radius) ?: return@forEach
            if (center.z < 0.05f) return@forEach
            paint.color = Color.argb((130 * center.z.coerceIn(0f, 1f)).toInt(), 255, 255, 255)
            canvas.drawCircle(center.x, center.y, radius * 0.11f, paint)
        }
    }

    private fun drawSphereLight(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.shader = RadialGradient(
            cx - radius * 0.45f,
            cy - radius * 0.55f,
            radius * 1.55f,
            intArrayOf(Color.argb(84, 255, 255, 255), Color.TRANSPARENT, Color.argb(106, 0, 0, 0)),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    private fun drawRim(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.035f
        paint.shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            intArrayOf(Color.argb(185, 255, 255, 255), Color.argb(70, 70, 198, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun drawTriangle(canvas: Canvas, points: List<ScreenPoint?>, color: Int) {
        val visible = points.filterNotNull()
        if (visible.size != 3 || visible.any { it.z < -0.08f }) return
        path.reset()
        path.moveTo(visible[0].x, visible[0].y)
        path.lineTo(visible[1].x, visible[1].y)
        path.lineTo(visible[2].x, visible[2].y)
        path.close()
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    private fun project(latDeg: Float, lonDeg: Float, cx: Float, cy: Float, radius: Float): ScreenPoint? {
        val lat = Math.toRadians(latDeg.toDouble())
        val lon = Math.toRadians((lonDeg + rotationLon).toDouble())
        val tilt = Math.toRadians(rotationLat.toDouble())
        val x3 = cos(lat) * sin(lon)
        val y3 = sin(lat)
        val z3 = cos(lat) * cos(lon)
        val yTilted = y3 * cos(tilt) - z3 * sin(tilt)
        val zTilted = y3 * sin(tilt) + z3 * cos(tilt)
        if (zTilted < -0.18) return null

        val depthScale = 0.9f + (zTilted.toFloat() + 1f) * 0.05f
        return ScreenPoint(
            x = cx + (x3.toFloat() * radius * depthScale),
            y = cy - (yTilted.toFloat() * radius * depthScale),
            z = zTilted.toFloat(),
        )
    }

    private fun shade(lat: Int, lon: Int): Float {
        val wave = sin((lat * 1.7 + lon * 0.43).toDouble()).toFloat()
        return (0.44f + wave * 0.22f).coerceIn(0f, 1f)
    }

    private fun palette(): EarthPalette = when (kind) {
        EarthKind.MY -> EarthPalette(
            oceanDark = Color.rgb(17, 109, 176),
            oceanLight = Color.rgb(45, 186, 238),
            landDark = Color.rgb(62, 166, 60),
            landLight = Color.rgb(149, 230, 68),
        )
        EarthKind.FRIENDS -> EarthPalette(
            oceanDark = Color.rgb(58, 96, 196),
            oceanLight = Color.rgb(80, 202, 229),
            landDark = Color.rgb(80, 168, 96),
            landLight = Color.rgb(183, 224, 86),
        )
        EarthKind.WORLD -> EarthPalette(
            oceanDark = Color.rgb(19, 133, 159),
            oceanLight = Color.rgb(91, 205, 231),
            landDark = Color.rgb(79, 161, 82),
            landLight = Color.rgb(221, 210, 85),
        )
    }

    private fun glowColor(): Int = when (kind) {
        EarthKind.MY -> Color.argb(115, 59, 210, 255)
        EarthKind.FRIENDS -> Color.argb(90, 149, 133, 255)
        EarthKind.WORLD -> Color.argb(92, 118, 232, 144)
    }

    private fun blend(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * f).toInt(),
            (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * f).toInt(),
            (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * f).toInt(),
        )
    }

    enum class EarthKind {
        MY,
        FRIENDS,
        WORLD,
    }

    private data class ScreenPoint(val x: Float, val y: Float, val z: Float)
    private data class EarthPalette(
        val oceanDark: Int,
        val oceanLight: Int,
        val landDark: Int,
        val landLight: Int,
    )
    private data class GeoPoint(val lat: Float, val lon: Float)

    private companion object {
        val landPolygons = listOf(
            listOf(
                GeoPoint(66f, -132f),
                GeoPoint(58f, -94f),
                GeoPoint(46f, -62f),
                GeoPoint(18f, -72f),
                GeoPoint(-12f, -78f),
                GeoPoint(-55f, -66f),
                GeoPoint(-34f, -78f),
                GeoPoint(4f, -110f),
                GeoPoint(38f, -126f),
            ),
            listOf(
                GeoPoint(62f, -10f),
                GeoPoint(58f, 38f),
                GeoPoint(50f, 92f),
                GeoPoint(32f, 124f),
                GeoPoint(7f, 102f),
                GeoPoint(-5f, 72f),
                GeoPoint(-35f, 34f),
                GeoPoint(-28f, 10f),
                GeoPoint(10f, -18f),
            ),
            listOf(
                GeoPoint(80f, -58f),
                GeoPoint(74f, -24f),
                GeoPoint(61f, -36f),
                GeoPoint(63f, -67f),
            ),
            listOf(
                GeoPoint(-12f, 112f),
                GeoPoint(-16f, 154f),
                GeoPoint(-39f, 146f),
                GeoPoint(-35f, 116f),
            ),
        )
    }
}

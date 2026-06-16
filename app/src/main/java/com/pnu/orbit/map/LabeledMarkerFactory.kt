package com.pnu.orbit.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Builds map-marker bitmaps that show a place's name right next to the pin, so users can read what
 * each marker is without tapping it one by one. The descriptor must be anchored at (0.5, 1.0) — the
 * tip of the pointer then sits exactly on the coordinate.
 */
object LabeledMarkerFactory {

    /** Recommended marker anchor: horizontal centre, vertical bottom (pointer tip). */
    const val ANCHOR_U = 0.5f
    const val ANCHOR_V = 1.0f

    fun create(
        context: Context,
        label: String,
        accentColor: Int,
        iconRes: Int? = null,
    ): BitmapDescriptor {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Float = value * density

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF202124")
            textSize = dp(13f)
            isFakeBoldText = true
        }
        val displayLabel = ellipsize(label.ifBlank { " " }, textPaint, dp(150f))
        val textWidth = textPaint.measureText(displayLabel)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val padH = dp(9f)
        val padV = dp(6f)
        val iconSize = if (iconRes != null) dp(16f) else 0f
        val iconGap = if (iconRes != null) dp(5f) else 0f
        val stroke = dp(1.5f)

        val chipWidth = padH * 2 + iconSize + iconGap + textWidth
        val chipHeight = padV * 2 + textHeight
        val pointerH = dp(7f)
        val pointerW = dp(11f)

        val width = (chipWidth + stroke * 2).toInt().coerceAtLeast(1)
        val height = (chipHeight + pointerH + stroke * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = accentColor
        }

        val left = stroke
        val top = stroke
        val right = left + chipWidth
        val bottom = top + chipHeight
        val radius = dp(8f)
        val chipRect = RectF(left, top, right, bottom)
        val cx = (left + right) / 2f

        val pointer = Path().apply {
            moveTo(cx - pointerW / 2f, bottom - dp(1f))
            lineTo(cx + pointerW / 2f, bottom - dp(1f))
            lineTo(cx, bottom + pointerH)
            close()
        }

        canvas.drawRoundRect(chipRect, radius, radius, fillPaint)
        canvas.drawPath(pointer, fillPaint)
        canvas.drawRoundRect(chipRect, radius, radius, borderPaint)
        canvas.drawPath(pointer, borderPaint)

        var contentLeft = left + padH
        if (iconRes != null) {
            ContextCompat.getDrawable(context, iconRes)?.mutate()?.let { drawable ->
                DrawableCompat.setTint(drawable, accentColor)
                val iconTop = top + (chipHeight - iconSize) / 2f
                drawable.setBounds(
                    contentLeft.toInt(),
                    iconTop.toInt(),
                    (contentLeft + iconSize).toInt(),
                    (iconTop + iconSize).toInt(),
                )
                drawable.draw(canvas)
            }
            contentLeft += iconSize + iconGap
        }
        canvas.drawText(displayLabel, contentLeft, top + padV - fm.ascent, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + "…"
    }
}

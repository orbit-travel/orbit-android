package com.pnu.orbit.ui.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SpaceBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(STAR_COUNT) { index ->
        Star(
            xRatio = pseudoRandom(index, 17),
            yRatio = pseudoRandom(index, 43),
            radiusRatio = 0.0014f + pseudoRandom(index, 71) * 0.0028f,
            alpha = 70 + (pseudoRandom(index, 91) * 150).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(Color.rgb(3, 5, 12), Color.rgb(12, 18, 32), Color.rgb(4, 6, 14)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = RadialGradient(
            width * 0.55f,
            height * 0.18f,
            min(width, height) * 0.55f,
            intArrayOf(Color.argb(62, 70, 150, 255), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        stars.forEach { star ->
            paint.color = Color.argb(star.alpha, 255, 255, 255)
            canvas.drawCircle(
                width * star.xRatio,
                height * star.yRatio,
                min(width, height) * star.radiusRatio,
                paint,
            )
        }
    }

    private fun pseudoRandom(index: Int, salt: Int): Float {
        val value = (index * 1103515245L + salt * 12345L + 67890L) and 0x7fffffff
        return (value % 10000L).toFloat() / 10000f
    }

    private data class Star(
        val xRatio: Float,
        val yRatio: Float,
        val radiusRatio: Float,
        val alpha: Int,
    )

    companion object {
        private const val STAR_COUNT = 72
    }
}

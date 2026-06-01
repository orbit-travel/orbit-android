package com.pnu.orbit.ui.record

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import kotlin.math.abs

class EarthModelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val sceneView = SceneView(context)
    private var modelNode: ModelNode? = null
    private var variant = EarthVariant.MY
    private var rotationX = -12f
    private var rotationY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastEventTimeMs = 0L
    private var dragging = false
    private var isTouching = false
    private var rotationVelocityY = 0f

    private val spinRunnable = object : Runnable {
        override fun run() {
            if (!isTouching) {
                rotationY = (rotationY + rotationVelocityY) % 360f
                rotationVelocityY += (variant.spinSpeed - rotationVelocityY) * INERTIA_DECAY
                applyModelRotation()
            }
            postOnAnimation(this)
        }
    }

    init {
        isClickable = true
        clipChildren = false
        clipToPadding = false

        sceneView.setBackgroundColor(Color.TRANSPARENT)
        sceneView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        sceneView.setOnTouchListener { _, event -> handleSceneTouch(event) }
        addView(
            sceneView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        setEarthVariant(EarthVariant.MY)
    }

    fun setEarthVariant(variant: EarthVariant) {
        this.variant = variant
        rotationY = variant.initialRotationY
        rotationVelocityY = variant.spinSpeed
        applyModelRotation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { loadModelIfNeeded() }
        postOnAnimation(spinRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(spinRunnable)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun loadModelIfNeeded() {
        if (modelNode != null) return

        runCatching {
            val node = ModelNode(
                modelInstance = sceneView.modelLoader.createModelInstance(
                    assetFileLocation = MODEL_ASSET,
                ),
                autoAnimate = true,
                scaleToUnits = variant.scaleToUnits,
            ).apply {
                isTouchable = false
                isEditable = false
                rotation = Float3(rotationX, rotationY, 0f)
            }

            sceneView.cameraNode.position = Float3(0f, 0f, variant.cameraZ)
            sceneView.cameraNode.lookAt(Float3(0f, 0f, 0f))
            sceneView.addChildNode(node)
            modelNode = node
        }.onFailure {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun handleSceneTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                lastEventTimeMs = event.eventTime
                dragging = false
                isTouching = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                val dtMs = (event.eventTime - lastEventTimeMs).coerceAtLeast(1L)
                val degreesY = dx * DRAG_ROTATION_FACTOR
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    dragging = true
                }
                rotationY = (rotationY + degreesY) % 360f
                rotationX = (rotationX - dy * 0.22f).coerceIn(-38f, 38f)
                rotationVelocityY = (degreesY / dtMs * FRAME_MS).coerceIn(-MAX_INERTIA_SPEED, MAX_INERTIA_SPEED)
                lastX = event.x
                lastY = event.y
                lastEventTimeMs = event.eventTime
                applyModelRotation()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isTouching = false
                if (!dragging) {
                    rotationVelocityY = variant.spinSpeed
                }
                if (!dragging) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isTouching = false
                return true
            }
        }
        return true
    }

    private fun applyModelRotation() {
        modelNode?.rotation = Float3(rotationX, rotationY, 0f)
    }

    enum class EarthVariant(
        val initialRotationY: Float,
        val spinSpeed: Float,
        val scaleToUnits: Float,
        val cameraZ: Float,
    ) {
        MY(
            initialRotationY = 0f,
            spinSpeed = 0.035f,
            scaleToUnits = 4.35f,
            cameraZ = 3.3f,
        ),
        FRIENDS(
            initialRotationY = 120f,
            spinSpeed = 0.03f,
            scaleToUnits = 4.35f,
            cameraZ = 3.3f,
        ),
        WORLD(
            initialRotationY = 240f,
            spinSpeed = 0.028f,
            scaleToUnits = 4.35f,
            cameraZ = 3.3f,
        ),
    }

    companion object {
        private const val MODEL_ASSET = "models/planet_earth.glb"
        private const val DRAG_ROTATION_FACTOR = 0.2f
        private const val FRAME_MS = 16.666f
        private const val MAX_INERTIA_SPEED = 1.8f
        private const val INERTIA_DECAY = 0.025f
    }
}

package com.jarvis.assistant.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class VoiceVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#00BCD4")
        strokeWidth = 4f
        isAntiAlias = true
    }

    private var amplitude = 0f
    private var isAnimating = false

    fun setAmplitude(amplitude: Float) {
        this.amplitude = amplitude
        if (!isAnimating) {
            startAnimation()
        }
        invalidate()
    }

    private fun startAnimation() {
        isAnimating = true
        post(object : Runnable {
            override fun run() {
                if (isAnimating) {
                    invalidate()
                    postDelayed(this, 50)
                }
            }
        })
    }

    fun stopAnimation() {
        isAnimating = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 4f) * (1 + amplitude * 0.5f)

        // Draw animated circles
        for (i in 0..2) {
            val alpha = (255 * (1 - i * 0.3f)).toInt()
            paint.alpha = alpha
            val currentRadius = radius * (1 + i * 0.2f)
            canvas.drawCircle(centerX, centerY, currentRadius, paint)
        }

        // Draw wave effect
        paint.alpha = 128
        val waveCount = 8
        for (i in 0 until waveCount) {
            val angle = (i * 360f / waveCount) * (Math.PI / 180f)
            val waveRadius = radius * (1 + sin(angle + System.currentTimeMillis() * 0.001) * 0.1f)
            val x = centerX + (waveRadius * sin(angle)).toFloat()
            val y = centerY + (waveRadius * cos(angle)).toFloat()
            canvas.drawCircle(x, y, 8f, paint)
        }
    }
}

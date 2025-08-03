package com.example.diaryapp.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.animation.doOnEnd
import kotlin.math.max
import kotlin.math.min
import android.graphics.Typeface

class DayNightSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isNight = false
    private var animProgress = 0f // 0 = day, 1 = night
    private var animator: ValueAnimator? = null
    private var onToggleListener: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
    }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF9C4")
    }

    private val trackRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 80
        val desiredHeight = 56
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        android.util.Log.d("DayNightSwitch", "onDraw() called with animProgress: $animProgress, width: $width, height: $height")
        
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 8f
        val trackRadius = h / 2f
        val thumbRadius = h / 2.5f
        val sunScale = 1f - animProgress
        val sunCX = padding + thumbRadius
        val sunCY = h / 2f
        val moonScale = animProgress
        val moonCX = w - padding - thumbRadius
        val moonCY = h / 2f
        // Track
        trackRect.set(padding, padding, w - padding, h - padding)
        trackPaint.color = blendColors(Color.parseColor("#29B6F6"), Color.parseColor("#222831"), animProgress)
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        // Sun on left (with rays)
        if (sunScale > 0.05f) {
            sunPaint.alpha = (255 * sunScale).toInt().coerceIn(0, 255)
            // Main sun (smaller)
            canvas.drawCircle(sunCX, sunCY, thumbRadius * 0.5f * sunScale, sunPaint)
            // Rays/dots (smaller)
            val rayRadius = thumbRadius * 0.75f * sunScale
            val dotRadius = thumbRadius * 0.08f * sunScale
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val dx = (rayRadius * Math.cos(angle)).toFloat()
                val dy = (rayRadius * Math.sin(angle)).toFloat()
                canvas.drawCircle(sunCX + dx, sunCY + dy, dotRadius, sunPaint)
            }
        }
        // Crescent moon on right
        if (moonScale > 0.05f) {
            moonPaint.alpha = (255 * moonScale).toInt().coerceIn(0, 255)
            // Main moon
            canvas.drawCircle(moonCX, moonCY, thumbRadius * 0.7f * moonScale, moonPaint)
            // Crescent cutout
            val crescentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222831") // match night track color
                alpha = moonPaint.alpha
            }
            canvas.drawCircle(
                moonCX + thumbRadius * 0.28f * moonScale,
                moonCY - thumbRadius * 0.10f * moonScale,
                thumbRadius * 0.55f * moonScale,
                crescentPaint
            )
        }
        // Draw labels
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = h / 4f // slightly smaller text size
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        labelPaint.typeface = Typeface.DEFAULT // remove bold
        val labelY = h / 2f + labelPaint.textSize / 2.5f
        val leftCenter = w / 4f + w * 0.12f // more inward padding
        val rightCenter = 3 * w / 4f - w * 0.12f // more inward padding
        // Fade in/out effect for labels
        val dayAlpha = ((1 - animProgress) * 255).toInt().coerceIn(0, 255)
        val nightAlpha = (animProgress * 255).toInt().coerceIn(0, 255)
        // Day Mode label on right half, centered and padded
        labelPaint.alpha = dayAlpha
        canvas.drawText("Day Mode", rightCenter, labelY, labelPaint)
        // Night Mode label on left half, centered and padded
        labelPaint.alpha = nightAlpha
        canvas.drawText("Night Mode", leftCenter, labelY, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        android.util.Log.d("DayNightSwitch", "onTouchEvent() called with action: ${event.action}")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                android.util.Log.d("DayNightSwitch", "ACTION_DOWN received")
                return true
            }
            MotionEvent.ACTION_UP -> {
                android.util.Log.d("DayNightSwitch", "ACTION_UP received, calling toggle()")
                toggle()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        android.util.Log.d("DayNightSwitch", "onAttachedToWindow() called")
        // Ensure the view is clickable and focusable
        isClickable = true
        isFocusable = true
    }

    private fun toggle() {
        android.util.Log.d("DayNightSwitch", "toggle() called, current isNight: $isNight")
        isNight = !isNight
        android.util.Log.d("DayNightSwitch", "toggle() new isNight: $isNight")
        animateToState(isNight)
        onToggleListener?.invoke(isNight)
    }

    private fun animateToState(night: Boolean) {
        android.util.Log.d("DayNightSwitch", "animateToState() called with night: $night, current animProgress: $animProgress")
        animator?.cancel()
        val start = animProgress
        val end = if (night) 1f else 0f
        android.util.Log.d("DayNightSwitch", "animateToState() start: $start, end: $end")
        animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 800 // Restore original duration for smooth animation
            interpolator = android.view.animation.DecelerateInterpolator()
            
            addUpdateListener { animation ->
                animProgress = animation.animatedValue as Float
                android.util.Log.d("DayNightSwitch", "Animation progress: $animProgress")
                invalidate()
            }
            doOnEnd { 
                android.util.Log.d("DayNightSwitch", "Animation ended")
                animator = null 
            }
            start()
        }
        android.util.Log.d("DayNightSwitch", "Animation started")
    }
    
    /**
     * Override to prevent animation interruption during configuration changes
     */
    override fun onSaveInstanceState(): android.os.Parcelable? {
        val superState = super.onSaveInstanceState()
        android.util.Log.d("DayNightSwitch", "onSaveInstanceState() called")
        return superState
    }
    
    override fun onRestoreInstanceState(state: android.os.Parcelable?) {
        super.onRestoreInstanceState(state)
        android.util.Log.d("DayNightSwitch", "onRestoreInstanceState() called")
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio
        val r = Color.red(from) * inverseRatio + Color.red(to) * ratio
        val g = Color.green(from) * inverseRatio + Color.green(to) * ratio
        val b = Color.blue(from) * inverseRatio + Color.blue(to) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    fun setIsNight(night: Boolean) {
        isNight = night
        animProgress = if (night) 1f else 0f
        invalidate()
    }
    
    /**
     * Initialize the switch state without affecting animation
     */
    fun initializeState(night: Boolean) {
        isNight = night
        animProgress = if (night) 1f else 0f
        invalidate()
    }
    
    /**
     * Update switch state without animation (for external theme changes)
     */
    fun updateStateWithoutAnimation(night: Boolean) {
        isNight = night
        animProgress = if (night) 1f else 0f
        invalidate()
    }

    fun isNight(): Boolean = isNight

    fun setListener(listener: (Boolean) -> Unit) {
        onToggleListener = listener
    }
} 
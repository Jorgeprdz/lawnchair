/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.oneui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorInt
import com.android.launcher3.R
import com.android.launcher3.graphics.OneUiGlassBackground
import kotlin.math.max

/**
 * GradientDrawable-compatible folder surface that can keep Folder.java untouched.
 *
 * Folder still expects a GradientDrawable because it calls setColor(), setAlpha(), setBounds() and
 * draw() directly from dispatchDraw(). This shim preserves that contract while routing glass modes
 * through the shared OneUiGlassBackground/OneUiCrystalRenderer pipeline whenever a root view can be
 * resolved. If a reliable host is not available yet, it still draws a visible Crystal fallback so
 * the open folder never collapses back to a flat solid rectangle.
 */
class OneUiFolderCrystalDrawable(
    private val context: Context,
    @ColorInt initialColor: Int,
) : GradientDrawable() {

    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    private val folderRadius = context.resources.getDimension(R.dimen.bg_round_rect_radius)

    private var explicitHost: View? = null
    private var delegate: Drawable? = null
    private var delegateMode = Int.MIN_VALUE
    private var delegateColor = Color.TRANSPARENT
    private var delegateWidth = -1
    private var delegateHeight = -1

    @ColorInt
    private var baseColor: Int = initialColor
    private var alphaValue: Int = 255
    private var colorFilterValue: ColorFilter? = null

    init {
        setShape(GradientDrawable.RECTANGLE)
        setCornerRadius(folderRadius)
        setColor(initialColor)
    }

    /** Optional host hook for future callers that can provide the actual Folder view. */
    fun setHost(host: View?) {
        if (explicitHost === host) return
        explicitHost = host
        clearDelegate()
    }

    override fun setColor(@ColorInt color: Int) {
        baseColor = color
        super.setColor(color)
        clearDelegate()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha.coerceIn(0, 255)
        super.setAlpha(alphaValue)
        clearDelegate()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        colorFilterValue = colorFilter
        super.setColorFilter(colorFilter)
        delegate?.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        delegate?.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        val mode = OneUiGlassStyle.normalize(OneUiGlassPreferences.getFolderMode(context))
        if (!OneUiGlassStyle.isGlass(mode)) {
            super.draw(canvas)
            return
        }

        val currentBounds = bounds
        if (currentBounds.isEmpty) return

        val glass = ensureDelegate(mode, currentBounds)
        if (glass != null) {
            glass.draw(canvas)
            return
        }

        // Hostless fallback: still visibly Crystal, but intentionally avoids pretending to have
        // reliable backdrop sampling for folder previews or unattached render paths.
        drawStaticCrystalFallback(canvas, currentBounds, mode)
    }

    private fun ensureDelegate(mode: Int, bounds: Rect): Drawable? {
        val host = resolveHostView() ?: return null
        if (!host.isAttachedToWindow || host.width <= 0 || host.height <= 0) return null

        val color = glassColor()
        if (delegate == null || delegateMode != mode || delegateColor != color
            || delegateWidth != bounds.width() || delegateHeight != bounds.height()
        ) {
            delegate = OneUiGlassBackground.createFolderDrawable(host, color, folderRadius).also {
                it.bounds = bounds
                it.colorFilter = colorFilterValue
            }
            delegateMode = mode
            delegateColor = color
            delegateWidth = bounds.width()
            delegateHeight = bounds.height()
        } else {
            delegate?.bounds = bounds
        }
        return delegate
    }

    private fun drawStaticCrystalFallback(canvas: Canvas, bounds: Rect, mode: Int) {
        rect.set(bounds)
        val strength = when (mode) {
            OneUiGlassStyle.CRYSTAL -> 1.0f
            OneUiGlassStyle.FROSTY -> 0.86f
            else -> 0.72f
        }
        val baseAlpha = max(42, (alphaValue * (0.30f + 0.22f * strength)).toInt())
        val topAlpha = max(28, (alphaValue * (0.20f + 0.16f * strength)).toInt())
        val edgeAlpha = max(52, (alphaValue * (0.24f + 0.22f * strength)).toInt())

        fillPaint.colorFilter = colorFilterValue
        fillPaint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            intArrayOf(
                alphaColor(Color.WHITE, topAlpha),
                alphaColor(baseColor, baseAlpha),
                alphaColor(Color.BLACK, (alphaValue * 0.05f).toInt()),
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, folderRadius, folderRadius, fillPaint)
        fillPaint.shader = null

        val inset = 1.15f * density
        rect.inset(inset, inset)

        edgePaint.colorFilter = colorFilterValue
        edgePaint.style = Paint.Style.STROKE
        edgePaint.strokeWidth = max(1f, 1.2f * density)
        edgePaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                alphaColor(Color.WHITE, edgeAlpha),
                alphaColor(Color.rgb(145, 210, 255), (edgeAlpha * 0.56f).toInt()),
                alphaColor(Color.rgb(255, 120, 210), (edgeAlpha * 0.34f).toInt()),
                alphaColor(Color.WHITE, (edgeAlpha * 0.42f).toInt()),
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, folderRadius - inset, folderRadius - inset, edgePaint)

        edgePaint.shader = null
        edgePaint.strokeWidth = max(1f, 0.65f * density)
        edgePaint.color = alphaColor(Color.rgb(90, 205, 255), (edgeAlpha * 0.34f).toInt())
        rect.offset(0.65f * density, 0f)
        canvas.drawRoundRect(rect, folderRadius - inset, folderRadius - inset, edgePaint)
        rect.offset(-1.3f * density, 0f)
        edgePaint.color = alphaColor(Color.rgb(255, 92, 196), (edgeAlpha * 0.26f).toInt())
        canvas.drawRoundRect(rect, folderRadius - inset, folderRadius - inset, edgePaint)
        rect.offset(0.65f * density, 0f)

        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(alphaColor(Color.WHITE, (topAlpha * 0.95f).toInt()), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        fillPaint.colorFilter = colorFilterValue
        rect.inset(2.5f * density, 2.5f * density)
        canvas.drawRoundRect(rect, folderRadius - 3f * density, folderRadius - 3f * density, fillPaint)
        fillPaint.shader = null
    }

    @ColorInt
    private fun glassColor(): Int = Color.argb(
        alphaValue,
        Color.red(baseColor),
        Color.green(baseColor),
        Color.blue(baseColor),
    )

    @ColorInt
    private fun alphaColor(@ColorInt color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun clearDelegate() {
        delegate = null
        delegateMode = Int.MIN_VALUE
        delegateWidth = -1
        delegateHeight = -1
        invalidateSelf()
    }

    private fun resolveHostView(): View? {
        explicitHost?.let { return it }
        return context.findActivity()?.window?.decorView
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

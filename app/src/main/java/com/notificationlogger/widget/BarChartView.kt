package com.notificationlogger.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight custom bar chart — no external library.
 * Supports VERTICAL (default, for hourly distribution)
 * and HORIZONTAL (for ranked lists like type breakdowns).
 *
 * Usage:
 *   chart.setData(listOf("01" to 5f, "02" to 12f, ...))
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ── Data ──────────────────────────────────────────────────────────────────
    private var entries: List<Pair<String, Float>> = emptyList()
    private var maxValue: Float = 1f
    private var highlightIndex: Int = -1

    // ── Config ────────────────────────────────────────────────────────────────
    var orientation: Orientation = Orientation.VERTICAL
    var barColor: Int        = 0xFF00D4FF.toInt()
    var highlightColor: Int  = 0xFF00FFB3.toInt()
    var labelColor: Int      = 0xFF667788.toInt()
    var valueColor: Int      = 0xFFCCDDEE.toInt()
    var axisColor: Int       = 0xFF1A3040.toInt()
    var showValues: Boolean  = true
    var showLabels: Boolean  = true
    var barRadius: Float     = 6f
    var labelTextSize: Float = 22f
    var valueTextSize: Float = 20f

    enum class Orientation { VERTICAL, HORIZONTAL }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val barPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hlPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

    fun setData(data: List<Pair<String, Float>>, highlightIdx: Int = -1) {
        entries = data
        maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f
        highlightIndex = highlightIdx
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        barPaint.color   = barColor
        hlPaint.color    = highlightColor
        axisPaint.color  = axisColor
        labelPaint.color = labelColor
        labelPaint.textSize = labelTextSize
        valuePaint.color = valueColor
        valuePaint.textSize = valueTextSize

        if (orientation == Orientation.VERTICAL) drawVertical(canvas)
        else drawHorizontal(canvas)
    }

    // ── Vertical bars (e.g. hourly heatmap) ──────────────────────────────────
    private fun drawVertical(canvas: Canvas) {
        val n = entries.size.coerceAtLeast(1)
        val w = width.toFloat()
        val h = height.toFloat()

        val labelH  = if (showLabels) labelTextSize * 2f else 4f
        val valueH  = if (showValues) valueTextSize * 1.8f else 4f
        val chartH  = h - labelH - valueH - 8f

        val slotW = w / n
        val barW  = slotW * 0.65f
        val gapX  = (slotW - barW) / 2f

        // axis line
        canvas.drawLine(0f, h - labelH, w, h - labelH, axisPaint)

        entries.forEachIndexed { i, (label, value) ->
            val bh    = ((value / maxValue) * chartH).coerceAtLeast(2f)
            val left  = i * slotW + gapX
            val top   = h - labelH - bh
            val right = left + barW
            val bot   = h - labelH

            val paint = if (i == highlightIndex) hlPaint else barPaint

            // Gradient: top lighter, bottom slightly darker
            val shader = LinearGradient(
                left, top, left, bot,
                intArrayOf(
                    blendAlpha(paint.color, 230),
                    blendAlpha(paint.color, 170)
                ),
                null, Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRoundRect(RectF(left, top, right, bot), barRadius, barRadius, paint)
            paint.shader = null

            // Value above bar (only if bar is tall enough)
            if (showValues && bh > valueTextSize * 1.5f) {
                valuePaint.textAlign = Paint.Align.CENTER
                val vLabel = formatNum(value)
                canvas.drawText(vLabel, left + barW / 2f, top - 4f, valuePaint)
            }

            // X label
            if (showLabels) {
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(label, left + barW / 2f, h - 4f, labelPaint)
            }
        }
    }

    // ── Horizontal bars (e.g. type breakdown) ─────────────────────────────────
    private fun drawHorizontal(canvas: Canvas) {
        val n = entries.size.coerceAtLeast(1)
        val w = width.toFloat()
        val h = height.toFloat()

        // Measure max label width
        labelPaint.textAlign = Paint.Align.RIGHT
        val maxLabelW = entries.maxOfOrNull { (lbl, _) -> labelPaint.measureText(lbl) } ?: 80f
        val labelW = maxLabelW + 12f

        val chartW = w - labelW - 8f
        val slotH  = h / n
        val barH   = slotH * 0.6f
        val gapY   = (slotH - barH) / 2f

        entries.forEachIndexed { i, (label, value) ->
            val bw    = ((value / maxValue) * chartW).coerceAtLeast(4f)
            val top   = i * slotH + gapY
            val left  = labelW
            val right = left + bw
            val bot   = top + barH

            val paint = if (i == highlightIndex) hlPaint else barPaint
            val shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(blendAlpha(paint.color, 200), blendAlpha(paint.color, 255)),
                null, Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRoundRect(RectF(left, top, right, bot), barRadius, barRadius, paint)
            paint.shader = null

            // Left label
            if (showLabels) {
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(label, labelW - 6f, top + barH * 0.73f, labelPaint)
            }

            // Value to the right of bar
            if (showValues) {
                valuePaint.textAlign = Paint.Align.LEFT
                canvas.drawText(formatNum(value), right + 6f, top + barH * 0.75f, valuePaint)
            }
        }
    }

    private fun blendAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun formatNum(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minH = when (orientation) {
            Orientation.VERTICAL   -> 120
            Orientation.HORIZONTAL -> (entries.size * 40 + 16).coerceAtLeast(80)
        }
        setMeasuredDimension(
            resolveSize(200, widthMeasureSpec),
            resolveSize(minH, heightMeasureSpec)
        )
    }
}

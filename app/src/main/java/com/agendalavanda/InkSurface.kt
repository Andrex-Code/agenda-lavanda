package com.agendalavanda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset

/** Canonical page coordinates keep handwriting stable across viewport sizes. */
class InkSurface(context: Context) : View(context) {
    var lines: List<List<Offset>> = emptyList()
    var allowFinger = true
    var onStroke: (List<Offset>) -> Unit = {}
    private var current = mutableListOf<Offset>()
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff75539b.toInt(); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    private val rule = Paint().apply { color = 0xffeee8f5.toInt(); strokeWidth = 1f }
    private var pointer = -1
    private val scale get() = (width / 1000f).coerceAtLeast(.01f)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.save(); canvas.scale(scale, scale)
        var y = 40f
        while (y < height / scale) { canvas.drawLine(0f, y, 1000f, y, rule); y += 40f }
        (lines + listOf(current)).forEach { points ->
            if (points.size == 1) canvas.drawCircle(points[0].x, points[0].y, 1.5f, pen)
            points.zipWithNext().forEach { (a,b) -> canvas.drawLine(a.x, a.y, b.x, b.y, pen) }
        }
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val tool = event.getToolType(0)
            if (!allowFinger && tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false
            pointer = event.getPointerId(0); current = mutableListOf()
            parent.requestDisallowInterceptTouchEvent(true)
        }
        val index = event.findPointerIndex(pointer)
        if (index < 0) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                for (h in 0 until event.historySize) current.add(Offset(event.getHistoricalX(index,h)/scale,event.getHistoricalY(index,h)/scale))
                current.add(Offset(event.getX(index)/scale,event.getY(index)/scale))
                if(event.actionMasked == MotionEvent.ACTION_UP) { onStroke(current.toList()); current.clear(); pointer = -1; performClick() }
            }
            MotionEvent.ACTION_CANCEL -> { current.clear(); pointer = -1 }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

package com.agendalavanda.ink
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

object PagePainter {
    const val WIDTH=1000f
    const val HEIGHT=1414f
    fun draw(canvas: Canvas, document: InkDocument, excluded:Set<Int> = emptySet()) {
        canvas.drawColor(Color.WHITE)
        val grid=Paint().apply { color=0xffe5dfef.toInt(); strokeWidth=1f }
        when(document.paper) {
            "lines" -> { var y=48f; while(y<HEIGHT) { canvas.drawLine(24f,y,WIDTH-24,y,grid); y+=36 } }
            "grid" -> { var n=24f; while(n<HEIGHT) { canvas.drawLine(0f,n,WIDTH,n,grid); n+=24 }
                n=24f; while(n<WIDTH) { canvas.drawLine(n,0f,n,HEIGHT,grid); n+=24 } }
            "dots" -> { var y=24f; while(y<HEIGHT) { var x=24f; while(x<WIDTH) { canvas.drawCircle(x,y,1.3f,grid); x+=24 }; y+=24 } }
        }
        document.strokes.forEachIndexed { index,stroke -> if(index !in excluded) drawStroke(canvas,stroke) }
    }
    fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=stroke.color; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
        stroke.points.firstOrNull()?.let { canvas.drawCircle(it.x,it.y,stroke.width*it.pressure/2,pen) }
        for(i in 1 until stroke.points.size) { val a=stroke.points[i-1];val b=stroke.points[i];pen.strokeWidth=stroke.width*(a.pressure+b.pressure)/2;canvas.drawLine(a.x,a.y,b.x,b.y,pen) }
    }
}
class InkCanvas(context: Context): View(context) {
    var document=InkDocument()
    var allowFinger=true
    var mode="pen"
    var penColor=0xff75539b.toInt()
    var penWidth=3f
    var onChange:(InkDocument)->Unit={}
    private var points=mutableListOf<InkPoint>()
    private var removed=setOf<Int>()
    private var pointer=-1
    private var zoom=1f
    private var dx=0f
    private var dy=0f
    private var lastX=0f
    private var lastY=0f
    private var multi=false
    private val fit get()=min(width/PagePainter.WIDTH,height/PagePainter.HEIGHT).coerceAtLeast(.01f)
    private val scale get()=fit*zoom
    private val originX get()=(width-PagePainter.WIDTH*fit)/2+dx
    private val originY get()=dy
    private val detector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            val old=scale; val newZoom=(zoom*detector.scaleFactor).coerceIn(.75f,6f)
            val ratio=newZoom/zoom
            dx=(detector.focusX-(width-PagePainter.WIDTH*fit)/2)*(1-ratio)+dx*ratio
            dy=detector.focusY*(1-ratio)+dy*ratio
            zoom=newZoom; invalidate(); return old>0
        }
    })
    fun resetPage(){ zoom=1f;dx=0f;dy=0f;invalidate() }
    override fun onDraw(canvas:Canvas) {
        canvas.drawColor(0xffe9e4ef.toInt());canvas.save();canvas.translate(originX,originY);canvas.scale(scale,scale)
        canvas.clipRect(0f,0f,PagePainter.WIDTH,PagePainter.HEIGHT)
        PagePainter.draw(canvas,document,removed)
        if(mode=="pen" && points.isNotEmpty()) PagePainter.drawStroke(canvas,InkStroke(points,penColor,penWidth))
        canvas.restore()
    }
    private fun point(x:Float,y:Float,pressure:Float)=InkPoint(((x-originX)/scale).coerceIn(0f,PagePainter.WIDTH),
        ((y-originY)/scale).coerceIn(0f,PagePainter.HEIGHT),pressure.coerceIn(.3f,1.5f))
    override fun onTouchEvent(e:MotionEvent):Boolean {
        // In pen-only mode, reject palm/finger events without feeding the gesture detector.
        if(!allowFinger && e.getToolType(e.actionIndex)==MotionEvent.TOOL_TYPE_FINGER && mode!="pan") return true
        detector.onTouchEvent(e)
        if(e.pointerCount>1) { points.clear();removed=emptySet();multi=true;invalidate();return true }
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multi=false; pointer=e.getPointerId(0);points.clear();removed=emptySet();lastX=e.x;lastY=e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_CANCEL -> { points.clear();removed=emptySet();pointer=-1;invalidate();return true }
        }
        if(multi) return true
        val index=e.findPointerIndex(pointer);if(index<0)return true
        if(mode=="pan") { if(e.actionMasked==MotionEvent.ACTION_MOVE) { dx+=e.x-lastX;dy+=e.y-lastY;invalidate() };lastX=e.x;lastY=e.y;return true }
        val pressure=if(e.getToolType(index)==MotionEvent.TOOL_TYPE_STYLUS) e.getPressure(index) else 1f
        val batch=(0 until e.historySize).map { h -> point(e.getHistoricalX(index,h),e.getHistoricalY(index,h),pressure) }+
            point(e.getX(index),e.getY(index),pressure)
        if(mode=="erase" || e.getToolType(index)==MotionEvent.TOOL_TYPE_ERASER) {
            document.strokes.forEachIndexed { i,s -> if(batch.any { s.hit(it,16f) }) removed=removed+i }
        } else points.addAll(batch)
        if(e.actionMasked==MotionEvent.ACTION_UP) {
            if(removed.isNotEmpty()) onChange(document.copy(strokes=document.strokes.filterIndexed { i,_ -> i !in removed }))
            else if(points.isNotEmpty()) onChange(document.copy(strokes=document.strokes+InkStroke(points.toList(),penColor,penWidth)))
            points.clear();removed=emptySet();pointer=-1;performClick()
        }
        invalidate();return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}


package com.agendalavanda
import android.view.*
import android.widget.FrameLayout
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agendalavanda.ink.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkInputTest {
    @Test fun ruledPaperKeepsItsLinesWhenScaledToPhoneWidth() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val view=InkCanvas(context);FrameLayout(context).addView(view);view.layout(0,0,480,800)
            val bitmap=android.graphics.Bitmap.createBitmap(480,800,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                view.document=InkDocument(paper="lines")
                view.draw(android.graphics.Canvas(bitmap))
                var lines=0;var previous=false
                for(y in 1 until 670) {
                    val ink=android.graphics.Color.red(bitmap.getPixel(240,y))<254
                    if(ink && !previous) lines++
                    previous=ink
                }
                assertTrue("Los renglones deben conservarse al reducir la hoja: $lines",lines>=36)
            } finally {bitmap.recycle()}
        }
    }
    @Test fun cachedPageRefreshesAfterRemovingStrokes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val view=InkCanvas(context);FrameLayout(context).addView(view);view.layout(0,0,1000,1414)
            val bitmap=android.graphics.Bitmap.createBitmap(1500,1414,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.RED)
                view.document=InkDocument(paper="blank",strokes=listOf(InkStroke(listOf(InkPoint(100f,100f),InkPoint(500f,500f)),0xff123456.toInt(),8f)))
                view.draw(android.graphics.Canvas(bitmap));assertEquals(0xff123456.toInt(),bitmap.getPixel(300,300))
                assertEquals("El lienzo no debe pintar fuera de sus límites",android.graphics.Color.RED,bitmap.getPixel(1200,300))
                view.document=view.document.copy(strokes=emptyList())
                view.draw(android.graphics.Canvas(bitmap));assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(300,300))
            } finally {bitmap.recycle()}
        }
    }
    @Test fun palmContactDoesNotCancelActiveStylusStroke() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val view=InkCanvas(context);FrameLayout(context).addView(view)
            view.layout(0,0,1000,1414);view.allowFinger=false;view.onChange={view.document=it}
            send(view,MotionEvent.ACTION_DOWN,100f,100f,MotionEvent.TOOL_TYPE_STYLUS)
            val tools=arrayOf(MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS},MotionEvent.PointerProperties().apply{id=1;toolType=MotionEvent.TOOL_TYPE_FINGER})
            val positions=arrayOf(MotionEvent.PointerCoords().apply{x=200f;y=200f;pressure=.7f},MotionEvent.PointerCoords().apply{x=300f;y=400f;pressure=1f})
            val now=SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),MotionEvent.ACTION_MOVE,MotionEvent.ACTION_POINTER_UP).forEach { action ->
                val event=MotionEvent.obtain(now,now,action,2,tools,positions,0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try{view.onTouchEvent(event)}finally{event.recycle()}
            }
            assertEquals(1,view.document.strokes.size)
            assertEquals(100f,view.document.strokes.single().points.first().x,.01f)
            assertEquals(200f,view.document.strokes.single().points.last().x,.01f)
        }
    }
    private fun send(view:InkCanvas,action:Int,x:Float,y:Float,tool:Int,pressure:Float=1f) {
        val properties=MotionEvent.PointerProperties().apply {id=0;toolType=tool}
        val coordinates=MotionEvent.PointerCoords().apply {this.x=x;this.y=y;this.pressure=pressure;size=1f}
        val now=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(now,now,action,1,arrayOf(properties),arrayOf(coordinates),0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_STYLUS,0)
        try {view.onTouchEvent(event)} finally {event.recycle()}
    }
    @Test fun penOnlyRejectsFingerAndKeepsStylusPressureAndCoordinates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val view=InkCanvas(context);val parent=FrameLayout(context);parent.addView(view)
            view.layout(0,0,500,707);view.allowFinger=false
            view.onChange={view.document=it}
            send(view,MotionEvent.ACTION_DOWN,50f,50f,MotionEvent.TOOL_TYPE_FINGER)
            send(view,MotionEvent.ACTION_UP,100f,100f,MotionEvent.TOOL_TYPE_FINGER)
            assertTrue(view.document.strokes.isEmpty())
            send(view,MotionEvent.ACTION_DOWN,50f,50f,MotionEvent.TOOL_TYPE_STYLUS,.5f)
            send(view,MotionEvent.ACTION_UP,100f,100f,MotionEvent.TOOL_TYPE_STYLUS,.5f)
            val points=view.document.strokes.single().points
            assertEquals(100f,points.first().x,.01f)
            assertEquals(200f,points.last().y,.01f)
            assertEquals(.5f,points.first().pressure,.01f)
        }
    }
    @Test fun eraserRemovesWholeStrokeAcrossUnsampledSegment() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val view=InkCanvas(context);FrameLayout(context).addView(view)
            view.layout(0,0,1000,1414)
            view.document=InkDocument(strokes=listOf(InkStroke(listOf(InkPoint(10f,100f),InkPoint(900f,100f)))))
            view.onChange={view.document=it};view.mode="erase"
            send(view,MotionEvent.ACTION_DOWN,450f,100f,MotionEvent.TOOL_TYPE_ERASER)
            send(view,MotionEvent.ACTION_UP,450f,100f,MotionEvent.TOOL_TYPE_ERASER)
            assertTrue(view.document.strokes.isEmpty())
        }
    }
}


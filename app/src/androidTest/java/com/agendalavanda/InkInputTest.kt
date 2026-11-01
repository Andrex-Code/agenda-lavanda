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


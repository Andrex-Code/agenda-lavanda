package com.agendalavanda.ink
class PagePreview(context: android.content.Context):android.view.View(context) {
    var page=InkDocument()
    override fun onDraw(canvas:android.graphics.Canvas) {canvas.save();val scale=kotlin.math.min(width/1000f,height/1414f);canvas.scale(scale,scale);PagePainter.draw(canvas,page);canvas.restore()}
}


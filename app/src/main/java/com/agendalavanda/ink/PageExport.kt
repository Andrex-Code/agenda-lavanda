package com.agendalavanda.ink
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
object PageExport {
    fun pdf(title:String, document:InkDocument, output:OutputStream) {
        val pdf=PdfDocument()
        try {
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=14f;color=Color.BLACK }
            val layout=StaticLayout.Builder.obtain(title+"\n\n"+document.text,0,(title+"\n\n"+document.text).length,paint,515)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4f,1f).build()
            var line=0;var pageNumber=1
            while(line<layout.lineCount) {
                val top=layout.getLineTop(line);var end=line+1
                while(end<layout.lineCount && layout.getLineBottom(end)-top<=730) end++
                val bottom=layout.getLineTop(end)
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber++).create())
                page.canvas.save();page.canvas.translate(40f,40f);page.canvas.clipRect(0f,0f,515f,(bottom-top).toFloat())
                page.canvas.translate(0f,-top.toFloat());layout.draw(page.canvas);page.canvas.restore()
                pdf.finishPage(page);line=end
            }
            if(document.strokes.isNotEmpty()) {
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
                page.canvas.scale(595f/1000f,842f/1414f);PagePainter.draw(page.canvas,document);pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally { pdf.close() }
    }
    fun png(document:InkDocument,output:OutputStream) {
        val bitmap=Bitmap.createBitmap(1000,1414,Bitmap.Config.ARGB_8888)
        try { PagePainter.draw(Canvas(bitmap),document);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) }
        finally { bitmap.recycle() }
    }
}


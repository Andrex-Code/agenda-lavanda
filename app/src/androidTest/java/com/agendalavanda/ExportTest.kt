package com.agendalavanda
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.agendalavanda.ink.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
@RunWith(AndroidJUnit4::class)
class ExportTest {
    @Test fun pdfPaginatesTextAndPreservesDrawingPage() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"export-test.pdf")
        val doc=InkDocument(text=("Una reflexión extensa para probar paginación.\n").repeat(140),
            strokes=listOf(InkStroke(listOf(InkPoint(10f,10f),InkPoint(200f,300f)),0xff123456.toInt(),5f)))
        file.outputStream().use{PageExport.pdf("Título",doc,it)}
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount>=3)
            renderer.openPage(renderer.pageCount-1).use { page -> assertEquals(595,page.width);assertEquals(842,page.height) }
        }
        val png=ByteArrayOutputStream();PageExport.png(doc,png)
        assertArrayEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47),png.toByteArray().take(4).toByteArray())
        file.delete()
    }
}

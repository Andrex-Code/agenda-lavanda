package com.agendalavanda.ink
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class InkStroke(val points: List<InkPoint>, val color: Int = 0xff75539b.toInt(), val width: Float = 3f)
data class InkDocument(val text: String = "", val paper: String = "lines", val strokes: List<InkStroke> = emptyList()) {
    fun encode(): String = "[PAGINA2]" + JSONObject().apply {
        put("text",text); put("paper",paper)
        put("strokes",JSONArray().apply { strokes.forEach { stroke -> put(JSONObject().apply {
            put("color",stroke.color); put("width",stroke.width)
            put("points",JSONArray().apply { stroke.points.forEach { put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble()).put(it.pressure.toDouble())) } })
        }) } })
    }
    companion object {
        fun decode(raw: String): InkDocument {
            if(raw.startsWith("[PAGINA2]")) {
                val o = JSONObject(raw.removePrefix("[PAGINA2]"))
                val a = o.getJSONArray("strokes")
                return InkDocument(o.optString("text"),o.optString("paper","lines"),(0 until a.length()).map { i ->
                    val line = a.getJSONObject(i); val pts = line.getJSONArray("points")
                    InkStroke((0 until pts.length()).map { n ->
                        val p = pts.getJSONArray(n); val x = p.getDouble(0).toFloat(); val y = p.getDouble(1).toFloat()
                        require(x.isFinite() && y.isFinite()) { "Trazo inválido." }
                        InkPoint(x,y,p.optDouble(2,1.0).toFloat().coerceIn(.1f,2f))
                    },line.optInt("color",0xff75539b.toInt()),line.optDouble("width",3.0).toFloat().coerceIn(1f,20f))
                })
            }
            if(raw.startsWith("[MANUSCRITO]")) {
                val a = JSONArray(raw.removePrefix("[MANUSCRITO]").substringBefore("\n"))
                return InkDocument(raw.substringAfter("\n",""),"lines",(0 until a.length()).map { i ->
                    val line = a.getJSONArray(i)
                    InkStroke((0 until line.length()).map { n -> val p = line.getJSONArray(n); InkPoint(p.getDouble(0).toFloat(),p.getDouble(1).toFloat()) })
                })
            }
            return InkDocument(text = raw)
        }
    }
}
fun distanceToSegment(p: InkPoint, a: InkPoint, b: InkPoint): Float {
    val dx=b.x-a.x; val dy=b.y-a.y; val length=dx*dx+dy*dy
    val t=if(length==0f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/length).coerceIn(0f,1f)
    return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
}
fun InkStroke.hit(point: InkPoint, radius: Float): Boolean = points.any { hypot(it.x-point.x,it.y-point.y) <= radius+width/2 } ||
    points.zipWithNext().any { (a,b) -> distanceToSegment(point,a,b) <= radius+width/2 }


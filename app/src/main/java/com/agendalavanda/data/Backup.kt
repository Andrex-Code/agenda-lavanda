package com.agendalavanda.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

object EntryCodec {
    fun json(e: Entry) = JSONObject().apply {
        put("id", e.id); put("section", e.section); put("title", e.title); put("body", e.body)
        put("date", e.date); put("done", e.done); put("updatedAt", e.updatedAt)
        put("notebookId", e.notebookId ?: JSONObject.NULL); put("deletedAt", e.deletedAt)
        put("progress", e.progress); put("habitDates", e.habitDates); put("time", e.time); put("repeatRule", e.repeatRule)
    }
    fun read(o: JSONObject, validate: Boolean = true): Entry {
        val section = o.getString("section")
        require(section in setOf("Inicio","Calendario","Tareas","Notas","Cuadernos","Diario","Hábitos","Metas")) { "Sección desconocida." }
        val date = o.optString("date")
        require(!validate || date.isBlank() || runCatching { LocalDate.parse(date) }.isSuccess) { "Fecha inválida." }
        val time = o.optString("time")
        require(!validate || time.isBlank() || runCatching { LocalTime.parse(time) }.isSuccess) { "Hora inválida." }
        val progress = o.optInt("progress")
        require(progress in 0..100) { "Progreso inválido." }
        val history = o.optString("habitDates")
        require(history.split(",").filter { it.isNotBlank() }.all { runCatching { LocalDate.parse(it) }.isSuccess }) { "Historial inválido." }
        val rule = o.optString("repeatRule")
        require(rule in setOf("", "daily", "weekly", "monthly")) { "Repetición desconocida." }
        return Entry(o.optLong("id"), section, o.getString("title"), o.optString("body"), date,
            o.optBoolean("done"), o.optLong("updatedAt", System.currentTimeMillis()),
            if (o.isNull("notebookId")) null else o.getLong("notebookId"), o.optLong("deletedAt"),
            progress, history, time, rule)
    }
}

object Backup {
    const val MAX_BYTES = 15_000_000
    fun encode(entries: List<Entry>, name: String, user: String, theme: Int = 0, water: Int = 8, drafts: List<Draft> = emptyList()): String {
        val json = JSONObject().apply {
            put("format","agenda-lavanda-backup"); put("version",2); put("agendaName",name); put("userName",user)
            put("theme",theme); put("waterGoal",water); put("createdAt",System.currentTimeMillis())
            put("entries",JSONArray().apply { entries.forEach { put(EntryCodec.json(it)) } })
            put("drafts",JSONArray().apply { drafts.forEach { put(JSONObject().put("key",it.key).put("payload",it.payload).put("updatedAt",it.updatedAt)) } })
        }.toString()
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "La copia supera 15 MB." }
        return json
    }
    fun decode(raw: String): ImportData {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "El archivo supera 15 MB." }
        val root = JSONObject(raw)
        require(root.optString("format") == "agenda-lavanda-backup") { "No es una copia de Agenda Lavanda." }
        require(root.optInt("version") in 1..2) { "Versión de copia no compatible." }
        val array = root.getJSONArray("entries")
        require(array.length() <= 50000) { "Demasiados registros." }
        var nextId = (0 until array.length()).maxOfOrNull { array.getJSONObject(it).optLong("id") }?.coerceAtLeast(0) ?: 0
        val entries = (0 until array.length()).map { EntryCodec.read(array.getJSONObject(it)) }.map { if(it.id <= 0) it.copy(id = ++nextId) else it }
        require(entries.map { it.id }.toSet().size == entries.size) { "Identificadores duplicados." }
        val byId = entries.associateBy { it.id }
        require(entries.all { it.notebookId == null || (it.section == "Notas" && byId[it.notebookId]?.section == "Cuadernos") }) { "Hay páginas con un cuaderno inexistente." }
        val draftsArray = root.optJSONArray("drafts") ?: JSONArray()
        val drafts = (0 until draftsArray.length()).map {
            val d = draftsArray.getJSONObject(it)
            EntryCodec.read(JSONObject(d.getString("payload")), validate=false)
            Draft(d.getString("key"),d.getString("payload"),d.optLong("updatedAt"))
        }
        drafts.forEach { draft ->
            val e=EntryCodec.read(JSONObject(draft.payload),validate=false)
            require(e.id == 0L || byId[e.id]?.section == e.section) { "El borrador no tiene su registro original." }
            require(e.notebookId == null || byId[e.notebookId]?.section == "Cuadernos") { "El cuaderno del borrador no existe." }
            val expected=if(e.id>0) "entry:${e.id}" else "new:${e.section}:${e.notebookId ?: 0}"
            require(draft.key==expected) { "Identificador de borrador inválido." }
        }
        require(drafts.map { it.key }.toSet().size == drafts.size) { "Borradores duplicados." }
        return ImportData(entries,root.optString("agendaName","Mi Agenda"),root.optString("userName"),
            root.optInt("theme").coerceIn(0,3),root.optInt("waterGoal",8).coerceIn(1,30),drafts)
    }
    data class ImportData(val entries: List<Entry>, val agendaName: String, val userName: String, val theme: Int = 0, val waterGoal: Int = 8, val drafts: List<Draft> = emptyList())
}


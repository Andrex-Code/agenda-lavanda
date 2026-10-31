package com.agendalavanda.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object Backup {
    private const val FORMAT = "agenda-lavanda-backup"
    fun encode(entries: List<Entry>, name: String, user: String): String {
        val json = JSONObject().apply {
        put("format", FORMAT); put("version", 1); put("agendaName", name); put("userName", user)
        put("createdAt", System.currentTimeMillis())
        put("entries", JSONArray().apply { entries.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("section", e.section); put("title", e.title); put("body", e.body)
            put("date", e.date); put("done", e.done); put("updatedAt", e.updatedAt)
        }) } })
        }.toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= 15_000_000) { "La copia supera el límite de 15 MB." }
        return json
    }

    fun decode(raw: String): ImportData {
        require(raw.toByteArray(Charsets.UTF_8).size <= 15_000_000) { "El archivo supera el límite de 15 MB." }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Este archivo no es una copia de Agenda Lavanda." }
        require(root.optInt("version", -1) == 1) { "La versión de la copia no es compatible." }
        val array = root.optJSONArray("entries") ?: error("La copia no contiene registros válidos.")
        require(array.length() <= 50_000) { "La copia contiene demasiados registros." }
        val valid = setOf("Inicio", "Calendario", "Tareas", "Notas", "Cuadernos", "Diario", "Hábitos", "Metas")
        val entries = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val section = o.getString("section")
            require(section in valid) { "La copia contiene una sección desconocida." }
            val date = o.optString("date").take(20)
            require(date.isBlank() || runCatching { LocalDate.parse(date) }.isSuccess) { "La copia contiene una fecha inválida." }
            Entry(section = section, title = o.getString("title").take(300), body = o.optString("body").take(100_000),
                date = date, done = o.optBoolean("done"), updatedAt = o.optLong("updatedAt", System.currentTimeMillis()))
        }
        return ImportData(entries, root.optString("agendaName", "Mi Agenda").take(60), root.optString("userName", "").take(60))
    }
    data class ImportData(val entries: List<Entry>, val agendaName: String, val userName: String)
}

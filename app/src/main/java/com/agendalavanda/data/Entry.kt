package com.agendalavanda.data
import androidx.room.*
import java.time.LocalDate

@Entity(tableName = "entries", indices = [Index("notebookId")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val section: String, val title: String, val body: String = "", val date: String = "",
    val done: Boolean = false, val updatedAt: Long = System.currentTimeMillis(),
    val notebookId: Long? = null,
    @ColumnInfo(defaultValue = "0") val deletedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val progress: Int = 0,
    @ColumnInfo(defaultValue = "''") val habitDates: String = "",
    @ColumnInfo(defaultValue = "''") val time: String = "",
    @ColumnInfo(defaultValue = "''") val repeatRule: String = ""
) {
    fun completedOn(day: LocalDate) = day.toString() in habitDates.split(",")
    fun toggleHabit(day: LocalDate): Entry {
        val dates = habitDates.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (!dates.add(day.toString())) dates.remove(day.toString())
        return copy(habitDates = dates.sorted().joinToString(","), updatedAt = System.currentTimeMillis())
    }
    fun streak(today: LocalDate): Int {
        val dates = habitDates.split(",").toSet()
        var day = if (today.toString() in dates) today else today.minusDays(1)
        var count = 0
        while (day.toString() in dates) { count++; day = day.minusDays(1) }
        return count
    }
    fun occursOn(day: LocalDate): Boolean {
        val start = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        if (day < start) return false
        return when(repeatRule) {
            "daily" -> true
            "weekly" -> day.dayOfWeek == start.dayOfWeek
            "monthly" -> day.dayOfMonth == start.dayOfMonth.coerceAtMost(day.lengthOfMonth())
            else -> day == start
        }
    }
}
@Entity(tableName = "drafts")
data class Draft(@PrimaryKey val key: String, val payload: String, val updatedAt: Long = System.currentTimeMillis())


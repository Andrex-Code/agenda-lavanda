package com.agendalavanda.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries ORDER BY updatedAt DESC") abstract fun observeAll(): Flow<List<Entry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun put(entry: Entry): Long
    @Query("SELECT * FROM entries ORDER BY id") abstract suspend fun all(): List<Entry>
    @Query("SELECT * FROM entries WHERE id = :id") abstract suspend fun find(id: Long): Entry?
    @Query("DELETE FROM entries WHERE id = :id") abstract suspend fun delete(id: Long)
    @Query("DELETE FROM entries") abstract suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAll(entries: List<Entry>)
    @Query("UPDATE entries SET deletedAt = :timestamp WHERE id = :id OR notebookId = :id") abstract suspend fun trash(id: Long, timestamp: Long)
    @Query("UPDATE entries SET deletedAt = 0 WHERE id = :id OR notebookId = :id") abstract suspend fun restore(id: Long)
    @Transaction open suspend fun restoreWithParent(entry: Entry) { entry.notebookId?.let { restore(it) }; restore(entry.id) }
    @Query("DELETE FROM entries WHERE id = :id OR notebookId = :id") abstract suspend fun deleteTree(id: Long)
    @Transaction open suspend fun permanentlyDelete(id: Long) {
        all().filter { it.id == id || it.notebookId == id }.forEach { deleteDraft("entry:${it.id}") }
        deleteDraft("new:Notas:$id")
        deleteTree(id)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putDraft(draft: Draft)
    @Query("SELECT * FROM drafts WHERE key = :key") abstract suspend fun draft(key: String): Draft?
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC") abstract fun observeDrafts(): Flow<List<Draft>>
    @Query("DELETE FROM drafts WHERE key = :key") abstract suspend fun deleteDraft(key: String)
    @Query("DELETE FROM drafts") abstract suspend fun clearDrafts()
    @Transaction open suspend fun save(entry: Entry, draftKey: String): Long { require(entry.notebookId == null || find(entry.notebookId)?.let { it.section == "Cuadernos" && it.deletedAt == 0L } == true) { "El cuaderno fue eliminado. Elige otro cuaderno." }; val id = put(entry); deleteDraft(draftKey); return id }
    @Transaction open suspend fun replaceAll(entries: List<Entry>) { clear(); clearDrafts(); insertAll(entries) }
    @Transaction open suspend fun importBackup(data: Backup.ImportData) {
        clear(); clearDrafts(); insertAll(data.entries); data.drafts.forEach { putDraft(it) }
    }
    @Transaction open suspend fun changeWater(day: String, delta: Int) {
        val existing = all().firstOrNull { it.section == "Diario" && it.title == "Agua" && it.date == day && it.deletedAt == 0L }
        put((existing ?: Entry(section = "Diario", title = "Agua", date = day)).copy(
            body = ((existing?.body?.toIntOrNull() ?: 0) + delta).coerceIn(0, 100).toString(),
            updatedAt = System.currentTimeMillis()))
    }
}


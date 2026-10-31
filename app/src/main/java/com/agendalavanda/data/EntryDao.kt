package com.agendalavanda.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries ORDER BY updatedAt DESC") abstract fun observeAll(): Flow<List<Entry>>
    @Query("SELECT * FROM entries WHERE section = :section ORDER BY updatedAt DESC") abstract fun observeSection(section: String): Flow<List<Entry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun put(entry: Entry): Long
    @Query("DELETE FROM entries WHERE id = :id") abstract suspend fun delete(id: Long)
    @Query("DELETE FROM entries") abstract suspend fun clear()
    @Query("SELECT * FROM entries ORDER BY id") abstract suspend fun all(): List<Entry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertAll(entries: List<Entry>)
    @Transaction open suspend fun replaceAll(entries: List<Entry>) { clear(); insertAll(entries) }
}

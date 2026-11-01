package com.agendalavanda.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities = [Entry::class, Draft::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN notebookId INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN progress INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN habitDates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN time TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN repeatRule TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entries_notebookId ON entries(notebookId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS drafts (key TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(key))")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "agenda-lavanda.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}


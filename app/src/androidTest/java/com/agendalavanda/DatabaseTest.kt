package com.agendalavanda
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import com.agendalavanda.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),AppDatabase::class.java)
    @Test fun migrationPreservesOriginalNotesAndAddsDrafts() {
        helper.createDatabase("migration-check",1).apply {
            execSQL("INSERT INTO entries (id, section,title,body,date,done,updatedAt) VALUES (1,'Notas','Original','No perder','',0,1)")
            close()
        }
        helper.runMigrationsAndValidate("migration-check",2,true,AppDatabase.MIGRATION_1_2).apply {
            query("SELECT body,deletedAt,notebookId FROM entries WHERE id=1").use { c-> assertTrue(c.moveToFirst());assertEquals("No perder",c.getString(0));assertEquals(0,c.getLong(1));assertTrue(c.isNull(2)) }
            close()
        }
    }
    @Test fun draftFinalSaveAndTrashRestoreAreConsistent()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val dao=db.entries()
            val book=dao.put(Entry(section="Cuadernos",title="Cuaderno"))
            val note=dao.put(Entry(section="Notas",title="Nota",notebookId=book))
            dao.trash(book,100)
            assertTrue(dao.all().all{it.deletedAt==100L})
            dao.restoreWithParent(dao.find(note)!!)
            assertTrue(dao.all().all{it.deletedAt==0L})
            val session=DraftSession(dao,"entry:$note")
            session.update(dao.find(note)!!.copy(body="Borrador"))
            session.flush()
            assertNotNull(dao.draft("entry:$note"))
            session.update(dao.find(note)!!.copy(body="Viejo"))
            session.save(dao.find(note)!!.copy(body="Final"))
            delay(600)
            assertNull(dao.draft("entry:$note"))
            assertEquals("Final",dao.find(note)!!.body)
            session.finish()
            dao.permanentlyDelete(book)
            assertTrue(dao.all().isEmpty())
        } finally {db.close()}
    }
}


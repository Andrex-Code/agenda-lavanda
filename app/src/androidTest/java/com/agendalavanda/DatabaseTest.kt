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
    @Test fun concurrentWaterUpdatesAndFailedDraftFlushAreSafe()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        val dao=db.entries()
        coroutineScope { (1..10).map { async {dao.changeWater("2026-11-01",1)} }.awaitAll() }
        assertEquals(1,dao.all().size)
        assertEquals("10",dao.all().single().body)
        coroutineScope { (1..10).map { async {dao.toggleRoutine("2026-11-01","Skincare mañana")} }.awaitAll() }
        val routines=dao.all().filter {it.title=="Skincare mañana"}
        assertEquals(1,routines.size)
        assertFalse(routines.single().done)
        val session=DraftSession(dao,"new:Notas:0",writeDraft={throw java.io.IOException("Fallo de almacenamiento simulado")})
        db.close()
        session.update(Entry(section="Notas",title="Debe conservarse"))
        var failed=false
        try {session.flush()} catch(e:Exception){failed=true}
        assertTrue("El cierre debe detectar que el borrador no se pudo escribir",failed)
        session.finish()
    }
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
            val sibling=dao.put(Entry(section="Notas",title="Otra página",notebookId=book))
            dao.trash(book,100)
            assertTrue(dao.all().all{it.deletedAt==100L})
            dao.restoreWithParent(dao.find(note)!!)
            assertEquals(0L,dao.find(book)!!.deletedAt)
            assertEquals(0L,dao.find(note)!!.deletedAt)
            assertEquals(100L,dao.find(sibling)!!.deletedAt)
            dao.restoreWithParent(dao.find(book)!!)
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


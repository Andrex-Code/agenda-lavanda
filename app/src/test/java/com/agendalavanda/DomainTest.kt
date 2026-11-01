package com.agendalavanda
import com.agendalavanda.data.*
import com.agendalavanda.ink.*
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

class DomainTest {
    @Test fun rejectsDraftWhoseOriginalWasDeletedOrKeyWasChanged() {
        val note=Entry(id=20,section="Notas",title="Página")
        val orphan=Draft("entry:20",EntryCodec.json(note).toString())
        assertThrows(IllegalArgumentException::class.java){Backup.decode(Backup.encode(emptyList(),"Agenda","",drafts=listOf(orphan)))}
        val wrongKey=orphan.copy(key="entry:99")
        assertThrows(IllegalArgumentException::class.java){Backup.decode(Backup.encode(listOf(note),"Agenda","",drafts=listOf(wrongKey)))}
    }
    @Test fun monthlyRecurrenceClampsToLastDayWithoutGoingBeforeStart() {
        val e=Entry(section="Calendario",title="Cita",date="2026-01-31",repeatRule="monthly")
        assertTrue(e.occursOn(LocalDate.parse("2026-02-28")))
        assertTrue(e.occursOn(LocalDate.parse("2026-03-31")))
        assertFalse(e.occursOn(LocalDate.parse("2026-01-01")))
        assertFalse(e.occursOn(LocalDate.parse("2026-03-28")))
    }
    @Test fun habitsPreserveHistoryAndStreakAcrossTodayAndYesterday() {
        val today=LocalDate.parse("2026-10-31")
        val e=Entry(section="Hábitos",title="Leer").toggleHabit(today.minusDays(2)).toggleHabit(today.minusDays(1))
        assertEquals(2,e.streak(today))
        assertEquals(3,e.toggleHabit(today).streak(today))
        assertFalse(e.toggleHabit(today.minusDays(1)).completedOn(today.minusDays(1)))
        assertEquals(0,e.streak(today.plusDays(1)))
    }
    @Test fun handwritingPreservesStyleAndLegacyText() {
        val old=InkDocument.decode("[MANUSCRITO][[[10,20],[30,40]]]\nMi reflexión")
        assertEquals("Mi reflexión",old.text)
        assertEquals(2,old.strokes.single().points.size)
        val d=old.copy(paper="dots",strokes=listOf(InkStroke(listOf(InkPoint(12f,30f,.5f)),0xff123456.toInt(),7f)))
        assertEquals(d,InkDocument.decode(d.encode()))
    }
    @Test fun eraserHitsSegmentBetweenSparseSamples() {
        val stroke=InkStroke(listOf(InkPoint(0f,0f),InkPoint(100f,0f)))
        assertTrue(stroke.hit(InkPoint(50f,2f),3f))
        assertFalse(stroke.hit(InkPoint(50f,30f),3f))
    }
    @Test fun backupPreservesRelationshipsTrashHistoryAndDrafts() {
        val book=Entry(id=8,section="Cuadernos",title="Ideas")
        val note=Entry(id=12,section="Notas",title="Página",notebookId=8,deletedAt=55)
        val draft=Draft("entry:12",EntryCodec.json(note).toString())
        val parsed=Backup.decode(Backup.encode(listOf(book,note),"Agenda","Yo",2,9,listOf(draft)))
        assertEquals(listOf(book,note),parsed.entries)
        assertEquals(listOf(draft),parsed.drafts)
        assertEquals(2,parsed.theme);assertEquals(9,parsed.waterGoal)
    }
    @Test fun rejectsOrphanPagesInsteadOfLosingTheirNotebook() {
        val note=Entry(id=12,section="Notas",title="Página",notebookId=8)
        assertThrows(IllegalArgumentException::class.java){Backup.decode(Backup.encode(listOf(note),"Agenda",""))}
    }
    @Test fun draftCanRecoverInvalidDateWhileFinalBackupEntriesRejectIt() {
        val e=Entry(section="Tareas",title="Pendiente",date="2026-")
        assertEquals("2026-",EntryCodec.read(EntryCodec.json(e),validate=false).date)
        assertThrows(IllegalArgumentException::class.java){EntryCodec.read(EntryCodec.json(e))}
    }
}


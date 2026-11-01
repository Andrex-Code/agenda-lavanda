package com.agendalavanda.data

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun displayPreferencesRoundTripAndOlderCopiesUseDefaults() {
        val restored=Backup.decode(Backup.encode(emptyList(),"Agenda","",showWater=false,showSkin=false,reminder=false))
        assertFalse(restored.showWater);assertFalse(restored.showSkin);assertFalse(restored.reminder)
        val old=Backup.decode("""{"format":"agenda-lavanda-backup","version":1,"entries":[]}""")
        assertTrue(old.showWater);assertTrue(old.showSkin);assertTrue(old.reminder)
    }
    @Test fun preservesLongHandwritingPayload() {
        val content = "[MANUSCRITO]" + "x".repeat(120_000)
        val source = Entry(section = "Notas", title = "Lápiz", body = content)
        assertEquals(content, Backup.decode(Backup.encode(listOf(source), "Agenda", "")).entries.single().body)
    }
    @Test fun backupRoundTripsDataAndProfile() {
        val source = listOf(Entry(section = "Diario", title = "Un día bonito", body = "😊\nReflexión", date = "2026-10-31", done = true))
        val restored = Backup.decode(Backup.encode(source, "Mi agenda", "Ana"))
        assertEquals("Mi agenda", restored.agendaName)
        assertEquals("Ana", restored.userName)
        assertEquals(source.single().copy(id = 0), restored.entries.single().copy(id = 0))
    }

    @Test fun rejectsWrongFormatAndUnknownSections() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("{}") }
        val bad = """{"format":"agenda-lavanda-backup","version":1,"entries":[{"section":"Sistema","title":"x"}]}"""
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(bad) }
    }

    @Test fun rejectsOversizedImport() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(" ".repeat(15_000_001)) }
    }
}

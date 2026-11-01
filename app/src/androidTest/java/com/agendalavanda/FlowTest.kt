package com.agendalavanda
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendalavanda.data.*
import com.agendalavanda.ink.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun start() {
        compose.waitUntil(60000){compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Notas").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Empezar").performClick()
    }
    private fun capture(name:String) {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val command="screencap -p /sdcard/Download/agenda-qa-$name.png"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {it.readBytes()}
    }
    @After fun captureResult(){capture("last-screen")}
    @Test fun calendarSurvivesRotation() {
        start()
        compose.onAllNodesWithText("Calendario").onFirst().performClick()
        compose.onNodeWithText("+ Evento para este día").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        val title="Evento "+System.nanoTime()
        compose.onNodeWithTag("entryTitle").performTextInput(title)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(30000){compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        compose.waitForIdle()
        compose.waitUntil(30000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
        compose.onNodeWithTag("calendarList").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed()
        capture("calendar-landscape")
        compose.runOnUiThread {compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        compose.waitUntil(30000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
    }
    @Test fun notebookPageDiaryAndGoalPersist() {
        start()
        fun menu(route:String) {
            if(compose.onAllNodesWithText("Más").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Más").performClick()
            compose.onAllNodesWithText(route).onLast().performScrollTo().performClick()
        }
        menu("Cuadernos")
        compose.onNodeWithText("+ Crear cuaderno").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        val book="Cuaderno "+System.nanoTime()
        compose.onNodeWithTag("entryTitle").performTextInput(book)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(30000){compose.onAllNodesWithText("▤  "+book).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("▤  "+book).performClick()
        compose.onNodeWithText("+ Nueva página en este cuaderno").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        val page="Página "+System.nanoTime()
        compose.onNodeWithTag("entryTitle").performTextInput(page)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(30000){compose.onAllNodesWithText(page).fetchSemanticsNodes().isNotEmpty()}
        val dao=AppDatabase.create(compose.activity).entries()
        val records=runBlocking {dao.all()}
        assertEquals(records.first{it.title==book}.id,records.first{it.title==page}.notebookId)
        val diaryNavigation=compose.onAllNodesWithText("Diario").onFirst()
        if(compose.activity.resources.configuration.screenWidthDp>=840) diaryNavigation.performScrollTo()
        diaryNavigation.performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("diaryList").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("diaryList").performScrollToNode(hasText("+ Un vaso"))
        compose.onNodeWithText("+ Un vaso").performClick()
        compose.onNodeWithTag("routine-Skincare mañana").performClick()
        compose.waitForIdle()
        compose.waitUntil(30000){runBlocking{dao.all().any{it.title=="Agua" && it.body.toIntOrNull()?.let{n->n>0}==true}}}
        menu("Metas")
        compose.onNodeWithText("+ Agregar metas").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        val goal="Meta "+System.nanoTime()
        compose.onNodeWithTag("entryTitle").performTextInput(goal)
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(50f)}
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(30000){runBlocking{dao.all().any{it.title==goal&&it.progress==50}}}
        capture("goals")
    }
    @Test fun handwritingSavedAndDraftRecovered() {
        val noteTitle="Manuscrito "+System.nanoTime()
        val draftTitle="Borrador "+System.nanoTime()
        compose.waitUntil(60000){compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Notas").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Empezar").performClick()
        compose.onAllNodesWithText("Notas").onFirst().performClick()
        compose.onNodeWithText("✎  Nueva página · texto y lápiz").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("entryTitle").performTextInput(noteTitle)
        compose.onNodeWithText("✎  Escribir con lápiz · 0 trazos").performScrollTo().performClick()
        compose.onNodeWithTag("inkCanvas").performTouchInput { swipe(Offset(100f,150f),Offset(230f,230f),500) }
        capture("ink-page")
        compose.onNodeWithText("Volver a la nota").performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(30000){compose.onAllNodesWithText(noteTitle).fetchSemanticsNodes().isNotEmpty()}
        val dao=AppDatabase.create(compose.activity).entries()
        val saved=runBlocking{dao.all().first{it.title==noteTitle}}
        assertTrue(InkDocument.decode(saved.body).strokes.isNotEmpty())
        compose.onNodeWithText("✎  Nueva página · texto y lápiz").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("entryTitle").performTextInput(draftTitle)
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithContentDescription("Borradores").performClick()
        compose.waitUntil(30000){compose.onAllNodesWithText(draftTitle).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(draftTitle).performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("entryTitle").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("entryTitle").assertTextContains(draftTitle)
    }
}


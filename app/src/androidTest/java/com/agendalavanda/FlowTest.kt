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
    @Test fun handwritingSavedAndDraftRecovered() {
        compose.waitUntil(15000){compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Notas").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("Empezar").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Empezar").performClick()
        compose.onAllNodesWithText("Notas").onFirst().performClick()
        compose.onNodeWithText("✎  Nueva página · texto y lápiz").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Título").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Título").performTextInput("Prueba manuscrita")
        compose.onNodeWithText("✎  Escribir con lápiz · 0 trazos").performScrollTo().performClick()
        compose.onNodeWithTag("inkCanvas").performTouchInput { swipe(Offset(100f,150f),Offset(230f,230f),500) }
        compose.onNodeWithText("Volver a la nota").performClick()
        compose.onNodeWithText("Guardar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Prueba manuscrita").fetchSemanticsNodes().isNotEmpty()}
        val dao=AppDatabase.create(compose.activity).entries()
        val saved=runBlocking{dao.all().first{it.title=="Prueba manuscrita"}}
        assertTrue(InkDocument.decode(saved.body).strokes.isNotEmpty())
        compose.onNodeWithText("✎  Nueva página · texto y lápiz").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Título").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Título").performTextInput("Borrador recuperable")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithContentDescription("Borradores").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Borrador recuperable").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Borrador recuperable").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Título").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Borrador recuperable").assertExists()
    }
}


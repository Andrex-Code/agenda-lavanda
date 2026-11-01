package com.agendalavanda

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agendalavanda.data.*
import com.agendalavanda.ink.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun EditorScreen(initial:Entry, dao:EntryDao, notebooks:List<Entry>, recovered:Draft?=null,onClose:()->Unit,onSaved:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val key=remember { recovered?.key ?: if(initial.id>0) "entry:${initial.id}" else "new:${initial.section}:${initial.notebookId ?: 0}" }
    val session=remember { DraftSession(dao,key) }
    var entry by remember { mutableStateOf(initial) }
    var doc by remember { mutableStateOf(InkDocument()) }
    var ready by remember { mutableStateOf(false) }
    var baseline by remember { mutableStateOf<Entry?>(null) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var ink by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var notebookMenu by remember { mutableStateOf(false) }
    val status by session.status.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    LaunchedEffect(key) {
        try {
            val draft=recovered ?: dao.draft(key)
            entry=draft?.let { EntryCodec.read(JSONObject(it.payload),validate=false) } ?: initial
            doc=InkDocument.decode(entry.body);baseline=initial.copy(body=InkDocument.decode(initial.body).encode());ready=true
        } catch(e:Exception) { error="No se pudo abrir la página: ${e.message}. El contenido original se conserva." }
    }
    val candidate=remember(entry,doc) { entry.copy(body=doc.encode()) }
    val currentCandidate by rememberUpdatedState(candidate)
    LaunchedEffect(candidate,ready) { if(ready && candidate != baseline) session.update(candidate) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP && ready) scope.launch { try { session.update(currentCandidate);session.flush() } catch(e:Exception) { error="No se pudo guardar el borrador: ${e.message}" } } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer);session.finish() }
    }
    fun close() { if(saving) return; if(!ready) {onClose();return}; scope.launch { try { if(candidate==baseline) session.discard() else {session.update(candidate);session.flush()};onClose() } catch(e:Exception) { error="No se pudo guardar el borrador. Permanece abierto para reintentar." } } }
    fun save() {
        saving=true
        scope.launch {
            try { session.save(candidate.copy(title=entry.title.trim(),updatedAt=System.currentTimeMillis()));onSaved() }
            catch(e:Exception) {error="No se pudo guardar: ${e.message}";saving=false}
        }
    }
    var exportPair by remember { mutableStateOf<Pair<String,InkDocument>?>(null) }
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) exportPair?.let { (title,document) -> scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use {PageExport.pdf(title,document,it)} ?: error("No se pudo abrir el destino.") };error="PDF exportado." }
            catch(e:Exception) {error="No se pudo exportar: ${e.message}"}
        } }
    }
    val png=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if(uri!=null) exportPair?.let { (_,document) -> scope.launch {
            try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {PageExport.png(document,it)} ?: error("No se pudo abrir el destino.")};error="Imagen de la hoja exportada."}
            catch(e:Exception){error="No se pudo exportar: ${e.message}"}
        } }
    }
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val keyboard=LocalSoftwareKeyboardController.current
        val focus=LocalFocusManager.current
        DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
        BackHandler { focus.clearFocus();keyboard?.hide();close() }
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={focus.clearFocus();keyboard?.hide();close()},enabled=!saving){Text("Cerrar")}
                    Text(entry.section,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
                    Button(onClick={focus.clearFocus();keyboard?.hide();save()},enabled=ready && !saving && entry.title.isNotBlank() &&
                        (entry.date.isBlank()||runCatching {LocalDate.parse(entry.date)}.isSuccess) &&
                        (entry.time.isBlank()||runCatching {LocalTime.parse(entry.time)}.isSuccess)) { Text(if(saving) "Guardando…" else "Guardar") }
                }
                if(!ready && error.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.weight(1f).fillMaxWidth().widthIn(max=1000.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.primary)
                    if(ready) {
                        Text(status,style=MaterialTheme.typography.bodySmall)
                        OutlinedTextField(entry.title,{entry=entry.copy(title=it)},label={Text("Título")},modifier=Modifier.fillMaxWidth().testTag("entryTitle"),singleLine=true)
                        if(entry.section in listOf("Calendario","Tareas","Diario","Metas")) {
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={
                                    val day=runCatching {LocalDate.parse(entry.date)}.getOrDefault(LocalDate.now())
                                    DatePickerDialog(context,{_,y,m,d->entry=entry.copy(date=LocalDate.of(y,m+1,d).toString())},day.year,day.monthValue-1,day.dayOfMonth).show()
                                }){Text(if(entry.date.isBlank()) "Elegir fecha" else entry.date)}
                                if(entry.section in listOf("Calendario","Tareas")) OutlinedButton(onClick={
                                    val t=runCatching {LocalTime.parse(entry.time)}.getOrDefault(LocalTime.of(9,0))
                                    TimePickerDialog(context,{_,h,m->entry=entry.copy(time=LocalTime.of(h,m).toString())},t.hour,t.minute,true).show()
                                }){Text(entry.time.ifBlank {"Elegir hora"})}
                                TextButton(onClick={entry=entry.copy(date="",time="",repeatRule="")}){Text("Sin fecha")}
                            }
                        }
                        if(entry.section=="Calendario") {
                            Text("Repetir")
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("" to "Nunca","daily" to "Cada día","weekly" to "Cada semana","monthly" to "Cada mes").forEach { (key,label) ->
                                    FilterChip(selected=entry.repeatRule==key,onClick={entry=entry.copy(repeatRule=key)},label={Text(label)})
                                }
                            }
                        }
                        if(entry.section=="Notas") {
                            Box {
                                OutlinedButton(onClick={notebookMenu=true}) {Text(notebooks.find {it.id==entry.notebookId}?.title ?: "Sin cuaderno")}
                                DropdownMenu(expanded=notebookMenu,onDismissRequest={notebookMenu=false}) {
                                    DropdownMenuItem(text={Text("Sin cuaderno")},onClick={entry=entry.copy(notebookId=null);notebookMenu=false})
                                    notebooks.forEach { book -> DropdownMenuItem(text={Text(book.title)},onClick={entry=entry.copy(notebookId=book.id);notebookMenu=false}) }
                                }
                            }
                        }
                        if(entry.section=="Metas") {
                            Text("Avance: ${entry.progress}%")
                            Slider(value=entry.progress.toFloat(),onValueChange={entry=entry.copy(progress=it.toInt(),done=it.toInt()==100)},valueRange=0f..100f,steps=19)
                        }
                        if(entry.section=="Tareas") Row { Checkbox(entry.done,{entry=entry.copy(done=it)});Text("Completada",Modifier.padding(top=12.dp)) }
                        if(entry.section=="Diario" && entry.title !in listOf("Agua","Skincare mañana","Skincare noche")) {
                            Text("¿Cómo te sientes?")
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                listOf("😊","🙂","😐","😔","😴").forEach { mood ->
                                    TextButton(onClick={doc=doc.copy(text=mood+" "+doc.text.replace(Regex("^(😊|🙂|😐|😔|😴)\\s*"),""))}){Text(mood)}
                                }
                            }
                        }
                        if(entry.section in listOf("Notas","Diario","Cuadernos")) {
                            Button(onClick={ink=true},modifier=Modifier.fillMaxWidth()) {Text("✎  Escribir con lápiz · ${doc.strokes.size} trazos")}
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick={doc=doc.copy(text=doc.text+"\n• Prioridad del día:\n• Pendientes:\n• Algo bueno de hoy:\n")}){Text("+ Plantilla diaria")}
                                TextButton(onClick={doc=doc.copy(text=doc.text+"\n☐ ")}){Text("+ Casilla")}
                            }
                        }
                        OutlinedTextField(doc.text,{doc=doc.copy(text=it)},label={Text("Escribe aquí")},modifier=Modifier.fillMaxWidth(),minLines=8)
                        if(doc.strokes.isNotEmpty()) {
                            Text("Vista de la hoja",style=MaterialTheme.typography.titleSmall)
                            androidx.compose.ui.viewinterop.AndroidView(factory={ctx->PagePreview(ctx)},modifier=Modifier.fillMaxWidth().height(200.dp),update={it.page=doc;it.invalidate()})
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick={exportPair=entry.title to doc;pdf.launch("pagina.pdf")}){Text("Exportar PDF")}
                            if(doc.strokes.isNotEmpty()) TextButton(onClick={exportPair=entry.title to doc;png.launch("hoja.png")}){Text("Exportar hoja PNG")}
                            TextButton(onClick={discard=true}){Text("Descartar borrador")}
                        }
                    }
                }
            }
        }
    }
    if(ink) InkEditor(doc,{doc=it},{ink=false})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("¿Descartar los cambios del borrador?")},
        text={Text("La última versión que guardaste de la página seguirá disponible.")},
        confirmButton={TextButton(onClick={scope.launch {session.discard();onClose()}}){Text("Descartar")}},
        dismissButton={TextButton(onClick={discard=false}){Text("Conservar")}})
}


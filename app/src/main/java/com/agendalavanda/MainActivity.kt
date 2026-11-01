package com.agendalavanda

import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agendalavanda.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

private val android.content.Context.prefs by preferencesDataStore("profile")
private val pAgenda=stringPreferencesKey("agenda_name")
private val pUser=stringPreferencesKey("user_name")
private val pTheme=intPreferencesKey("theme")
private val pWaterGoal=intPreferencesKey("water_goal")
private val pLastBackup=stringPreferencesKey("last_backup")

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val db=AppDatabase.create(this)
        setContent { AgendaApp(db.entries()) }
    }
}
private val sections=listOf("Inicio","Calendario","Tareas","Notas","Cuadernos","Diario","Hábitos","Metas","Borradores","Papelera","Ajustes")
private val icons=listOf(Icons.Default.Home,Icons.Default.CalendarMonth,Icons.Default.CheckCircle,Icons.Default.Edit,
    Icons.Default.Book,Icons.Default.Favorite,Icons.Default.AutoAwesome,Icons.Default.Flag,Icons.Default.History,Icons.Default.DeleteOutline,Icons.Default.Settings)
val palettes=listOf(
    listOf(Color(0xFFF4EFFB),Color(0xFFE8DDF7),Color(0xFF75539B),Color(0xFFFAF8FD)),
    listOf(Color(0xFFFFF0F1),Color(0xFFFFDDE1),Color(0xFFAD586E),Color(0xFFFFFAFA)),
    listOf(Color(0xFFEDF5F1),Color(0xFFD7EBE1),Color(0xFF4E8068),Color(0xFFF8FCF9)),
    listOf(Color(0xFFFFF5E9),Color(0xFFFFE6C7),Color(0xFF9C6A2E),Color(0xFFFFFBF5)))

@Composable
fun AgendaApp(dao:EntryDao) {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    val records by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    val drafts by dao.observeDrafts().collectAsStateWithLifecycle(emptyList())
    val preferenceFlow=remember(ctx){ctx.prefs.data.map { it as Preferences? }}
    val prefs by preferenceFlow.collectAsStateWithLifecycle(null)
    val all=records.filter { it.deletedAt==0L }
    val name=prefs?.get(pAgenda).orEmpty()
    val user=prefs?.get(pUser).orEmpty()
    val theme=(prefs?.get(pTheme) ?: 0).coerceIn(0,3)
    val goal=(prefs?.get(pWaterGoal) ?: 8).coerceIn(1,30)
    val c=palettes[theme]
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while(true) { today=LocalDate.now();delay(30000) } }
    var page by rememberSaveable { mutableStateOf("Inicio") }
    var menu by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<Entry?>(null) }
    var resumed by remember { mutableStateOf<Draft?>(null) }
    var editorId by remember { mutableIntStateOf(0) }
    var pendingImport by remember { mutableStateOf<Backup.ImportData?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snack=remember { SnackbarHostState() }
    fun action(block:suspend ()->Unit) { scope.launch { try {block()} catch(e:Exception) {if(e is CancellationException) throw e;snack.showSnackbar(e.message ?: "No se pudo completar. Intenta de nuevo.")} } }
    fun open(e:Entry,d:Draft?=null) { editorId++;resumed=d;entry=e }
    fun create(section:String,date:String="") = Entry(section=section,title="",date=date)
    fun toggle(e:Entry) { action { dao.toggleEntry(e.id,today) } }
    val safety=remember { File(ctx.filesDir,"before-import.json") }
    suspend fun snapshot():String { val (saved,pending)=dao.snapshot();return withContext(Dispatchers.Default){Backup.encode(saved,name,user,theme,goal,pending)} }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri:Uri? ->
        if(uri!=null) action {
            val raw=snapshot()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) } ?: error("No se pudo escribir la copia.") }
            ctx.prefs.edit {it[pLastBackup]=today.toString()};snack.showSnackbar("Copia exportada, incluidos los borradores.")
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri? ->
        if(uri!=null) action {
            busy=true
            try { pendingImport=withContext(Dispatchers.IO) {
                val bytes=ctx.contentResolver.openInputStream(uri)?.use { input ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) {val n=input.read(buffer);if(n<0)break;require(out.size()+n<=Backup.MAX_BYTES){"El archivo supera 15 MB."};out.write(buffer,0,n)};out.toByteArray()
                } ?: error("No se pudo leer el archivo.")
                Backup.decode(String(bytes,Charsets.UTF_8))
            } } finally {busy=false}
        }
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=c[2],secondary=c[2],background=c[0],surface=c[3])) {
        if(prefs==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return@MaterialTheme }
        BoxWithConstraints(Modifier.fillMaxSize().background(c[0]).safeDrawingPadding()) {
            val wide=maxWidth>=840.dp
            Row(Modifier.fillMaxSize()) {
                if(wide) Column(Modifier.width(164.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text("✿  Tu espacio",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(12.dp))
                    sections.forEachIndexed { i,s -> NavigationDrawerItem(label={Text(s)},selected=page==s,onClick={page=s},
                        icon={Icon(icons[i],null)},modifier=Modifier.padding(vertical=2.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)){Text(name.ifBlank {"Mi Agenda"},style=MaterialTheme.typography.titleLarge);Text(page,style=MaterialTheme.typography.labelLarge,color=c[2])}
                        IconButton(onClick={page="Borradores"}){Icon(Icons.Default.History,"Borradores")}
                        IconButton(onClick={page="Ajustes"}){Icon(Icons.Default.Settings,"Ajustes")}
                    }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when(page) {
                            "Inicio" -> HomeScreen(all,user,today,goal,drafts.size,{page=it},{open(it)})
                            "Calendario" -> CalendarScreen(all,today,{open(it)},{day->open(create("Calendario",day.toString()))})
                            "Cuadernos" -> NotebooksScreen(all,{open(it)},{id->open(create("Notas").copy(notebookId=id))},{open(create("Cuadernos"))},{e->action{dao.trash(e.id,System.currentTimeMillis());snack.showSnackbar("Cuaderno y páginas enviados a Papelera.")}})
                            "Hábitos" -> HabitsScreen(all.filter{it.section=="Hábitos"},today,{open(create("Hábitos"))},{open(it)},{toggle(it)},{e->action{dao.trash(e.id,System.currentTimeMillis())}})
                            "Diario" -> DiaryScreen(all.filter{it.section=="Diario"},today,goal,{open(it)}, {day,delta->action{dao.changeWater(day.toString(),delta)}},
                                {day,label->action{dao.toggleRoutine(day.toString(),label)}},
                                {e->action{dao.trash(e.id,System.currentTimeMillis())}})
                            "Borradores" -> DraftsScreen(drafts,{d->action{open(EntryCodec.read(JSONObject(d.payload),validate=false),d)}},{d->action{dao.deleteDraft(d.key)}})
                            "Papelera" -> TrashScreen(records.filter{it.deletedAt>0},{e->action{dao.restoreWithParent(e)}},{e->action{dao.permanentlyDelete(e.id)}})
                            "Ajustes" -> SettingsScreen(name,user,theme,goal,prefs?.get(pLastBackup).orEmpty(),safety.exists(),
                                {n,u,t,g->action{ctx.prefs.edit {it[pAgenda]=n;if(n.isBlank())it[pAgenda]="Mi Agenda";it[pUser]=u;it[pTheme]=t;it[pWaterGoal]=g};snack.showSnackbar("Preferencias guardadas.")}},
                                {export.launch("agenda-${today}.json")},{import.launch(arrayOf("application/json","text/*","application/octet-stream"))},
                                {action{pendingImport=withContext(Dispatchers.IO){Backup.decode(String(AtomicFile(safety).readFully(),Charsets.UTF_8))}}})
                            else -> EntryListScreen(page,all.filter{it.section==page},{open(create(page))},{open(it)},{toggle(it)},{e->action{dao.trash(e.id,System.currentTimeMillis());snack.showSnackbar("Enviado a Papelera.")}})
                        }
                    }
                    if(!wide) NavigationBar(containerColor=c[3]) {
                        listOf("Inicio","Calendario","Notas","Diario").forEach { s ->
                            NavigationBarItem(selected=page==s,onClick={page=s},icon={Icon(icons[sections.indexOf(s)],null)},label={Text(s)})
                        }
                        NavigationBarItem(selected=page !in listOf("Inicio","Calendario","Notas","Diario"),onClick={menu=true},icon={Icon(Icons.Default.Menu,null)},label={Text("Más")})
                    }
                }
            }
            SnackbarHost(snack,Modifier.align(Alignment.BottomCenter))
            if(menu) AlertDialog(onDismissRequest={menu=false},title={Text("Tu agenda")},text={
                Column(Modifier.verticalScroll(rememberScrollState())) {sections.forEach{s->TextButton(onClick={page=s;menu=false},modifier=Modifier.fillMaxWidth()){Text(s)}}}
            },confirmButton={TextButton(onClick={menu=false}){Text("Cerrar")}})
            entry?.let { initial -> key(editorId) {
                EditorScreen(initial,dao,all.filter{it.section=="Cuadernos"},resumed,
                    onClose={entry=null;resumed=null},onSaved={entry=null;resumed=null;action{snack.showSnackbar("Guardado")}})
            } }
            if(name.isBlank()) ProfileDialog { n,u -> action {ctx.prefs.edit{it[pAgenda]=n.ifBlank{"Mi Agenda"};it[pUser]=u}} }
            pendingImport?.let { data -> AlertDialog(onDismissRequest={if(!busy)pendingImport=null},
                title={Text("¿Restaurar esta copia?")},text={Text("${data.entries.size} registros y ${data.drafts.size} borradores. Reemplazará el contenido actual. Se conserva una copia local previa para deshacer la importación.")},
                confirmButton={TextButton(enabled=!busy,onClick={action{
                    busy=true
                    try {
                        val raw=snapshot()
                        withContext(Dispatchers.IO) {
                            val file=AtomicFile(safety);val out=file.startWrite()
                            try {out.write(raw.toByteArray(Charsets.UTF_8));file.finishWrite(out)} catch(e:Exception){file.failWrite(out);throw e}
                        }
                        dao.importBackup(data)
                        ctx.prefs.edit{it[pAgenda]=data.agendaName.ifBlank{"Mi Agenda"};it[pUser]=data.userName;it[pTheme]=data.theme;it[pWaterGoal]=data.waterGoal}
                        pendingImport=null;snack.showSnackbar("Copia restaurada.")
                    } finally {busy=false}
                }}){Text("Restaurar")}},dismissButton={TextButton(enabled=!busy,onClick={pendingImport=null}){Text("Cancelar")}})
            }
        }
    }
}
@Composable
private fun ProfileDialog(save:(String,String)->Unit) {
    var n by rememberSaveable{mutableStateOf("Mi Agenda")};var u by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest={},title={Text("Bienvenida a tu espacio")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Escribe, organiza tus días y cuida de ti. Todo se guarda en este dispositivo.")
        OutlinedTextField(n,{n=it.take(60)},label={Text("Nombre de la agenda")})
        OutlinedTextField(u,{u=it.take(60)},label={Text("Tu nombre (opcional)")})
    }},confirmButton={Button(onClick={save(n,u)}){Text("Empezar")}})
}


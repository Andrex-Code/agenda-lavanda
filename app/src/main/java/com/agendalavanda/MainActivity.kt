package com.agendalavanda

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.agendalavanda.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream

private val android.content.Context.prefs by preferencesDataStore("profile")
private val pAgenda = stringPreferencesKey("agenda_name")
private val pUser = stringPreferencesKey("user_name")
private val pTheme = intPreferencesKey("theme")
private val pWaterGoal = intPreferencesKey("water_goal")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.create(this)
        setContent { AgendaApp(db.entries()) }
    }
}

private val sections = listOf("Inicio", "Calendario", "Tareas", "Notas", "Cuadernos", "Diario", "Hábitos", "Metas", "Ajustes")
private val icons = listOf(Icons.Default.Home, Icons.Default.CalendarMonth, Icons.Default.CheckCircle, Icons.Default.Edit,
    Icons.Default.Book, Icons.Default.Favorite, Icons.Default.AutoAwesome, Icons.Default.Flag, Icons.Default.Settings)
private val palettes = listOf(
    listOf(Color(0xFFF4EFFB), Color(0xFFE8DDF7), Color(0xFF75539B), Color(0xFFFAF8FD)),
    listOf(Color(0xFFFFF0F1), Color(0xFFFFDDE1), Color(0xFFAD586E), Color(0xFFFFFAFA)),
    listOf(Color(0xFFEDF5F1), Color(0xFFD7EBE1), Color(0xFF4E8068), Color(0xFFF8FCF9)),
    listOf(Color(0xFFFFF5E9), Color(0xFFFFE6C7), Color(0xFF9C6A2E), Color(0xFFFFFBF5))
)

@Composable
private fun AgendaApp(dao: EntryDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    val prefs by context.prefs.data.map { it as Preferences? }.collectAsStateWithLifecycle(initialValue = null)
    val name = prefs?.get(pAgenda) ?: ""
    val user = prefs?.get(pUser) ?: ""
    val theme = (prefs?.get(pTheme) ?: 0).coerceIn(0, palettes.lastIndex)
    val goal = (prefs?.get(pWaterGoal) ?: 8).coerceIn(1, 30)
    val colors = palettes[theme]
    var page by rememberSaveable { mutableStateOf("Inicio") }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    var calendarMode by remember { mutableStateOf("Mes") }
    var monthOffset by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(prefs) { if (prefs != null) onboarding = name.isBlank() }
    LaunchedEffect(toast) { if (toast.isNotBlank()) { snack.showSnackbar(toast); toast = "" } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(Backup.encode(dao.all(), name.ifBlank { "Mi Agenda" }, user)) }
                ?: error("No se pudo guardar la copia.")
        }.onSuccess { toast = "Copia de seguridad guardada." }.onFailure { toast = "No se pudo exportar: ${it.message}" } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (stream.read(buffer).also { count = it } != -1) {
                    require(out.size() + count <= 15_000_000) { "El archivo supera el límite de 15 MB." }
                    out.write(buffer, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            } ?: error("No se pudo leer el archivo.")
            Backup.decode(raw)
        }.onSuccess { data ->
            runCatching { dao.replaceAll(data.entries); context.prefs.edit { it[pAgenda] = data.agendaName; it[pUser] = data.userName }; toast = "Copia importada." }
                .onFailure { toast = "No se pudo aplicar la copia: ${it.message}" }
        }.onFailure { toast = "Copia no válida: ${it.message}" } }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = colors[2], secondary = colors[2], background = colors[0], surface = colors[3])) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors[0]).safeDrawingPadding()) {
            val wide = maxWidth >= 700.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) NavigationRail(modifier = Modifier.verticalScroll(rememberScrollState()), containerColor = colors[0], header = {
                    Text("✿", fontSize = 30.sp, color = colors[2], modifier = Modifier.padding(14.dp))
                }) { sections.forEachIndexed { i, s -> NavigationRailItem(selected = page == s, onClick = { page = s }, icon = { Icon(icons[i], null) }, label = { Text(s) }, alwaysShowLabel = true) } }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name.ifBlank { "Mi Agenda" }, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = colors[2])
                            if (user.isNotBlank()) Text("Un espacio para ti, $user", color = Color(0xFF786F7F), fontSize = 13.sp)
                        }
                        if (page != "Ajustes") IconButton(onClick = { page = "Ajustes" }) { Icon(Icons.Default.Settings, "Ajustes") }
                    }
                    HorizontalDivider(color = colors[1])
                    Box(Modifier.weight(1f).fillMaxWidth()) { when (page) {
                        "Inicio" -> HomePage(all, name, user, colors, goal, { page = it }, { page = "Tareas"; showForm = true; editing = null })
                        "Calendario" -> CalendarPage(all, colors, calendarMode, { calendarMode = it }, monthOffset, { monthOffset += it }, { showForm = true; editing = null })
                        "Ajustes" -> SettingsPage(name, user, theme, goal, colors, onSave = { n, u, t, g -> scope.launch { context.prefs.edit { it[pAgenda] = n; it[pUser] = u; it[pTheme] = t; it[pWaterGoal] = g } }; toast = "Preferencias guardadas." }, onExport = { export.launch("agenda-lavanda-${LocalDate.now()}.json") }, onImport = { import.launch(arrayOf("application/json", "text/*")) })
                        else -> EntriesPage(page, all.filter { it.section == page }, colors, goal, { showForm = true; editing = null }, { editing = it; showForm = true }, { entry -> scope.launch { dao.put(entry.copy(done = !entry.done, updatedAt = System.currentTimeMillis())) } }, { entry -> scope.launch { dao.delete(entry.id) } }, { label -> scope.launch { dao.put(Entry(section = page, title = label, date = if (page == "Diario") LocalDate.now().toString() else "")) } }, { amount ->
                            scope.launch {
                                val current = all.lastOrNull { it.section == "Diario" && it.title == "Agua" && it.date == LocalDate.now().toString() }
                                dao.put((current ?: Entry(section = "Diario", title = "Agua", date = LocalDate.now().toString())).copy(body = amount.coerceAtLeast(0).toString(), updatedAt = System.currentTimeMillis()))
                            }
                        })
                    }
                    }
                    if (!wide) NavigationBar(containerColor = colors[3]) {
                        sections.take(4).forEachIndexed { i, s -> NavigationBarItem(selected = page == s, onClick = { page = s }, icon = { Icon(icons[i], null) }, label = { Text(s, fontSize = 10.sp) }) }
                        NavigationBarItem(selected = page == "Diario", onClick = { page = "Diario" }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Diario", fontSize = 10.sp) })
                        NavigationBarItem(selected = page in listOf("Cuadernos", "Hábitos", "Metas", "Ajustes"), onClick = { moreOpen = true }, icon = { Icon(Icons.Default.Menu, null) }, label = { Text("Más", fontSize = 10.sp) })
                    }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
            if (showForm) EntryDialog(page, editing, colors, onDismiss = { showForm = false }, onSave = { entry -> scope.launch { dao.put(entry); showForm = false } })
            if (onboarding) ProfileDialog(name, user, colors, onSave = { n, u -> scope.launch { context.prefs.edit { it[pAgenda] = n; it[pUser] = u } }; onboarding = false })
            if (moreOpen) AlertDialog(onDismissRequest = { moreOpen = false }, title = { Text("Más secciones") }, text = { Column { listOf("Cuadernos", "Hábitos", "Metas", "Ajustes").forEach { destination -> TextButton(onClick = { page = destination; moreOpen = false }, modifier = Modifier.fillMaxWidth()) { Text(destination, modifier = Modifier.fillMaxWidth()) } } } }, confirmButton = { TextButton(onClick = { moreOpen = false }) { Text("Cerrar") } })
        }
    }
}

@Composable
private fun HomePage(all: List<Entry>, name: String, user: String, c: List<Color>, goal: Int, navigate: (String) -> Unit, add: () -> Unit) {
    val today = LocalDate.now().toString()
    val tasks = all.filter { it.section == "Tareas" && !it.done }
    val water = all.lastOrNull { it.section == "Diario" && it.title == "Agua" && it.date == today }?.body?.toIntOrNull() ?: 0
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Surface(shape = RoundedCornerShape(26.dp), color = c[1], modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp)) {
            Text("${greeting()}, ${user.ifBlank { "qué gusto verte" }} ✨", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c[2])
            Text(todayLabel(), color = c[2], modifier = Modifier.padding(top = 6.dp))
            Text("Tu agenda, a tu ritmo.", color = c[2], modifier = Modifier.padding(top = 14.dp))
        } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Pendientes", "${tasks.size}", "Tareas", c, Modifier.weight(1f), navigate)
            SummaryCard("Agua", "$water / $goal vasos", "Diario", c, Modifier.weight(1f), navigate)
        } }
        item { SectionHeader("Para hoy", "Ver calendario") { navigate("Calendario") } }
        val todayItems = all.filter { it.date == today && !it.done }
        if (todayItems.isEmpty()) item { EmptyCard("No tienes nada pendiente para hoy. Puedes agregar una tarea o disfrutar el espacio libre.", c, add) }
        else items(todayItems.take(5)) { EntryRow(it, c, { navigate(it.section) }, {}) }
        item { SectionHeader("Tu espacio", "Agregar") { add() } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tareas", "Notas", "Diario", "Hábitos").forEach { s -> OutlinedButton(onClick = { navigate(s) }, shape = RoundedCornerShape(15.dp)) { Text(s) } }
        } }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, route: String, c: List<Color>, modifier: Modifier, navigate: (String) -> Unit) {
    Card(onClick = { navigate(route) }, modifier = modifier, colors = CardDefaults.cardColors(containerColor = c[3]), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text(title, color = Color.Gray); Text(value, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c[2]) } }
}

@Composable
private fun CalendarPage(all: List<Entry>, c: List<Color>, mode: String, setMode: (String) -> Unit, offset: Int, shift: (Int) -> Unit, add: () -> Unit) {
    val today = LocalDate.now()
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    val anchor = today.plusWeeks(offset.toLong())
    val start = when (mode) { "Día" -> today.plusDays(offset.toLong()); "Semana" -> anchor.with(java.time.DayOfWeek.MONDAY); else -> month.atDay(1) }
    val days = when (mode) { "Día" -> listOf(start); "Semana" -> (0..6).map { start.plusDays(it.toLong()) }; else -> (1..month.lengthOfMonth()).map { month.atDay(it) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Anterior") }
            Text(if (mode == "Mes") month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) else if (mode == "Semana") "Semana del ${start.format(DateTimeFormatter.ofPattern("d MMM"))}" else start.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { shift(1) }) { Icon(Icons.Default.ChevronRight, "Siguiente") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Mes", "Semana", "Día").forEach { FilterChip(selected = mode == it, onClick = { setMode(it) }, label = { Text(it) }) } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { day ->
                val entries = all.filter { it.date == day.toString() }
                Card(colors = CardDefaults.cardColors(containerColor = if (day == today) c[1] else c[3]), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(day.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c[2]); if (day == today) Text("HOY", color = c[2], fontSize = 11.sp) }
                        if (entries.isEmpty()) Text("Sin eventos", Modifier.padding(top = 5.dp), color = Color.Gray, fontSize = 13.sp)
                        entries.forEach { Text("• ${it.title}${if (it.done) " ✓" else ""}", Modifier.padding(top = 5.dp), color = Color(0xFF59525F)) }
                    }
                }
            }
        }
        FloatingActionButton(onClick = add, modifier = Modifier.align(Alignment.End).padding(18.dp), containerColor = c[2], contentColor = Color.White) { Icon(Icons.Default.Add, "Añadir evento") }
    }
}

@Composable
private fun EntriesPage(page: String, entries: List<Entry>, c: List<Color>, goal: Int, add: () -> Unit, edit: (Entry) -> Unit, toggle: (Entry) -> Unit, delete: (Entry) -> Unit, quickAdd: (String) -> Unit, setWater: (Int) -> Unit) {
    val today = LocalDate.now().toString()
    val water = entries.lastOrNull { it.title == "Agua" && it.date == today }?.body?.toIntOrNull() ?: 0
    var query by rememberSaveable(page) { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    if (pendingDelete != null) AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("¿Eliminar este elemento?") }, text = { Text("Esta acción no se puede deshacer. Exporta una copia para conservar tus datos.") }, confirmButton = { TextButton(onClick = { pendingDelete?.let(delete); pendingDelete = null }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } })
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar en $page") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (page in listOf("Notas", "Cuadernos", "Diario")) Button(onClick = add, modifier = Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Default.Draw, null); Text("  Nueva página · texto y lápiz") }
        if (page == "Diario") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Skincare mañana", "Skincare noche").forEach { label ->
                    val routine = entries.firstOrNull { it.title == label && it.date == today }
                    FilterChip(selected = routine?.done == true, onClick = { if (routine == null) quickAdd(label) else toggle(routine) }, label = { Text("${if (routine?.done == true) "✓" else "＋"} $label") })
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = c[1]), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Agua de hoy 💧", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c[2])
                    Text("$water de $goal vasos", color = c[2], modifier = Modifier.padding(top = 6.dp))
                    LinearProgressIndicator(progress = { (water.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), color = c[2], trackColor = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { setWater(water - 1) }, enabled = water > 0) { Text("−") }; Button(onClick = { setWater(water + 1) }, colors = ButtonDefaults.buttonColors(containerColor = c[2])) { Text("+ Añadir vaso") } }
                }
            }
            Text("Diario y autocuidado", Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp), color = c[2], fontWeight = FontWeight.SemiBold)
        }
        if (page == "Hábitos") Text("Marca tus hábitos al completarlos", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.Gray)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) item { EmptyCard(emptyMessage(page), c, add) }
            items(entries.filterNot { page == "Diario" && it.title == "Agua" }.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }, key = { it.id }) { entry -> EntryRow(entry, c, { edit(entry) }, { toggle(entry) }, { pendingDelete = entry }) }
        }
        FloatingActionButton(onClick = add, modifier = Modifier.align(Alignment.End).padding(18.dp), containerColor = c[2], contentColor = Color.White) { Icon(Icons.Default.Add, "Agregar") }
    }
}

@Composable
private fun EntryRow(entry: Entry, c: List<Color>, edit: () -> Unit, toggle: () -> Unit, delete: (() -> Unit)? = null) {
    Card(onClick = edit, colors = CardDefaults.cardColors(containerColor = c[3]), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.section in listOf("Tareas", "Hábitos") || entry.title.startsWith("Skincare")) Checkbox(entry.done, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = c[2]))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.SemiBold, color = if (entry.done) Color.Gray else Color(0xFF39333F))
                if (entry.date.isNotBlank()) Text(entry.date, color = Color.Gray, fontSize = 12.sp)
                val summary = when { entry.body.startsWith("[MANUSCRITO]") -> "Nota escrita a mano"; entry.body.isNotBlank() -> entry.body.removePrefix("[MANUSCRITO]").takeIf { it.isNotBlank() }; else -> null }
                if (summary != null) Text(summary.take(160), color = Color(0xFF69616E), fontSize = 13.sp, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = edit) { Icon(Icons.Default.Edit, "Editar", tint = c[2]) }
            if (delete != null) IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = Color.Gray) }
        }
    }
}

@Composable
private fun EntryDialog(section: String, initial: Entry?, c: List<Color>, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var title by rememberSaveable(initial?.id, section) { mutableStateOf(initial?.title ?: defaultTitle(section)) }
    var body by rememberSaveable(initial?.id) { mutableStateOf(initial?.body?.substringAfter("\n", "")?.takeIf { initial.body.startsWith("[MANUSCRITO]") } ?: initial?.body.orEmpty()) }
    var date by rememberSaveable(initial?.id) { mutableStateOf(initial?.date ?: if (section == "Diario" || section == "Calendario" || section == "Tareas") LocalDate.now().toString() else "") }
    var done by rememberSaveable(initial?.id) { mutableStateOf(initial?.done ?: false) }
    var ink by remember { mutableStateOf(false) }
    var strokes by remember(initial) { mutableStateOf(parseStrokes(initial?.body.orEmpty())) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Nuevo ${section.lowercase()}" else "Editar") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (date.isNotBlank() && runCatching { LocalDate.parse(date) }.isFailure) Text("Usa una fecha válida: AAAA-MM-DD", color = MaterialTheme.colorScheme.error)
            InputField(title, { title = it }, "Título")
            if (section in listOf("Tareas", "Calendario", "Diario", "Hábitos", "Metas")) InputField(date, { date = it }, "Fecha (AAAA-MM-DD)")
            if (section == "Diario" && initial == null && title == "Mi diario de hoy") {
                Text("Estado de ánimo", color = c[2]); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("😊", "🙂", "😐", "😔", "😴").forEach { emoji -> FilterChip(selected = body.contains(emoji), onClick = { body = if (body.contains(emoji)) body else "$emoji  " + body }, label = { Text(emoji) }) } }
                Text("Autocuidado", color = c[2]); Row { listOf("Skincare mañana", "Skincare noche").forEach { label -> FilterChip(selected = body.contains("☑ $label"), onClick = { body += if (body.contains("☑ $label")) "\n☐ $label" else "\n☑ $label" }, label = { Text(label) }) } }
                Text("Reflexión del día", color = c[2])
            }
            if (section == "Diario" && title == "Skincare mañana" || section == "Diario" && title == "Skincare noche") {
                InputField(body, { body = it }, "Pasos o productos de tu rutina", 3)
            } else if (title != "Agua") InputField(body, { body = it }, if (section == "Notas") "Escribe tu nota o reflexión…" else "Detalles…", 5)
            if (section in listOf("Notas", "Cuadernos", "Diario")) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = { ink = true }) { Icon(Icons.Default.Draw, null); Text(" Escribir a mano") }; if (strokes.isNotEmpty()) Text("Dibujo guardado", color = c[2], fontSize = 12.sp) } }
            if (section in listOf("Tareas", "Hábitos") || title.startsWith("Skincare")) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(done, { done = it }); Text("Completado") }
        }
    }, confirmButton = { Button(onClick = { if (title.isNotBlank() && (date.isBlank() || runCatching { LocalDate.parse(date) }.isSuccess)) onSave((initial ?: Entry(section = section, title = title)).copy(section = section, title = title.trim(), body = if (strokes.isNotEmpty()) inkToJson(strokes) + if (body.isNotBlank()) "\n" + body else "" else body, date = date.trim(), done = done, updatedAt = System.currentTimeMillis())) }, colors = ButtonDefaults.buttonColors(containerColor = c[2])) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    if (ink) InkDialog(c, strokes, { strokes = it }, { ink = false })
}

@Composable
private fun InkDialog(c: List<Color>, initial: List<List<Offset>>, onDone: (List<List<Offset>>) -> Unit, close: () -> Unit) {
    var strokes by remember { mutableStateOf(initial) }
    var active by remember { mutableStateOf(listOf<Offset>()) }
    var redo by remember { mutableStateOf(listOf<List<Offset>>()) }
    var finger by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = c[0]) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("Página manuscrita", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c[2])
                Text("Escribe con lápiz o dedo. Pulsa Listo y después Guardar en la página.", fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (strokes.isNotEmpty()) { redo = redo + listOf(strokes.last()); strokes = strokes.dropLast(1) } }, enabled = strokes.isNotEmpty()) { Text("Deshacer") }
                    TextButton(onClick = { if (redo.isNotEmpty()) { strokes = strokes + listOf(redo.last()); redo = redo.dropLast(1) } }, enabled = redo.isNotEmpty()) { Text("Rehacer") }
                    FilterChip(selected = !finger, onClick = { finger = !finger }, label = { Text("Solo lápiz") })
                    Button(onClick = { onDone(strokes); close() }) { Text("Listo") }
                    TextButton(onClick = close) { Text("Cancelar") }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> InkSurface(ctx) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    update = { view ->
                        view.lines = strokes
                        view.allowFinger = finger
                        view.onStroke = { line -> strokes = strokes + listOf(line); redo = emptyList() }
                        view.invalidate()
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(name: String, user: String, theme: Int, goal: Int, c: List<Color>, onSave: (String, String, Int, Int) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var n by remember(name) { mutableStateOf(name) }; var u by remember(user) { mutableStateOf(user) }
    var t by remember(theme) { mutableIntStateOf(theme) }; var g by remember(goal) { mutableStateOf(goal.toString()) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Tu espacio personal", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = c[2]) }
        item { InputField(n, { n = it }, "Nombre de la agenda") }
        item { InputField(u, { u = it }, "Tu nombre (opcional)") }
        item { Text("Tema", fontWeight = FontWeight.SemiBold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { palettes.forEachIndexed { i, palette -> Surface(onClick = { t = i }, shape = CircleShape, color = palette[1], modifier = Modifier.size(42.dp), border = if (t == i) androidx.compose.foundation.BorderStroke(3.dp, palette[2]) else null) {} } } }
        item { InputField(g, { g = it.filter(Char::isDigit).take(2) }, "Meta de vasos de agua al día", 1, KeyboardType.Number) }
        item { Button(onClick = { onSave(n.ifBlank { "Mi Agenda" }.take(60), u.take(60), t, (g.toIntOrNull() ?: 8).coerceIn(1, 30)) }, colors = ButtonDefaults.buttonColors(containerColor = c[2])) { Text("Guardar preferencias") } }
        item { HorizontalDivider() }
        item { Text("Copias de seguridad", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c[2]) }
        item { Text("La copia incluye tus registros y nombres. Guárdala en un lugar privado. Al importar, reemplazará los registros actuales.", color = Color.Gray) }
        item { OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Text("  Exportar copia JSON") } }
        item { OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Text("  Importar copia JSON") } }
        item { Text("Privacidad", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c[2]) }
        item { Text("Tus datos se guardan en este dispositivo. La aplicación no requiere cuenta, conexión a internet ni permisos de red.", color = Color.Gray) }
        item { Text("Agenda Lavanda · versión 1.1.0", color = Color.Gray, fontSize = 12.sp) }
    }
}

@Composable
private fun ProfileDialog(name: String, user: String, c: List<Color>, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }; var u by remember { mutableStateOf(user) }
    AlertDialog(onDismissRequest = {}, title = { Text("Crea tu espacio ✨") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Elige cómo quieres llamar a tu agenda. Podrás cambiarlo después."); InputField(n, { n = it }, "Nombre de tu agenda"); InputField(u, { u = it }, "Tu nombre (opcional)") } }, confirmButton = { Button(onClick = { onSave(n.ifBlank { "Mi Agenda" }.take(60), u.take(60)) }, colors = ButtonDefaults.buttonColors(containerColor = c[2])) { Text("Empezar") } })
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(value, onChange, Modifier.fillMaxWidth().padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF332F38), fontSize = 15.sp), minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboard), decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = Color.Gray); inner() } })
    }
}

@Composable
private fun EmptyCard(message: String, c: List<Color>, add: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = c[3]), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(message, color = Color(0xFF6F6874)); TextButton(onClick = add) { Text("+ Agregar", color = c[2]) } } }
}
@Composable private fun SectionHeader(title: String, action: String, onClick: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 18.sp); TextButton(onClick = onClick) { Text(action) } } }
private fun emptyMessage(s: String) = when(s) { "Notas" -> "Todavía no hay notas. Escribe o dibuja una idea."; "Diario" -> "Tu espacio de reflexión y autocuidado empieza aquí."; "Hábitos" -> "Agrega hábitos sencillos para seguir tu rutina."; "Cuadernos" -> "Crea un cuaderno para reunir tus ideas."; "Metas" -> "Define una meta y avanza poco a poco."; else -> "Aquí aparecerán tus elementos." }
private fun defaultTitle(s: String) = when(s) { "Tareas" -> "Nueva tarea"; "Calendario" -> "Nuevo evento"; "Notas" -> "Nueva nota"; "Cuadernos" -> "Nuevo cuaderno"; "Diario" -> "Mi diario de hoy"; "Hábitos" -> "Nuevo hábito"; "Metas" -> "Nueva meta"; else -> "Nuevo elemento" }
private fun greeting() = when (java.time.LocalTime.now().hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
private fun todayLabel() = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM"))
private fun inkToJson(strokes: List<List<Offset>>): String {
    val data = JSONArray()
    strokes.forEach { line ->
        val points = JSONArray()
        line.forEach { points.put(JSONArray().put(it.x).put(it.y)) }
        data.put(points)
    }
    return "[MANUSCRITO]$data"
}
private fun parseStrokes(body: String): List<List<Offset>> = runCatching { val raw = body.substringAfter("[MANUSCRITO]", "").substringBefore("\n"); val a = JSONArray(raw); (0 until a.length()).map { i -> val row = a.getJSONArray(i); (0 until row.length()).map { j -> val p = row.getJSONArray(j); Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) } } }.getOrDefault(emptyList())

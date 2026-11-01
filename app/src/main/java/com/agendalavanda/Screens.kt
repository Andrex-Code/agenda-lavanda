package com.agendalavanda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.agendalavanda.data.*
import com.agendalavanda.ink.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish=Locale.forLanguageTag("es")
private fun dateLabel(day:LocalDate)=day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",spanish))
private fun note(e:Entry)=runCatching{InkDocument.decode(e.body)}.getOrDefault(InkDocument(text="No se puede mostrar la vista previa."))
@Composable private fun Empty(text:String) { Card(Modifier.fillMaxWidth()){Text(text,Modifier.padding(24.dp))} }
@Composable fun EntryCard(entry:Entry,onOpen:()->Unit,onToggle:(()->Unit)?=null,onTrash:(()->Unit)?=null,extra:String="") {
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onToggle!=null) Checkbox(entry.done,{onToggle()})
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(entry.title.ifBlank{"Sin título"},style=MaterialTheme.typography.titleMedium)
                if(extra.isNotBlank())Text(extra,style=MaterialTheme.typography.labelMedium)
                if(entry.date.isNotBlank())Text(entry.date+" "+entry.time,style=MaterialTheme.typography.labelMedium)
                val doc=remember(entry.body){note(entry)}
                if(doc.text.isNotBlank())Text(doc.text.take(200),maxLines=3,style=MaterialTheme.typography.bodyMedium)
                if(doc.strokes.isNotEmpty()) {
                    AndroidView(factory={PagePreview(it)},modifier=Modifier.fillMaxWidth().height(110.dp),
                        update={it.page=doc;it.invalidate()})
                    Text("✎ ${doc.strokes.size} trazos · toca para editar",style=MaterialTheme.typography.labelSmall)
                }
                if(entry.section=="Metas") { LinearProgressIndicator(progress={entry.progress/100f},modifier=Modifier.fillMaxWidth());Text("${entry.progress}% completado") }
            }
            if(onTrash!=null) IconButton(onClick=onTrash){Icon(Icons.Default.DeleteOutline,"Mover a Papelera")}
        }
    }
}
@Composable fun HomeScreen(all:List<Entry>,user:String,today:LocalDate,goal:Int,draftCount:Int,navigate:(String)->Unit,open:(Entry)->Unit) {
    val pending=all.count{it.section=="Tareas"&&!it.done}
    val water=all.firstOrNull{it.section=="Diario"&&it.title=="Agua"&&it.date==today.toString()}?.body?.toIntOrNull() ?: 0
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(if(user.isBlank())"Un nuevo día para ti" else "Hola, $user",style=MaterialTheme.typography.headlineMedium)
                Text(dateLabel(today))
                Text("Organiza lo importante. Deja espacio para ti.")
            }
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Card(onClick={navigate("Tareas")},modifier=Modifier.weight(1f)){Column(Modifier.padding(18.dp)){Text("$pending",style=MaterialTheme.typography.headlineMedium);Text("Tareas pendientes")}}
            Card(onClick={navigate("Diario")},modifier=Modifier.weight(1f)){Column(Modifier.padding(18.dp)){Text("$water / $goal",style=MaterialTheme.typography.headlineMedium);Text("Vasos de agua")}}
        } }
        if(draftCount>0)item{OutlinedButton(onClick={navigate("Borradores")},modifier=Modifier.fillMaxWidth()){Text("Continuar $draftCount borrador(es)")}}
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Notas","Cuadernos","Hábitos","Metas").forEach{s->FilledTonalButton(onClick={navigate(s)}){Text(s)}}
        } }
        item{Text("Tu día",style=MaterialTheme.typography.titleLarge)}
        val agenda=all.filter{it.section in listOf("Tareas","Calendario")&&it.occursOn(today)&&!it.done}.sortedBy{it.time}
        if(agenda.isEmpty())item{Empty("Sin pendientes con fecha para hoy. Puedes crear uno en Calendario.")}
        items(agenda,key={it.id}){e->EntryCard(e,{open(e)})}
        item{Text("Últimas páginas",style=MaterialTheme.typography.titleLarge)}
        items(all.filter{it.section=="Notas"}.take(3),key={"note-${it.id}"}){e->EntryCard(e,{open(e)})}
    }
}
@Composable fun CalendarScreen(all:List<Entry>,today:LocalDate,open:(Entry)->Unit,add:(LocalDate)->Unit) {
    var selected by rememberSaveable{mutableStateOf(today.toString())}
    var mode by rememberSaveable{mutableStateOf("Mes")}
    val day=LocalDate.parse(selected);val month=YearMonth.from(day)
    val events=all.filter{it.section in listOf("Calendario","Tareas")}
    fun shift(delta:Long){selected=when(mode){"Mes"->day.plusMonths(delta);"Semana"->day.plusWeeks(delta);else->day.plusDays(delta)}.toString()}
    LazyColumn(Modifier.testTag("calendarList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={shift(-1)}){Icon(Icons.Default.ChevronLeft,"Anterior")}
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",spanish)),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={shift(1)}){Icon(Icons.Default.ChevronRight,"Siguiente")}
            TextButton(onClick={selected=today.toString()}){Text("Hoy")}
        }}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Mes","Semana","Día").forEach{m->FilterChip(selected=mode==m,onClick={mode=m},label={Text(m)})}}}
        if(mode!="Día") item {
            val first=if(mode=="Mes") month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong()) else day.minusDays((day.dayOfWeek.value-1).toLong())
            val count=if(mode=="Mes") ((month.lengthOfMonth()+month.atDay(1).dayOfWeek.value-2)/7+1)*7 else 7
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                Row {listOf("L","M","X","J","V","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
                (0 until count).chunked(7).forEach { week ->
                    Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        week.forEach { offset ->
                            val cell=first.plusDays(offset.toLong());val n=events.count{it.occursOn(cell)}
                            Surface(onClick={selected=cell.toString()},modifier=Modifier.weight(1f).heightIn(min=56.dp),
                                color=if(cell==day)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape=MaterialTheme.shapes.small) {
                                Column(Modifier.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                    Text(cell.dayOfMonth.toString(),color=if(cell.month==month.month)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                                    Text(if(cell==today)"Hoy" else if(n>0)"• $n" else "",style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item{Text(dateLabel(day),style=MaterialTheme.typography.titleMedium)}
        item{Button(onClick={add(day)},modifier=Modifier.fillMaxWidth()){Text("+ Evento para este día")}}
        val selectedEvents=events.filter{it.occursOn(day)}.sortedWith(compareBy<Entry>{it.time.ifBlank{"99:99"}}.thenBy{it.title})
        if(selectedEvents.isEmpty())item{Empty("Este día está libre.")}
        items(selectedEvents,key={it.id}){e->EntryCard(e,{open(e)},extra=if(e.repeatRule.isNotBlank())"Evento recurrente" else e.section)}
    }
}
@Composable fun EntryListScreen(section:String,entries:List<Entry>,add:()->Unit,open:(Entry)->Unit,toggle:(Entry)->Unit,trash:(Entry)->Unit) {
    var query by rememberSaveable(section){mutableStateOf("")}
    var filter by rememberSaveable(section){mutableStateOf("Todos")}
    var sort by rememberSaveable(section){mutableStateOf("Recientes")}
    val shown=entries.filter{query.isBlank()||it.title.contains(query,true)||note(it).text.contains(query,true)}
        .filter{filter=="Todos" || (if(section=="Metas")it.progress==100 else it.done)==(filter=="Completados")}
        .let { list -> when(sort){"Título"->list.sortedBy{it.title.lowercase()};"Fecha"->list.sortedBy{it.date.ifBlank{"9999"}};else->list.sortedByDescending{it.updatedAt}} }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth()){Text(if(section=="Notas")"✎  Nueva página · texto y lápiz" else "+ Agregar ${section.lowercase()}")}}
        item{OutlinedTextField(query,{query=it},label={Text("Buscar")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            if(section in listOf("Tareas","Metas"))listOf("Todos","Pendientes","Completados").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)})}
            listOf("Recientes","Título","Fecha").forEach{s->FilterChip(selected=sort==s,onClick={sort=s},label={Text(s)})}
        }}
        if(shown.isEmpty())item{Empty(if(query.isBlank())"Tu espacio está listo. Agrega el primer elemento." else "No hay resultados para esta búsqueda.")}
        items(shown,key={it.id}){e->EntryCard(e,{open(e)},if(section=="Tareas")({toggle(e)}) else null,{trash(e)})}
    }
}
@Composable fun NotebooksScreen(all:List<Entry>,open:(Entry)->Unit,addPage:(Long)->Unit,addBook:()->Unit,trash:(Entry)->Unit) {
    var bookId by rememberSaveable{mutableStateOf<Long?>(null)}
    val books=all.filter{it.section=="Cuadernos"};val book=books.find{it.id==bookId}
    BackHandler(enabled=book!=null){bookId=null}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(book==null) {
            item{Button(onClick=addBook,modifier=Modifier.fillMaxWidth()){Text("+ Crear cuaderno")}}
            if(books.isEmpty())item{Empty("Crea un cuaderno para agrupar tus páginas. También puedes mover una nota a un cuaderno desde su editor.")}
            items(books,key={it.id}){b->
                Card(onClick={bookId=b.id},modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(20.dp)){
                        Text("▤  ${b.title}",style=MaterialTheme.typography.headlineSmall)
                        Text("${all.count{it.notebookId==b.id}} páginas")
                        Row{TextButton(onClick={open(b)}){Text("Editar portada")};TextButton(onClick={trash(b)}){Text("Papelera")}}
                    }
                }
            }
        } else {
            item{TextButton(onClick={bookId=null}){Text("‹ Todos los cuadernos")}}
            item{Text(book.title,style=MaterialTheme.typography.headlineMedium)}
            item{Button(onClick={addPage(book.id)},modifier=Modifier.fillMaxWidth()){Text("+ Nueva página en este cuaderno")}}
            val pages=all.filter{it.notebookId==book.id}
            if(pages.isEmpty())item{Empty("Aquí aparecerán las páginas del cuaderno.")}
            items(pages,key={it.id}){e->EntryCard(e,{open(e)},onTrash={trash(e)})}
        }
    }
}
@Composable fun DiaryScreen(entries:List<Entry>,today:LocalDate,goal:Int,open:(Entry)->Unit,water:(LocalDate,Int)->Unit,routine:(LocalDate,String)->Unit,trash:(Entry)->Unit) {
    var selected by rememberSaveable{mutableStateOf(today.toString())};val day=LocalDate.parse(selected)
    val daily=entries.filter{it.date==selected};val count=daily.firstOrNull{it.title=="Agua"}?.body?.toIntOrNull() ?: 0
    LazyColumn(Modifier.testTag("diaryList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={selected=day.minusDays(1).toString()}){Icon(Icons.Default.ChevronLeft,"Día anterior")}
            Text(selected,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={selected=day.plusDays(1).toString()}){Icon(Icons.Default.ChevronRight,"Día siguiente")}
            TextButton(onClick={selected=today.toString()}){Text("Hoy")}
        }}
        item{Card{Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Tu autocuidado",style=MaterialTheme.typography.titleLarge)
            listOf("Skincare mañana","Skincare noche").forEach{label->Row(Modifier.fillMaxWidth().testTag("routine-$label").toggleable(value=daily.any{it.title==label&&it.done},role=Role.Checkbox,onValueChange={routine(day,label)}),verticalAlignment=Alignment.CenterVertically){
                Checkbox(daily.any{it.title==label&&it.done},null);Text(label)
            }}
            Text("Agua: $count / $goal vasos")
            LinearProgressIndicator(progress={(count.toFloat()/goal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedButton(enabled=count>0,onClick={water(day,-1)}){Text("− Un vaso")}
                Button(onClick={water(day,1)}){Text("+ Un vaso")}
            }
        }}}
        item{Button(onClick={open(Entry(section="Diario",title="Mi diario",date=selected))},modifier=Modifier.fillMaxWidth()){Text("✎  Escribir mi reflexión")}}
        items(daily.filter{it.title !in listOf("Agua","Skincare mañana","Skincare noche")},key={it.id}){e->EntryCard(e,{open(e)},onTrash={trash(e)})}
    }
}
@Composable fun HabitsScreen(entries:List<Entry>,today:LocalDate,add:()->Unit,open:(Entry)->Unit,toggle:(Entry)->Unit,trash:(Entry)->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth()){Text("+ Nuevo hábito")}}
        item{Text("Marca lo que hiciste hoy. El historial se conserva cada día.",style=MaterialTheme.typography.bodyMedium)}
        if(entries.isEmpty())item{Empty("Empieza con algo pequeño: leer, caminar, descansar…")}
        items(entries,key={it.id}){e->
            Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(Modifier.fillMaxWidth().toggleable(value=e.completedOn(today),role=Role.Checkbox,onValueChange={toggle(e)}),verticalAlignment=Alignment.CenterVertically){Checkbox(e.completedOn(today),null);Text(e.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)}
                Text("Racha actual: ${e.streak(today)} días")
                Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
                    (6 downTo 0).forEach{n->val d=today.minusDays(n.toLong());Column(horizontalAlignment=Alignment.CenterHorizontally){Text(d.dayOfMonth.toString(),style=MaterialTheme.typography.labelSmall);Text(if(e.completedOn(d))"●" else "○",color=MaterialTheme.colorScheme.primary)}}
                }
                Row{TextButton(onClick={open(e)}){Text("Editar")};TextButton(onClick={trash(e)}){Text("Papelera")}}
            }}
        }
    }
}
@Composable fun DraftsScreen(drafts:List<Draft>,open:(Draft)->Unit,delete:(Draft)->Unit) {
    var pending by remember{mutableStateOf<Draft?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Borradores recuperables",style=MaterialTheme.typography.headlineSmall)}
        item{Text("Los cambios del editor se guardan aquí automáticamente. Abre uno para continuar y pulsa Guardar para finalizar.")}
        if(drafts.isEmpty())item{Empty("No tienes borradores pendientes.")}
        items(drafts,key={it.key}){d->
            val e=runCatching{EntryCodec.read(JSONObject(d.payload),validate=false)}.getOrNull()
            Card(onClick={open(d)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
                Text(e?.title?.ifBlank{"Página sin título"} ?: "Borrador",style=MaterialTheme.typography.titleMedium)
                Text(e?.section.orEmpty());TextButton(onClick={pending=d}){Text("Descartar borrador")}
            }}
        }
    }
    pending?.let{d->AlertDialog(onDismissRequest={pending=null},title={Text("¿Descartar este borrador?")},confirmButton={TextButton(onClick={delete(d);pending=null}){Text("Descartar")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancelar")}})}
}
@Composable fun TrashScreen(entries:List<Entry>,restore:(Entry)->Unit,delete:(Entry)->Unit) {
    var pending by remember{mutableStateOf<Entry?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Papelera",style=MaterialTheme.typography.headlineSmall)}
        item{Text("Los elementos permanecen aquí hasta que los restaures o elimines definitivamente. Restaurar un cuaderno también recupera sus páginas.")}
        if(entries.isEmpty())item{Empty("La papelera está vacía.")}
        items(entries,key={it.id}){e->Card{Column(Modifier.fillMaxWidth().padding(16.dp)){
            Text(e.title,style=MaterialTheme.typography.titleMedium);Text(e.section)
            Row{TextButton(onClick={restore(e)}){Text("Restaurar")};TextButton(onClick={pending=e}){Text("Eliminar definitivamente")}}
        }}}
    }
    pending?.let{e->AlertDialog(onDismissRequest={pending=null},title={Text("¿Eliminar definitivamente?")},text={Text("No se podrá deshacer. Si es un cuaderno, también se eliminan sus páginas.")},
        confirmButton={TextButton(onClick={delete(e);pending=null}){Text("Eliminar")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancelar")}})}
}
@Composable fun SettingsScreen(name:String,user:String,theme:Int,water:Int,lastBackup:String,hasSafety:Boolean,
    save:(String,String,Int,Int)->Unit,export:()->Unit,import:()->Unit,restoreSafety:()->Unit) {
    var n by remember(name){mutableStateOf(name)};var u by remember(user){mutableStateOf(user)}
    var t by remember(theme){mutableIntStateOf(theme)};var w by remember(water){mutableStateOf(water.toString())}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Hazla tuya",style=MaterialTheme.typography.headlineMedium)}
        item{OutlinedTextField(n,{n=it.take(60)},label={Text("Nombre de la agenda")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(u,{u=it.take(60)},label={Text("Tu nombre")},modifier=Modifier.fillMaxWidth())}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Lavanda","Rosa","Salvia","Durazno").forEachIndexed{i,label->FilterChip(selected=t==i,onClick={t=i},label={Text(label)})}
        }}
        item{OutlinedTextField(w,{w=it.filter(Char::isDigit).take(2)},label={Text("Meta diaria de vasos de agua (1–30)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Button(onClick={save(n,u,t,(w.toIntOrNull() ?: 8).coerceIn(1,30))}){Text("Guardar preferencias")}}
        item{HorizontalDivider()}
        item{Text("Cuida tus recuerdos",style=MaterialTheme.typography.titleLarge)}
        item{Text(if(lastBackup.isBlank())"Aún no has exportado una copia. Guarda una fuera de la tablet para protegerte si se pierde o se daña." else "Última copia exportada: $lastBackup")}
        item{OutlinedButton(onClick=export,modifier=Modifier.fillMaxWidth()){Text("Exportar copia completa")}}
        item{OutlinedButton(onClick=import,modifier=Modifier.fillMaxWidth()){Text("Importar copia")}}
        if(hasSafety)item{OutlinedButton(onClick=restoreSafety,modifier=Modifier.fillMaxWidth()){Text("Recuperar estado previo a última importación")}}
        item{Text("La copia JSON contiene tus páginas, cuadernos, hábitos, papelera, borradores y preferencias. No está cifrada; guárdala en un lugar privado.",style=MaterialTheme.typography.bodySmall)}
        item{HorizontalDivider()}
        item{Text("Cómo escribir con lápiz",style=MaterialTheme.typography.titleLarge)}
        item{Text("Abre Notas → Nueva página → Escribir con lápiz. Elige color, grosor y papel. Usa dos dedos para ampliar, Mover para desplazarte y Solo lápiz para ignorar el dedo. Al volver al editor, pulsa Guardar. Si cierras antes, encontrarás el borrador en Más → Borradores.")}
        item{Text("Tus datos permanecen en este dispositivo. No hay cuenta, servidor, publicidad ni analítica. La app funciona sin internet.")}
        item{Text("Agenda Lavanda 1.2.1 · Android",style=MaterialTheme.typography.labelMedium)}
    }
}


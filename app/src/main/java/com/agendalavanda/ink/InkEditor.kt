package com.agendalavanda.ink
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkEditor(document: InkDocument, onChange:(InkDocument)->Unit, close:()->Unit,status:String="",onRetry:()->Unit={}) {
    var undo by remember { mutableStateOf(listOf<InkDocument>()) }
    var redo by remember { mutableStateOf(listOf<InkDocument>()) }
    var mode by remember { mutableStateOf("pen") }
    var finger by remember { mutableStateOf(true) }
    var color by remember { mutableIntStateOf(0xff75539b.toInt()) }
    var width by remember { mutableFloatStateOf(4f) }
    var view by remember { mutableStateOf<InkCanvas?>(null) }
    var options by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    fun change(next:InkDocument) { undo=(undo+document).takeLast(40);redo=emptyList();onChange(next) }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Tu página",style=MaterialTheme.typography.titleLarge)
                    Button(onClick=close) { Text("Volver a la nota") }
                }
                Text(status,style=MaterialTheme.typography.bodySmall,color=if(status.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if(status.startsWith("Error")) TextButton(onClick=onRetry){Text("Reintentar guardado")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    IconToggleButton(checked=mode=="pen",onCheckedChange={mode="pen"}) { Icon(Icons.Default.Edit,"Lápiz") }
                    IconToggleButton(checked=mode=="erase",onCheckedChange={mode="erase"}) { Icon(Icons.Default.AutoFixNormal,"Borrador") }
                    IconToggleButton(checked=mode=="pan",onCheckedChange={mode="pan"}) { Icon(Icons.Default.PanTool,"Mover") }
                    IconButton(enabled=undo.isNotEmpty(),onClick={redo=redo+document;onChange(undo.last());undo=undo.dropLast(1)}) { Icon(Icons.AutoMirrored.Filled.Undo,"Deshacer") }
                    IconButton(enabled=redo.isNotEmpty(),onClick={undo=undo+document;onChange(redo.last());redo=redo.dropLast(1)}) { Icon(Icons.AutoMirrored.Filled.Redo,"Rehacer") }
                    IconButton(onClick={options=true}) { Icon(Icons.Default.Tune,"Color, grosor y papel") }
                }
                Text("${when(mode){"erase"->"Borrador";"pan"->"Mover";else->"Lápiz · ${width.toInt()} pt"}} · Pellizca para ampliar",style=MaterialTheme.typography.bodySmall)
                AndroidView(factory={ctx->InkCanvas(ctx).also { view=it }},modifier=Modifier.weight(1f).fillMaxWidth().testTag("inkCanvas"),
                    update={it.document=document;it.mode=mode;it.allowFinger=finger;it.penColor=color;it.penWidth=width;it.onChange={next->change(next)};it.invalidate()})
            }
        }
    }
    if(options) AlertDialog(onDismissRequest={options=false},title={Text("Opciones de la hoja")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Color de tinta")
            listOf(0xff75539b.toInt(),0xff272334.toInt(),0xff9a365c.toInt(),0xff286660.toInt(),0xff225b99.toInt()).forEachIndexed { i,c ->
                FilterChip(selected=color==c,onClick={color=c},label={Text(listOf("Lavanda","Tinta","Rosa","Verde","Azul")[i],color=Color(c))})
            }
            Text("Grosor")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf(2f,4f,7f,12f).forEach { n -> FilterChip(selected=width==n,onClick={width=n},label={Text("${n.toInt()} pt")}) } }
            Text("Papel")
            listOf("blank" to "Lisa","lines" to "Renglones","grid" to "Cuadros","dots" to "Puntos").forEach { (key,label) ->
                FilterChip(selected=document.paper==key,onClick={change(document.copy(paper=key))},label={Text(label)})
            }
            FilterChip(selected=!finger,onClick={finger=!finger},label={Text("Solo lápiz · ignorar dedo")})
            TextButton(onClick={view?.resetPage();options=false}) {Text("Ajustar hoja a la pantalla")}
            TextButton(onClick={options=false;clear=true}) {Text("Vaciar hoja")}
        }
    },confirmButton={TextButton(onClick={options=false}){Text("Listo")}})
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("¿Vaciar los trazos?")},text={Text("Puedes recuperarlos con Deshacer mientras el editor siga abierto.")},
        confirmButton={TextButton(onClick={change(document.copy(strokes=emptyList()));clear=false}){Text("Vaciar")}},
        dismissButton={TextButton(onClick={clear=false}){Text("Cancelar")}})
}


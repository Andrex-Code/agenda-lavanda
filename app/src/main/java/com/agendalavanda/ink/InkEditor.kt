package com.agendalavanda.ink
import androidx.compose.foundation.horizontalScroll
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

@Composable
fun InkEditor(document: InkDocument, onChange:(InkDocument)->Unit, close:()->Unit) {
    var undo by remember { mutableStateOf(listOf<InkDocument>()) }
    var redo by remember { mutableStateOf(listOf<InkDocument>()) }
    var mode by remember { mutableStateOf("pen") }
    var finger by remember { mutableStateOf(true) }
    var color by remember { mutableIntStateOf(0xff75539b.toInt()) }
    var width by remember { mutableFloatStateOf(3f) }
    var view by remember { mutableStateOf<InkCanvas?>(null) }
    var clear by remember { mutableStateOf(false) }
    fun change(next:InkDocument) { undo=(undo+document).takeLast(40);redo=emptyList();onChange(next) }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Tu página",style=MaterialTheme.typography.titleLarge)
                    Button(onClick=close) { Text("Volver a la nota") }
                }
                Text("Pellizca para ampliar. Mover permite recorrer la hoja.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("pen" to "Lápiz","erase" to "Borrador","pan" to "Mover").forEach { (key,label) ->
                        FilterChip(selected=mode==key,onClick={mode=key},label={Text(label)})
                    }
                    FilterChip(selected=!finger,onClick={finger=!finger},label={Text("Solo lápiz")})
                    TextButton(onClick={ view?.resetPage() }) { Text("Ajustar hoja") }
                    TextButton(enabled=undo.isNotEmpty(),onClick={redo=redo+document;onChange(undo.last());undo=undo.dropLast(1)}) { Text("Deshacer") }
                    TextButton(enabled=redo.isNotEmpty(),onClick={undo=undo+document;onChange(redo.last());redo=redo.dropLast(1)}) { Text("Rehacer") }
                    TextButton(onClick={clear=true}) { Text("Vaciar hoja") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(0xff75539b.toInt(),0xff272334.toInt(),0xffb94f77.toInt(),0xff377c77.toInt(),0xff387bc3.toInt()).forEachIndexed { i,c ->
                        FilterChip(selected=color==c,onClick={color=c},label={Text(listOf("Lavanda","Tinta","Rosa","Verde","Azul")[i],color=Color(c))})
                    }
                    listOf(2f,4f,7f,12f).forEach { n -> FilterChip(selected=width==n,onClick={width=n},label={Text("${n.toInt()} pt")}) }
                    listOf("blank" to "Lisa","lines" to "Renglones","grid" to "Cuadros","dots" to "Puntos").forEach { (key,label) ->
                        FilterChip(selected=document.paper==key,onClick={change(document.copy(paper=key))},label={Text(label)})
                    }
                }
                AndroidView(factory={ctx->InkCanvas(ctx).also { view=it }},modifier=Modifier.weight(1f).fillMaxWidth().testTag("inkCanvas"),
                    update={it.document=document;it.mode=mode;it.allowFinger=finger;it.penColor=color;it.penWidth=width;it.onChange={next->change(next)};it.invalidate()})
            }
        }
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("¿Vaciar los trazos?")},text={Text("Puedes recuperarlos con Deshacer mientras el editor siga abierto.")},
        confirmButton={TextButton(onClick={change(document.copy(strokes=emptyList()));clear=false}){Text("Vaciar")}},
        dismissButton={TextButton(onClick={clear=false}){Text("Cancelar")}})
}


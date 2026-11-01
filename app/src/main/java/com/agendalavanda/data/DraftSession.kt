package com.agendalavanda.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes draft writes and final save, so an older autosave cannot resurrect a saved draft. */
class DraftSession(private val dao:EntryDao,val key:String,private val writeDraft:suspend (Draft)->Unit = {dao.putDraft(it)},private val autoCommit:Boolean=false) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private var pending:Job?=null
    @Volatile private var latest:Entry?=null
    private var closed=false
    @Volatile private var savedId:Long?=null
    val hasSavedDocument get()=savedId!=null
    private var activeKey=key
    val status=MutableStateFlow(if(autoCommit) "Los cambios se guardan automáticamente" else "Borrador listo")
    fun update(entry:Entry) {
        latest=entry;status.value=if(autoCommit) "Guardando…" else "Guardando borrador…";pending?.cancel()
        pending=scope.launch { delay(400);write() }
    }
    private suspend fun write(failOnError:Boolean=false)=lock.withLock {
        if(!closed) latest?.let { entry ->
            try {
                withContext(NonCancellable) {
                    writeDraft(Draft(activeKey,EntryCodec.json(entry.copy(id=savedId ?: entry.id)).toString()))
                    if(autoCommit) {
                        savedId=dao.save(entry.copy(id=savedId ?: entry.id,updatedAt=System.currentTimeMillis()),activeKey)
                        activeKey="entry:$savedId"
                    }
                }
                status.value=if(autoCommit) "Guardado en este dispositivo" else "Borrador guardado en este dispositivo"
            }
            catch(e:Exception) { if(e is CancellationException) throw e;status.value=if(autoCommit) "Error al guardar · reintenta con Guardar" else "No se pudo guardar el borrador. Intenta Guardar.";if(failOnError) throw e }
        }
    }
    suspend fun flush() { pending?.cancel();write(true) }
    suspend fun save(entry:Entry):Long {
        pending?.cancel()
        return lock.withLock { withContext(NonCancellable) { val id=dao.save(entry.copy(id=savedId ?: entry.id),activeKey);savedId=id;closed=true;status.value="Guardado";id } }
    }
    suspend fun discard() { pending?.cancel();lock.withLock {dao.deleteDraft(activeKey);closed=true} }
    fun finish() { pending?.cancel();scope.launch { write();scope.cancel() } }
}


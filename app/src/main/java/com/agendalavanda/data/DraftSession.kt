package com.agendalavanda.data
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes draft writes and final save, so an older autosave cannot resurrect a saved draft. */
class DraftSession(private val dao:EntryDao,val key:String) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private var pending:Job?=null
    @Volatile private var latest:Entry?=null
    private var closed=false
    val status=MutableStateFlow("Borrador listo")
    fun update(entry:Entry) {
        latest=entry;status.value="Guardando borrador…";pending?.cancel()
        pending=scope.launch { delay(400);write() }
    }
    private suspend fun write()=lock.withLock {
        if(!closed) latest?.let { entry ->
            try { dao.putDraft(Draft(key,EntryCodec.json(entry).toString()));status.value="Borrador guardado en este dispositivo" }
            catch(e:Exception) { if(e is CancellationException) throw e;status.value="No se pudo guardar el borrador. Intenta Guardar." }
        }
    }
    suspend fun flush() { pending?.cancel();write() }
    suspend fun save(entry:Entry):Long {
        pending?.cancel()
        return lock.withLock { val id=dao.save(entry,key);closed=true;status.value="Guardado";id }
    }
    suspend fun discard() { pending?.cancel();lock.withLock {dao.deleteDraft(key);closed=true} }
    fun finish() { pending?.cancel();scope.launch { write();scope.cancel() } }
}


package com.example.focuswear

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.focuswear.routine.CONFIG_ACK_PREFIX
import com.example.focuswear.routine.CONFIG_PATH
import com.example.focuswear.routine.ConfigRequest
import com.example.focuswear.routine.DataSync
import com.example.focuswear.routine.FocusDataListener
import com.example.focuswear.routine.IncomingItem
import com.example.focuswear.routine.RESULT_PREFIX
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.Executors

internal data class PhoneState(
    val saved: RoutineConfig?,
    val request: ConfigRequest?,
    val received: Boolean,
    val results: List<SessionResult> = emptyList(),
    val storageError: Boolean = false
)

internal class PhoneRoutine private constructor(context: Context) {
    private val sync = DataSync.from(context)
    private val prefs = sync.preferences
    private val history = ResultHistory(context)
    private val disk = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(loadState(context))
    val state = mutableState.asStateFlow()

    init {
        disk.execute {
            withHistory {
                val results = history.list()
                main.post { mutableState.value = state.value.copy(results = results) }
            }
        }
    }

    fun open() {
        sync.read(CONFIG_ACK_PREFIX, ::receive)
        sync.read(RESULT_PREFIX, ::receive)
        sync.kick()
    }

    fun save(config: RoutineConfig) {
        if (!config.isValid) return
        if (prefs.edit().putString("saved_config", config.encode()).commit()) {
            mutableState.value = state.value.copy(saved = config, storageError = false)
        } else {
            mutableState.value = state.value.copy(storageError = true)
        }
    }

    fun send() {
        val current = state.value
        val config = current.saved?.takeIf { it.isValid } ?: return
        val request = current.request?.takeIf { it.config == config && !current.received }
            ?: ConfigRequest(
                UUID.randomUUID().toString(),
                maxOf(System.currentTimeMillis(), (current.request?.revision ?: 0) + 1),
                config
            )
        val payload = request.encode()
        val edit = prefs.edit().putString("sent_config", payload).remove("confirmed_config")
        sync.queuePut(edit, CONFIG_PATH, payload)
        if (edit.commit()) {
            mutableState.value = current.copy(request = request, received = false, storageError = false)
            sync.kick()
        } else {
            mutableState.value = current.copy(storageError = true)
        }
    }

    fun receive(item: IncomingItem) {
        val path = item.uri.path ?: return
        if (path.startsWith(CONFIG_ACK_PREFIX)) {
            val request = ConfigRequest.decode(item.payload) ?: return
            val current = state.value
            if (request == current.request && !current.received) {
                if (prefs.edit().putString("confirmed_config", request.id).commit()) {
                    mutableState.value = current.copy(received = true)
                } else {
                    mutableState.value = current.copy(storageError = true)
                }
            }
        } else if (path.startsWith(RESULT_PREFIX)) {
            val result = SessionResult.decode(item.payload) ?: return
            if (path != RESULT_PREFIX + result.id) return
            disk.execute {
                withHistory {
                    val inserted = history.save(result)
                    main.post {
                        if (inserted) {
                            val results = (state.value.results + result).sortedByDescending { it.startedAt }
                            mutableState.value = state.value.copy(results = results)
                        }
                        // Delete the network item only after the local transaction commits.
                        sync.consume(item.uri)
                    }
                }
            }
        }
    }

    private fun withHistory(operation: () -> Unit) {
        try {
            operation()
        } catch (exception: android.database.SQLException) {
            Log.e("FocusWearHistory", "Could not access session history", exception)
            main.post { mutableState.value = state.value.copy(storageError = true) }
        }
    }

    private fun loadState(context: Context): PhoneState {
        val request = ConfigRequest.decode(prefs.getString("sent_config", null))
        val legacy = context.getSharedPreferences("focuswear_configuracion", Context.MODE_PRIVATE)
        val saved = RoutineConfig.decode(prefs.getString("saved_config", null))
            ?: if (legacy.contains("estudio")) {
                RoutineConfig(legacy.getInt("estudio", 25), legacy.getInt("descanso", 5),
                    legacy.getInt("sets", 4)).takeIf { it.isValid }
            } else null
        return PhoneState(saved, request, request != null && prefs.getString("confirmed_config", null) == request.id)
    }

    companion object {
        private var instance: PhoneRoutine? = null

        fun from(context: Context): PhoneRoutine =
            instance ?: PhoneRoutine(context.applicationContext).also { instance = it }
    }
}

internal class ResultHistory(context: Context, name: String = "focuswear_results.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE results (id TEXT PRIMARY KEY NOT NULL, started_at INTEGER NOT NULL, payload TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(result: SessionResult): Boolean {
        require(result.isValid)
        val values = ContentValues().apply {
            put("id", result.id)
            put("started_at", result.startedAt)
            put("payload", result.encode())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val exists = db.query("results", arrayOf("id"), "id = ?", arrayOf(result.id),
                null, null, null).use { it.moveToFirst() }
            if (!exists) db.insertOrThrow("results", null, values)
            db.setTransactionSuccessful()
            return !exists
        } finally {
            db.endTransaction()
        }
    }

    fun list(): List<SessionResult> = readableDatabase.query(
        "results", arrayOf("payload"), null, null, null, null, "started_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) SessionResult.decode(cursor.getString(0))?.let(::add)
        }
    }
}

class PhoneDataListener : FocusDataListener() {
    override fun receive(item: IncomingItem) {
        PhoneRoutine.from(this).receive(item)
    }
}

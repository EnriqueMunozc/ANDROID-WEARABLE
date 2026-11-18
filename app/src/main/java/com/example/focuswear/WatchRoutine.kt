package com.example.focuswear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.example.focuswear.routine.CONFIG_ACK_PREFIX
import com.example.focuswear.routine.CONFIG_PATH
import com.example.focuswear.routine.ConfigReceipt
import com.example.focuswear.routine.ConfigRequest
import com.example.focuswear.routine.DataSync
import com.example.focuswear.routine.FocusDataListener
import com.example.focuswear.routine.IncomingItem
import com.example.focuswear.routine.RESULT_PREFIX
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult
import com.example.focuswear.routine.receiptAfter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class WatchState(
    val nextConfig: RoutineConfig,
    val configReceived: Boolean,
    val session: Session?,
    val lastResult: SessionResult?,
    val recoveredAfterRestart: Boolean
) {
    fun acceptConfig(request: ConfigRequest) = copy(nextConfig = request.config, configReceived = true)
}

internal class WatchRoutine private constructor(private val context: Context) {
    private val sync = DataSync.from(context)
    private val prefs = sync.preferences
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val alarmIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, StageReceiver::class.java).setAction(STAGE_ALARM),
        PendingIntent.FLAG_IMMUTABLE
    )

    init {
        migrateLegacyRoutine()
    }

    private val mutableState = MutableStateFlow(
        WatchState(
            nextConfig = RoutineConfig.decode(prefs.getString("next_config", null)) ?: DEFAULT_CONFIG,
            configReceived = prefs.getBoolean("config_received", false),
            session = Session.decode(prefs.getString("session", null)),
            lastResult = SessionResult.decode(prefs.getString("last_result", null)),
            recoveredAfterRestart = prefs.getBoolean("reboot_pause", false)
        )
    )
    val state = mutableState.asStateFlow()

    val exactAlarmsAllowed get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun open() {
        reconcile(rearm = true)
        sync.read(CONFIG_PATH, ::receive)
        sync.kick()
    }

    fun receive(item: IncomingItem) {
        if (item.uri.path != CONFIG_PATH) return
        val sender = item.uri.host?.takeIf { NODE_ID.matches(it) } ?: return
        val request = ConfigRequest.decode(item.payload) ?: return
        val key = "received:$sender"
        val previous = ConfigRequest.decode(prefs.getString(key, null))
        val receipt = request.receiptAfter(previous)
        if (receipt == ConfigReceipt.STALE) return
        val edit = prefs.edit()
        if (receipt == ConfigReceipt.NEW) {
            edit.putString(key, request.encode())
                .putString("next_config", request.config.encode())
                .putBoolean("config_received", true)
        }
        sync.queuePut(edit, CONFIG_ACK_PREFIX + sender, request.encode())
        if (edit.commit()) {
            if (receipt == ConfigReceipt.NEW) {
                mutableState.value = state.value.acceptConfig(request)
            }
            sync.kick()
        }
    }

    fun start() {
        val now = SystemClock.elapsedRealtime()
        if (reconcile(now) != null) return
        val config = state.value.nextConfig
        persist(Session(UUID.randomUUID().toString(), System.currentTimeMillis(), config, boot,
            deadline = now + config.studyMillis))
    }

    fun togglePause(id: String) {
        val now = SystemClock.elapsedRealtime()
        val session = reconcile(now)?.takeIf { it.id == id } ?: return
        persist(session.togglePause(now))
    }

    fun distract(id: String) {
        val now = SystemClock.elapsedRealtime()
        val session = reconcile(now)?.takeIf { it.id == id } ?: return
        val updated = session.distract(now)
        if (updated != session) persist(updated, rearm = false)
    }

    fun finish(id: String) {
        val now = SystemClock.elapsedRealtime()
        val session = reconcile(now)?.takeIf { it.id == id } ?: return
        persist(null, session.result(now))
    }

    fun checkpoint() {
        val now = SystemClock.elapsedRealtime()
        val session = reconcile(now)?.takeUnless { it.paused } ?: return
        persist(session.checkpoint(now), rearm = false)
    }

    fun reconcile(
        now: Long = SystemClock.elapsedRealtime(),
        rearm: Boolean = false,
        alarmFired: Boolean = false
    ): Session? {
        val session = state.value.session ?: return null
        if (session.boot != boot) {
            persist(session.afterReboot(boot), rebootPause = !session.paused || state.value.recoveredAfterRestart)
            return state.value.session
        }
        val update = session.advance(now)
        if (update.boundary != null) {
            val updatedSession = update.session?.checkpoint(now)
            if (persist(updatedSession, update.result)) {
                // Recovery on open skips old alerts. A delayed alarm plays only the latest boundary.
                if (alarmFired || now - update.boundary <= 5_000L) vibrate(update.result != null)
            } else {
                schedule(now + 5_000L)
            }
        } else if (rearm) {
            schedule(session.deadline)
        }
        return state.value.session
    }

    private fun persist(
        session: Session?,
        result: SessionResult? = null,
        rearm: Boolean = true,
        rebootPause: Boolean = false
    ): Boolean {
        val edit = prefs.edit().putString("session", session?.encode())
            .putBoolean("reboot_pause", rebootPause)
        if (result != null) {
            val payload = result.encode()
            edit.putString("last_result", payload)
            sync.queuePut(edit, RESULT_PREFIX + result.id, payload)
        }
        if (!edit.commit()) {
            Log.e("FocusWearTimer", "Could not persist session")
            return false
        }
        mutableState.value = state.value.copy(
            session = session,
            lastResult = result ?: state.value.lastResult,
            recoveredAfterRestart = rebootPause
        )
        if (rearm) schedule(session?.deadline)
        if (result != null) sync.kick()
        return true
    }

    private fun schedule(deadline: Long?) {
        if (deadline == null) {
            alarms.cancel(alarmIntent)
        } else if (exactAlarmsAllowed) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent)
            }
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(complete: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else context.getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        val pattern = if (complete) longArrayOf(0, 350, 120, 350, 120, 450)
        else longArrayOf(0, 230, 120, 230)
        val effect = VibrationEffect.createWaveform(pattern, -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun migrateLegacyRoutine() {
        if (prefs.getBoolean("migrated_watch", false)) return
        val legacy = context.getSharedPreferences("focuswear", Context.MODE_PRIVATE)
        val config = RoutineConfig(
            legacy.getInt("estudio", 1), legacy.getInt("descanso", 1), legacy.getInt("sets", 3)
        ).takeIf { it.isValid } ?: DEFAULT_CONFIG
        val edit = prefs.edit().putBoolean("migrated_watch", true).putString("next_config", config.encode())
        if (legacy.getBoolean("activa", false)) {
            val rest = legacy.getString("etapa", null) == "Descanso" && config.sets > 1
            val set = legacy.getInt("setActual", 1).coerceIn(1, config.sets)
            val duration = if (rest) config.restMillis else config.studyMillis
            val now = System.currentTimeMillis()
            val remaining = if (legacy.getBoolean("pausada", false)) legacy.getLong("restantePausa", duration)
            else legacy.getLong("finEtapa", now) - now
            // The legacy timer did not store a session start date or a monotonic checkpoint.
            val session = Session(
                id = UUID.randomUUID().toString(), startedAt = now, config = config, boot = boot,
                setsCompleted = if (rest) set.coerceAtMost(config.sets - 1) else set - 1,
                phase = if (rest) Phase.REST else Phase.STUDY,
                deadline = null, checkpointRemaining = remaining.coerceIn(1, duration),
                distractions = legacy.getInt("distracciones", 0).coerceAtLeast(0)
            )
            edit.putString("session", session.encode()).putBoolean("reboot_pause", true)
        }
        edit.commit()
    }

    companion object {
        private val DEFAULT_CONFIG = RoutineConfig(1, 1, 3)
        private val NODE_ID = Regex("[A-Za-z0-9_-]{1,128}")
        private var instance: WatchRoutine? = null

        fun from(context: Context): WatchRoutine =
            instance ?: WatchRoutine(context.applicationContext).also { instance = it }
    }
}

private const val STAGE_ALARM = "com.example.focuswear.STAGE_ALARM"

class StageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WatchRoutine.from(context).reconcile(rearm = true, alarmFired = intent.action == STAGE_ALARM)
    }
}

class WatchDataListener : FocusDataListener() {
    override fun receive(item: IncomingItem) {
        WatchRoutine.from(this).receive(item)
    }
}

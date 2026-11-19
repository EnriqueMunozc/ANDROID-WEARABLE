package com.example.focuswear

import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult

internal enum class Phase { STUDY, REST }

internal data class Session(
    val id: String,
    val startedAt: Long,
    val config: RoutineConfig,
    val boot: Int,
    val setsCompleted: Int = 0,
    val phase: Phase = Phase.STUDY,
    val deadline: Long?,
    val checkpointRemaining: Long = config.studyMillis,
    val distractions: Int = 0
) {
    val paused get() = deadline == null
    private val phaseDuration get() = if (phase == Phase.STUDY) config.studyMillis else config.restMillis

    fun remaining(now: Long) = deadline?.let { (it - now).coerceIn(0, phaseDuration) }
        ?: checkpointRemaining

    fun checkpoint(now: Long) = copy(checkpointRemaining = remaining(now))

    // Al pausar conserva el tiempo restante; al reanudar calcula una nueva fecha límite.
    fun togglePause(now: Long) = if (paused) copy(deadline = now + checkpointRemaining)
    else checkpoint(now).copy(deadline = null)

    fun distract(now: Long) = if (phase == Phase.STUDY && !paused && distractions < Int.MAX_VALUE) {
        checkpoint(now).copy(distractions = distractions + 1)
    } else this

    fun afterReboot(bootCount: Int) = copy(boot = bootCount, deadline = null)

    fun result(now: Long, complete: Boolean = false): SessionResult {
        // Si se finaliza durante el estudio, suma solo el tiempo transcurrido de esa etapa.
        val partial = if (!complete && phase == Phase.STUDY) config.studyMillis - remaining(now)
        else 0L
        return SessionResult(
            id, startedAt, config, setsCompleted,
            setsCompleted * config.studyMillis + partial, distractions, complete
        )
    }

    // Procesa todos los cambios de etapa vencidos para que una alarma tardía no alargue la rutina.
    fun advance(now: Long): SessionUpdate {
        var session = this
        var lastBoundary: Long? = null
        while (true) {
            val end = session.deadline ?: break
            if (now < end) break
            lastBoundary = end
            if (session.phase == Phase.STUDY) {
                val completed = session.setsCompleted + 1
                if (completed == config.sets) {
                    // La rutina termina al completar el último bloque de estudio, sin otro descanso.
                    return SessionUpdate(null, session.copy(setsCompleted = completed)
                        .result(end, complete = true), end)
                }
                session = session.copy(
                    phase = Phase.REST,
                    setsCompleted = completed,
                    deadline = end + config.restMillis,
                    checkpointRemaining = config.restMillis
                )
            } else {
                session = session.copy(
                    phase = Phase.STUDY,
                    deadline = end + config.studyMillis,
                    checkpointRemaining = config.studyMillis
                )
            }
        }
        return SessionUpdate(session, boundary = lastBoundary)
    }

    fun encode() = listOf(
        id, startedAt, boot, setsCompleted, phase, deadline ?: -1L,
        checkpointRemaining, distractions, config.encode()
    ).joinToString("|")

    companion object {
        // Valida los datos persistidos antes de restaurar una sesión.
        fun decode(payload: String?): Session? {
            val fields = payload?.split('|', limit = 9) ?: return null
            if (fields.size != 9) return null
            val config = RoutineConfig.decode(fields[8]) ?: return null
            val phase = Phase.entries.firstOrNull { it.name == fields[4] } ?: return null
            val session = Session(
                id = fields[0],
                startedAt = fields[1].toLongOrNull() ?: return null,
                config = config,
                boot = fields[2].toIntOrNull() ?: return null,
                setsCompleted = fields[3].toIntOrNull() ?: return null,
                phase = phase,
                deadline = (fields[5].toLongOrNull() ?: return null).takeUnless { it == -1L },
                checkpointRemaining = fields[6].toLongOrNull() ?: return null,
                distractions = fields[7].toIntOrNull() ?: return null
            )
            return session.takeIf {
                it.checkpointRemaining in 1..it.phaseDuration &&
                    (it.deadline == null || it.deadline > 0) &&
                    (it.phase != Phase.REST || it.setsCompleted > 0) &&
                    it.result(0).isValid
            }
        }
    }
}

internal data class SessionUpdate(
    val session: Session?,
    val result: SessionResult? = null,
    val boundary: Long? = null
)

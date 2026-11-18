package com.example.focuswear.routine

import java.util.UUID

const val CONFIG_PATH = "/focuswear/v1/config"
const val CONFIG_ACK_PREFIX = "/focuswear/v1/config-ack/"
const val RESULT_PREFIX = "/focuswear/v1/result/"

data class RoutineConfig(val studyMinutes: Int, val restMinutes: Int, val sets: Int) {
    val isValid get() = studyMinutes in 1..120 && restMinutes in 1..60 && sets in 1..12
    val studyMillis get() = studyMinutes * 60_000L
    val restMillis get() = restMinutes * 60_000L

    fun encode() = "$studyMinutes|$restMinutes|$sets"

    companion object {
        fun decode(payload: String?): RoutineConfig? {
            val fields = payload?.split('|') ?: return null
            if (fields.size != 3) return null
            return RoutineConfig(
                fields[0].toIntOrNull() ?: return null,
                fields[1].toIntOrNull() ?: return null,
                fields[2].toIntOrNull() ?: return null
            ).takeIf { it.isValid }
        }
    }
}

data class ConfigRequest(val id: String, val revision: Long, val config: RoutineConfig) {
    fun encode() = "$id|$revision|${config.encode()}"

    companion object {
        fun decode(payload: String?): ConfigRequest? {
            val fields = payload?.split('|', limit = 3) ?: return null
            if (fields.size != 3 || !isSessionId(fields[0])) return null
            val revision = fields[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return ConfigRequest(fields[0], revision, RoutineConfig.decode(fields[2]) ?: return null)
        }
    }
}

enum class ConfigReceipt { NEW, DUPLICATE, STALE }

fun ConfigRequest.receiptAfter(previous: ConfigRequest?): ConfigReceipt = when {
    previous == this -> ConfigReceipt.DUPLICATE
    previous == null || revision > previous.revision -> ConfigReceipt.NEW
    else -> ConfigReceipt.STALE
}

data class SessionResult(
    val id: String,
    val startedAt: Long,
    val config: RoutineConfig,
    val completedSets: Int,
    val studyMillis: Long,
    val distractions: Int,
    val completed: Boolean
) {
    val isValid: Boolean
        get() {
            if (!config.isValid || !isSessionId(id) || startedAt <= 0 || distractions < 0) return false
            if (completed != (completedSets == config.sets) || completedSets !in 0..config.sets) {
                return false
            }
            val fullStudy = completedSets * config.studyMillis
            return if (completed) studyMillis == fullStudy
            else studyMillis in fullStudy until (fullStudy + config.studyMillis)
        }

    fun encode() =
        "$id|$startedAt|$completedSets|$studyMillis|$distractions|$completed|${config.encode()}"

    companion object {
        fun decode(payload: String?): SessionResult? {
            val fields = payload?.split('|', limit = 7) ?: return null
            if (fields.size != 7) return null
            return SessionResult(
                fields[0],
                fields[1].toLongOrNull() ?: return null,
                RoutineConfig.decode(fields[6]) ?: return null,
                fields[2].toIntOrNull() ?: return null,
                fields[3].toLongOrNull() ?: return null,
                fields[4].toIntOrNull() ?: return null,
                fields[5].toBooleanStrictOrNull() ?: return null
            ).takeIf { it.isValid }
        }
    }
}

private fun isSessionId(value: String): Boolean = try {
    UUID.fromString(value).toString() == value
} catch (_: IllegalArgumentException) {
    false
}

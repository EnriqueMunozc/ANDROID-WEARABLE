
package com.example.focuswear

object RoutineTimeCalculator {

    fun calcularMinutosTotales(
        estudio: Int,
        descanso: Int,
        ciclos: Int
    ): Int {
        require(estudio > 0)
        require(descanso >= 0)
        require(ciclos > 0)

        return Math.addExact(
            Math.multiplyExact(estudio, ciclos),
            Math.multiplyExact(descanso, ciclos)
        )
    }

    fun formatearDuracion(minutos: Int): String {
        require(minutos >= 0)

        val horas = minutos / 60
        val minutosRestantes = minutos % 60

        return if (horas > 0) {
            "$horas h $minutosRestantes min"
        } else {
            "$minutosRestantes min"
        }
    }


fun formatearComoReloj(minutos: Int): String {
    require(minutos >= 0) {
        "Los minutos no pueden ser negativos"
    }

    val horas = minutos / 60
    val minutosRestantes = minutos % 60

    return String.format(
        java.util.Locale.ROOT,
        "%02d:%02d:00",
        horas,
        minutosRestantes
    )
}

}

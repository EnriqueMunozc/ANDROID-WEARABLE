package com.example.focuswear.presentation

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.focuswear.presentation.theme.FocusWearTheme
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FocusWearTheme {
                FocusWearScreen()
            }
        }
    }
}

/* Conserva el estado necesario para recuperar una rutina. */
private data class Rutina(
    val estudioMinutos: Int = 1,
    val descansoMinutos: Int = 1,
    val setsTotales: Int = 3,
    val setActual: Int = 1,
    val etapa: String = "Lista",
    val activa: Boolean = false,
    val pausada: Boolean = false,
    val finEtapa: Long = 0L,
    val restantePausa: Long = 0L,
    val distracciones: Int = 0
)

private fun cargarRutina(context: Context): Rutina {
    val datos = context.getSharedPreferences(
        "focuswear",
        Context.MODE_PRIVATE
    )

    return Rutina(
        estudioMinutos = datos.getInt("estudio", 1),
        descansoMinutos = datos.getInt("descanso", 1),
        setsTotales = datos.getInt("sets", 3),
        setActual = datos.getInt("setActual", 1),
        etapa = datos.getString("etapa", "Lista") ?: "Lista",
        activa = datos.getBoolean("activa", false),
        pausada = datos.getBoolean("pausada", false),
        finEtapa = datos.getLong("finEtapa", 0L),
        restantePausa = datos.getLong("restantePausa", 0L),
        distracciones = datos.getInt("distracciones", 0)
    )
}

private fun guardarRutina(context: Context, rutina: Rutina) {
    context.getSharedPreferences("focuswear", Context.MODE_PRIVATE)
        .edit()
        .putInt("estudio", rutina.estudioMinutos)
        .putInt("descanso", rutina.descansoMinutos)
        .putInt("sets", rutina.setsTotales)
        .putInt("setActual", rutina.setActual)
        .putString("etapa", rutina.etapa)
        .putBoolean("activa", rutina.activa)
        .putBoolean("pausada", rutina.pausada)
        .putLong("finEtapa", rutina.finEtapa)
        .putLong("restantePausa", rutina.restantePausa)
        .putInt("distracciones", rutina.distracciones)
        .apply()
}

private fun vibrar(context: Context) {
    val vibrador = context.getSystemService(Vibrator::class.java)

    if (vibrador?.hasVibrator() == true) {
        vibrador.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 300, 150, 300),
                -1
            )
        )
    }
}

private fun formatoTiempo(milisegundos: Long): String {
    val segundos = (milisegundos.coerceAtLeast(0L) + 999L) / 1000L
    val minutos = segundos / 60
    val resto = segundos % 60

    return "%02d:%02d".format(minutos, resto)
}

/* Calcula las transiciones a partir de la hora de fin de cada etapa. */
private fun actualizarEtapas(rutina: Rutina, ahora: Long): Rutina {
    var resultado = rutina

    while (
        resultado.activa &&
        !resultado.pausada &&
        ahora >= resultado.finEtapa
    ) {
        resultado = when {
            resultado.etapa == "Estudio" &&
                    resultado.setActual >= resultado.setsTotales -> {
                resultado.copy(
                    etapa = "Terminada",
                    activa = false,
                    finEtapa = 0L
                )
            }

            resultado.etapa == "Estudio" -> {
                resultado.copy(
                    etapa = "Descanso",
                    finEtapa = resultado.finEtapa +
                            resultado.descansoMinutos * 60_000L
                )
            }

            else -> {
                resultado.copy(
                    etapa = "Estudio",
                    setActual = resultado.setActual + 1,
                    finEtapa = resultado.finEtapa +
                            resultado.estudioMinutos * 60_000L
                )
            }
        }
    }

    return resultado
}

@Composable
private fun FocusWearScreen() {
    val context = LocalContext.current

    var rutina by remember {
        mutableStateOf(cargarRutina(context))
    }

    var ahora by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    fun guardar(nueva: Rutina) {
        rutina = nueva
        guardarRutina(context, nueva)
    }

    /* Actualiza el tiempo visible y procesa los cambios de etapa. */
    LaunchedEffect(Unit) {
        while (true) {
            ahora = System.currentTimeMillis()

            val actualizada = actualizarEtapas(rutina, ahora)

            if (actualizada != rutina) {
                guardar(actualizada)
                vibrar(context)
            }

            delay(250L)
        }
    }

    val restante = when {
        !rutina.activa -> 0L
        rutina.pausada -> rutina.restantePausa
        else -> (rutina.finEtapa - ahora).coerceAtLeast(0L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "FocusWear",
            textAlign = TextAlign.Center
        )

        if (!rutina.activa) {
            Text(
                text = when (rutina.etapa) {
                    "Terminada" -> "¡Rutina terminada!"
                    "Finalizada" -> "Rutina finalizada antes de tiempo"
                    else -> "Rutina de prueba"
                },
                textAlign = TextAlign.Center
            )

            Text(
                text = "${rutina.estudioMinutos} min de estudio\n" +
                        "${rutina.descansoMinutos} min de descanso\n" +
                        "${rutina.setsTotales} sets",
                textAlign = TextAlign.Center
            )

            if (rutina.etapa != "Lista") {
                Text(
                    text = "Distracciones: ${rutina.distracciones}",
                    textAlign = TextAlign.Center
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val inicio = System.currentTimeMillis()
                    ahora = inicio

                    guardar(
                        rutina.copy(
                            setActual = 1,
                            etapa = "Estudio",
                            activa = true,
                            pausada = false,
                            finEtapa = inicio +
                                    rutina.estudioMinutos * 60_000L,
                            restantePausa = 0L,
                            distracciones = 0
                        )
                    )
                }
            ) {
                Text("Iniciar rutina")
            }
        } else {
            Text(
                text = "${rutina.etapa} · " +
                        "${rutina.setActual}/${rutina.setsTotales}",
                textAlign = TextAlign.Center
            )

            Text(
                text = formatoTiempo(restante),
                textAlign = TextAlign.Center
            )

            if (rutina.pausada) {
                Text("En pausa")
            }

            Text(
                text = "Distracciones: ${rutina.distracciones}",
                textAlign = TextAlign.Center
            )

            if (rutina.etapa == "Estudio" && !rutina.pausada) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val momento = System.currentTimeMillis()
                        val actualizada = actualizarEtapas(rutina, momento)

                        if (actualizada != rutina) {
                            guardar(actualizada)
                            vibrar(context)
                        } else {
                            guardar(
                                rutina.copy(
                                    distracciones = rutina.distracciones + 1
                                )
                            )
                        }
                    }
                ) {
                    Text("Me distraje")
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val momento = System.currentTimeMillis()
                    ahora = momento

                    if (rutina.pausada) {
                        guardar(
                            rutina.copy(
                                pausada = false,
                                finEtapa = momento + rutina.restantePausa,
                                restantePausa = 0L
                            )
                        )
                    } else {
                        val actualizada = actualizarEtapas(rutina, momento)

                        if (actualizada != rutina) {
                            guardar(actualizada)
                            vibrar(context)
                        } else {
                            guardar(
                                rutina.copy(
                                    pausada = true,
                                    restantePausa =
                                        (rutina.finEtapa - momento)
                                            .coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                }
            ) {
                Text(if (rutina.pausada) "Reanudar" else "Pausar")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val actualizada = actualizarEtapas(
                        rutina,
                        System.currentTimeMillis()
                    )

                    guardar(
                        actualizada.copy(
                            etapa = if (actualizada.etapa == "Terminada") {
                                "Terminada"
                            } else {
                                "Finalizada"
                            },
                            activa = false,
                            pausada = false,
                            finEtapa = 0L,
                            restantePausa = 0L
                        )
                    )
                }
            ) {
                Text("Finalizar")
            }
        }
    }
}
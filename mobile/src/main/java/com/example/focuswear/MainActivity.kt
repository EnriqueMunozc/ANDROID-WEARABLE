package com.example.focuswear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.focuswear.ui.theme.FocusWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FocusWearTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { espacioSistema ->
                    ConfiguracionScreen(
                        modifier = Modifier.padding(espacioSistema)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfiguracionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val preferencias = remember(context) {
        context.getSharedPreferences(
            "focuswear_configuracion",
            Context.MODE_PRIVATE
        )
    }

    var estudio by rememberSaveable {
        mutableStateOf(
            preferencias.getInt("estudio", 25).toString()
        )
    }

    var descanso by rememberSaveable {
        mutableStateOf(
            preferencias.getInt("descanso", 5).toString()
        )
    }

    var sets by rememberSaveable {
        mutableStateOf(
            preferencias.getInt("sets", 4).toString()
        )
    }

    var mensaje by rememberSaveable {
        mutableStateOf("")
    }

    val minutosEstudio = estudio.toIntOrNull()
    val minutosDescanso = descanso.toIntOrNull()
    val cantidadSets = sets.toIntOrNull()

    val estudioValido =
        minutosEstudio != null && minutosEstudio in 1..120

    val descansoValido =
        minutosDescanso != null && minutosDescanso in 1..60

    val setsValidos =
        cantidadSets != null && cantidadSets in 1..12

    val configuracionValida =
        estudioValido && descansoValido && setsValidos

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "FocusWear",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Configurar rutina",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "Cada set incluye un bloque de estudio. " +
                    "Los descansos se realizan entre sets."
        )

        CampoNumerico(
            titulo = "Minutos de estudio",
            valor = estudio,
            ayuda = "Entre 1 y 120 minutos",
            error = !estudioValido,
            alCambiar = {
                estudio = it
                mensaje = ""
            }
        )

        CampoNumerico(
            titulo = "Minutos de descanso",
            valor = descanso,
            ayuda = "Entre 1 y 60 minutos",
            error = !descansoValido,
            alCambiar = {
                descanso = it
                mensaje = ""
            }
        )

        CampoNumerico(
            titulo = "Cantidad de sets",
            valor = sets,
            ayuda = "Entre 1 y 12 sets",
            error = !setsValidos,
            alCambiar = {
                sets = it
                mensaje = ""
            }
        )

        if (configuracionValida) {
            val totalEstudio =
                minutosEstudio!! * cantidadSets!!

            val totalDescanso =
                minutosDescanso!! * (cantidadSets - 1)

            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Estudio: $totalEstudio minutos\n" +
                        "Descansos: $totalDescanso minutos\n" +
                        "Duración total: " +
                        "${totalEstudio + totalDescanso} minutos"
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = configuracionValida,
            onClick = {
                val estudioGuardar = estudio.toIntOrNull()
                val descansoGuardar = descanso.toIntOrNull()
                val setsGuardar = sets.toIntOrNull()

                if (
                    estudioGuardar != null &&
                    estudioGuardar in 1..120 &&
                    descansoGuardar != null &&
                    descansoGuardar in 1..60 &&
                    setsGuardar != null &&
                    setsGuardar in 1..12
                ) {
                    preferencias.edit()
                        .putInt("estudio", estudioGuardar)
                        .putInt("descanso", descansoGuardar)
                        .putInt("sets", setsGuardar)
                        .apply()

                    mensaje = "Configuración guardada en el celular."
                }
            }
        ) {
            Text("Guardar configuración")
        }

        if (mensaje.isNotEmpty()) {
            Text(mensaje)
        }
    }
}

@Composable
private fun CampoNumerico(
    titulo: String,
    valor: String,
    ayuda: String,
    error: Boolean,
    alCambiar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo ->
            if (
                nuevo.length <= 3 &&
                nuevo.all { it in '0'..'9' }
            ) {
                alCambiar(nuevo)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(titulo) },
        supportingText = { Text(ayuda) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        )
    )
}
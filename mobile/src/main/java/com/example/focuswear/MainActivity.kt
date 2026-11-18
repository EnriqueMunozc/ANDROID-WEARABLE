package com.example.focuswear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.ui.theme.FocusWearTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val routine by lazy { PhoneRoutine.from(this) }

    override fun onStart() {
        super.onStart()
        routine.open()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FocusWearTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { espacioSistema ->
                    ConfiguracionScreen(
                        routine = routine,
                        modifier = Modifier.padding(espacioSistema)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfiguracionScreen(routine: PhoneRoutine, modifier: Modifier = Modifier) {
    val state by routine.state.collectAsState()
    val initial = state.saved ?: RoutineConfig(25, 5, 4)
    var estudio by rememberSaveable { mutableStateOf(initial.studyMinutes.toString()) }
    var descanso by rememberSaveable { mutableStateOf(initial.restMinutes.toString()) }
    var sets by rememberSaveable { mutableStateOf(initial.sets.toString()) }

    val study = estudio.toIntOrNull()?.takeIf { it in 1..120 }
    val rest = descanso.toIntOrNull()?.takeIf { it in 1..60 }
    val setCount = sets.toIntOrNull()?.takeIf { it in 1..12 }
    val draft = if (study != null && rest != null && setCount != null) {
        RoutineConfig(study, rest, setCount)
    } else null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("FocusWear", style = MaterialTheme.typography.headlineMedium)
            Text("Configurar rutina", style = MaterialTheme.typography.titleLarge)
        }
        item {
            CampoNumerico("Minutos de estudio", estudio, "Entre 1 y 120 minutos", study == null) { estudio = it }
        }
        item {
            CampoNumerico("Minutos de descanso", descanso, "Entre 1 y 60 minutos", rest == null) { descanso = it }
        }
        item {
            CampoNumerico("Cantidad de sets", sets, "Entre 1 y 12 sets", setCount == null) { sets = it }
        }
        if (draft != null) {
            item {
                val studyTotal = draft.studyMinutes * draft.sets
                val restTotal = draft.restMinutes * (draft.sets - 1)
                Text("Estudio: $studyTotal min · Descansos: $restTotal min")
                Text("Duración total: ${studyTotal + restTotal} min")
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = draft != null && draft != state.saved,
                onClick = { draft?.let(routine::save) }
            ) { Text("Guardar configuración") }
            if (state.saved != null && draft == state.saved) {
                Text("Guardado en el celular.")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = draft != null && draft == state.saved,
                onClick = routine::send
            ) {
                val pending = state.request?.let { it.config == state.saved && !state.received } == true
                Text(if (pending) "Reintentar envío" else "Enviar al reloj")
            }
            state.request?.let { request ->
                val status = if (state.received) "Recibido por el reloj" else "Pendiente de envío"
                Text("$status · ${descripcion(request.config)}")
            }
            if (state.storageError) Text("Error de almacenamiento local.")
        }
        item {
            Text("Historial de sesiones", style = MaterialTheme.typography.titleLarge)
            if (state.results.isEmpty()) Text("Todavía no hay sesiones recibidas.")
        }
        items(state.results, key = { it.id }) { result ->
            val date = DateFormat.getDateTimeInstance().format(Date(result.startedAt))
            val seconds = result.studyMillis / 1_000L
            val effective = "%d:%02d".format(seconds / 60, seconds % 60)
            Text(date, style = MaterialTheme.typography.titleMedium)
            Text(descripcion(result.config))
            Text("Sets completados: ${result.completedSets}/${result.config.sets}")
            Text("Estudio efectivo: $effective")
            Text("Distracciones: ${result.distractions}")
            Text(if (result.completed) "Completa" else "Finalizada antes de tiempo")
        }
    }
}

private fun descripcion(config: RoutineConfig) =
    "${config.studyMinutes} min de estudio, ${config.restMinutes} min de descanso, ${config.sets} sets"

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

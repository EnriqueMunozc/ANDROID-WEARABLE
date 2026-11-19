package com.example.focuswear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult
import com.example.focuswear.ui.theme.Evergreen
import com.example.focuswear.ui.theme.FocusWearTheme
import com.example.focuswear.ui.theme.Moss
import com.example.focuswear.ui.theme.Sage
import com.example.focuswear.ui.theme.SageHint
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
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
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
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { FocusHeader() }
        item {
            SectionHeader(
                eyebrow = "TU PRÓXIMA RUTINA",
                title = "Configura tu enfoque",
                description = "Define un ritmo sostenible y envíalo a tu reloj."
            )
        }
        item {
            ConfigurationCard(
                estudio = estudio,
                descanso = descanso,
                sets = sets,
                studyError = study == null,
                restError = rest == null,
                setsError = setCount == null,
                onStudyChange = { estudio = it },
                onRestChange = { descanso = it },
                onSetsChange = { sets = it }
            )
        }
        if (draft != null) item { RoutineSummary(draft) }
        item {
            ActionCard(
                draft = draft,
                saved = state.saved,
                hasRequest = state.request?.config == state.saved,
                received = state.received,
                storageError = state.storageError,
                requestDescription = state.request?.let { descripcion(it.config) },
                onSave = { draft?.let(routine::save) },
                onSend = routine::send
            )
        }
        item {
            SectionHeader(
                eyebrow = "REGISTRO",
                title = "Historial de sesiones",
                description = "Tus sesiones más recientes aparecen primero."
            )
        }
        if (state.results.isEmpty()) {
            item { EmptyHistory() }
        } else {
            items(state.results, key = { it.id }) { result -> HistoryCard(result) }
        }
    }
}

@Composable
private fun FocusHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = SageHint) {
            FocusMark(modifier = Modifier.padding(9.dp), color = Evergreen, accent = Moss)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FocusWear",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Enfoque que viaja contigo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigurationCard(
    estudio: String,
    descanso: String,
    sets: String,
    studyError: Boolean,
    restError: Boolean,
    setsError: Boolean,
    onStudyChange: (String) -> Unit,
    onRestChange: (String) -> Unit,
    onSetsChange: (String) -> Unit
) {
    FocusCard {
        CampoNumerico("Estudio", "min", estudio, "De 1 a 120 minutos", studyError, onStudyChange)
        CampoNumerico("Descanso", "min", descanso, "De 1 a 60 minutos", restError, onRestChange)
        CampoNumerico("Cantidad de sets", "sets", sets, "De 1 a 12 sets", setsError, onSetsChange)
    }
}

@Composable
private fun RoutineSummary(config: RoutineConfig) {
    val studyTotal = config.studyMinutes * config.sets
    val restTotal = config.restMinutes * (config.sets - 1)
    val total = studyTotal + restTotal

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    FocusMark(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Resumen de la rutina", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("$total min en total", style = MaterialTheme.typography.bodyMedium)
                }
                Text("${config.sets}×", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("Estudio", "$studyTotal min", Modifier.weight(1f))
                SummaryMetric("Descansos", "$restTotal min", Modifier.weight(1f))
                SummaryMetric("Duración", "$total min", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionCard(
    draft: RoutineConfig?,
    saved: RoutineConfig?,
    hasRequest: Boolean,
    received: Boolean,
    storageError: Boolean,
    requestDescription: String?,
    onSave: () -> Unit,
    onSend: () -> Unit
) {
    val pending = hasRequest && !received
    FocusCard {
        Text("Sincronización", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Primero guarda los cambios y después envíalos al reloj.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = draft != null && draft != saved,
            onClick = onSave,
            shape = RoundedCornerShape(16.dp)
        ) { Text("Guardar configuración", fontWeight = FontWeight.SemiBold) }
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = draft != null && draft == saved,
            onClick = onSend,
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (pending) "Reintentar envío" else "Enviar al reloj", fontWeight = FontWeight.SemiBold) }
        when {
            storageError -> StatusMessage("No se pudo guardar", "Error de almacenamiento local.", StatusTone.Error)
            requestDescription != null && received -> StatusMessage(
                "Recibido por el reloj", requestDescription, StatusTone.Success
            )
            requestDescription != null -> StatusMessage("Pendiente de envío", requestDescription, StatusTone.Pending)
            saved != null && draft == saved -> StatusMessage(
                "Guardado en el celular", "Listo para enviar al reloj.", StatusTone.Saved
            )
        }
    }
}

private enum class StatusTone { Saved, Pending, Success, Error }

@Composable
private fun StatusMessage(title: String, detail: String, tone: StatusTone) {
    val (container, foreground) = when (tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Pending -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.Saved -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(container).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(12.dp).clip(CircleShape).background(foreground),
            contentAlignment = Alignment.Center
        ) {
            if (tone == StatusTone.Pending) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(container))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = foreground)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = foreground)
        }
    }
}

@Composable
private fun HistoryCard(result: SessionResult) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.startedAt))
    val seconds = result.studyMillis / 1_000L
    val effective = "%d:%02d".format(seconds / 60, seconds % 60)
    val accent = if (result.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val statusContainer = if (result.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val statusContent = if (result.completed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.background(accent).width(6.dp).height(220.dp))
            Column(
                modifier = Modifier.padding(18.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            descripcion(result.config),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(statusContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (result.completed) "Completa" else "Anticipada",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusContent
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    HistoryMetric("Sets", "${result.completedSets}/${result.config.sets}", Modifier.weight(1f))
                    HistoryMetric("Estudio", effective, Modifier.weight(1f))
                    HistoryMetric("Distracciones", result.distractions.toString(), Modifier.weight(1f))
                }
                Text(
                    if (result.completed) "Rutina completada según lo planeado" else "Rutina finalizada antes de tiempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            FocusMark(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                accent = MaterialTheme.colorScheme.secondary
            )
        }
        Text("Tu historial empieza aquí", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Completa una rutina en el reloj para ver sus resultados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FocusCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

private fun descripcion(config: RoutineConfig) =
    "${config.studyMinutes} min de estudio, ${config.restMinutes} min de descanso, ${config.sets} sets"

@Composable
private fun CampoNumerico(
    titulo: String,
    unidad: String,
    valor: String,
    ayuda: String,
    error: Boolean,
    alCambiar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo ->
            if (nuevo.length <= 3 && nuevo.all { it in '0'..'9' }) alCambiar(nuevo)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(titulo) },
        suffix = { Text(unidad, fontWeight = FontWeight.SemiBold) },
        supportingText = { Text(if (error) "$ayuda · Revisa este valor" else ayuda) },
        isError = error,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
        )
    )
}

@Composable
private fun FocusMark(modifier: Modifier = Modifier, color: Color, accent: Color) {
    Canvas(modifier = modifier.semantics { contentDescription = "Símbolo de FocusWear: reloj con hoja" }) {
        val stroke = size.minDimension * 0.11f
        val center = Offset(size.width * 0.46f, size.height * 0.56f)
        val radius = size.minDimension * 0.31f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = stroke))
        drawLine(
            color = accent,
            start = center,
            end = Offset(size.width * 0.33f, size.height * 0.43f),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accent,
            start = center,
            end = Offset(size.width * 0.66f, size.height * 0.39f),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
        drawCircle(color = color, radius = stroke * 0.66f, center = center)
        drawOval(
            color = Sage,
            topLeft = Offset(size.width * 0.62f, size.height * 0.08f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.38f)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.65f, size.height * 0.42f),
            end = Offset(size.width * 0.84f, size.height * 0.16f),
            strokeWidth = stroke * 0.35f,
            cap = StrokeCap.Round
        )
    }
}

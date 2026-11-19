package com.example.focuswear.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.focuswear.Phase
import com.example.focuswear.Session
import com.example.focuswear.WatchRoutine
import com.example.focuswear.presentation.theme.DeepGreen
import com.example.focuswear.presentation.theme.Evergreen
import com.example.focuswear.presentation.theme.FocusWearTheme
import com.example.focuswear.presentation.theme.Mint
import com.example.focuswear.presentation.theme.Moss
import com.example.focuswear.presentation.theme.RestGold
import com.example.focuswear.presentation.theme.Sage
import com.example.focuswear.presentation.theme.StudyGreen
import com.example.focuswear.presentation.theme.Warning
import com.example.focuswear.presentation.theme.WatchSurface
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val routine by lazy { WatchRoutine.from(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusWearTheme {
                FocusWearScreen(routine, lifecycle, ::requestExactAlarms)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        routine.open()
    }

    override fun onPause() {
        routine.checkpoint()
        super.onPause()
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}

@Composable
private fun FocusWearScreen(routine: WatchRoutine, lifecycle: Lifecycle, requestExactAlarms: () -> Unit) {
    val state by routine.state.collectAsState()
    val session = state.session
    val deadline = session?.deadline
    var now by remember(deadline) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    val confirming = session != null && confirmId == session.id
    BackHandler(enabled = confirming) { confirmId = null }

    LaunchedEffect(deadline, lifecycle) {
        if (deadline == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                if (now >= deadline) {
                    routine.reconcile(now)
                    break
                }
                delay((deadline - now - 1) % 1_000L + 1)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BrandHeader()
        when {
            confirming -> ConfirmFinish(
                onFinish = {
                    confirmId = null
                    routine.finish(session.id)
                },
                onBack = { confirmId = null }
            )
            session != null -> ActiveSession(
                session = session,
                now = now,
                recoveredAfterRestart = state.recoveredAfterRestart,
                onDistract = { routine.distract(session.id) },
                onTogglePause = { routine.togglePause(session.id) },
                onFinish = { confirmId = session.id }
            )
            else -> IdleSession(
                configReceived = state.configReceived,
                config = state.nextConfig,
                lastResultCompleted = state.lastResult?.completed,
                lastResultSets = state.lastResult?.let { "${it.completedSets}/${it.config.sets}" },
                lastResultDistractions = state.lastResult?.distractions,
                onStart = routine::start
            )
        }
        if (!routine.exactAlarmsAllowed) {
            AlarmWarning(requestExactAlarms)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        FocusMark(
            modifier = Modifier.size(28.dp),
            color = Mint,
            accent = Sage
        )
        Column {
            Text(
                text = "FocusWear",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Enfócate en tus objetivos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun ActiveSession(
    session: Session,
    now: Long,
    recoveredAfterRestart: Boolean,
    onDistract: () -> Unit,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit
) {
    val studying = session.phase == Phase.STUDY
    val label = if (studying) "ESTUDIO" else "DESCANSO"
    val set = session.setsCompleted + if (studying) 1 else 0
    val remaining = session.remaining(now)
    val seconds = (remaining + 999L) / 1_000L
    val duration = if (studying) session.config.studyMillis else session.config.restMillis
    val progress = (1f - remaining.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val phaseColor = if (studying) StudyGreen else RestGold

    PhaseBadge(label = label, color = phaseColor)
    Text(
        text = "Set $set de ${session.config.sets}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    TimerDial(
        time = "%02d:%02d".format(seconds / 60, seconds % 60),
        progress = progress,
        accent = phaseColor,
        paused = session.paused
    )
    if (session.paused) {
        StatusPill("EN PAUSA", MaterialTheme.colorScheme.secondaryContainer, Mint)
    }
    if (recoveredAfterRestart) {
        NoticeCard("Rutina recuperada en pausa")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Distracciones", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(session.distractions.toString(), color = phaseColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
    if (studying && !session.paused) {
        WatchButton("Me distraje", onDistract, WatchButtonStyle.Secondary)
    }
    WatchButton(if (session.paused) "Reanudar" else "Pausar", onTogglePause, WatchButtonStyle.Primary)
    WatchButton("Finalizar", onFinish, WatchButtonStyle.Ghost)
}

@Composable
private fun TimerDial(time: String, progress: Float, accent: Color, paused: Boolean) {
    Box(
        modifier = Modifier.size(145.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "Progreso de la etapa: ${(progress * 100).toInt()} por ciento"
            }
        ) {
            val width = 8.dp.toPx()

            drawArc(
                color = Evergreen,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width, cap = StrokeCap.Round)
            )

            drawArc(
                color = if (paused) Sage else accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )

            Text(
                text = if (paused) "PAUSADO" else "TIEMPO RESTANTE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ConfirmFinish(onFinish: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text("!", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
    Text(
        "¿Finalizar antes de tiempo?",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Text(
        "Se guardará el progreso realizado hasta ahora.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center
    )
    WatchButton("Sí, finalizar", onFinish, WatchButtonStyle.Warning)
    WatchButton("Volver a la rutina", onBack, WatchButtonStyle.Secondary)
}

@Composable
private fun IdleSession(
    configReceived: Boolean,
    config: com.example.focuswear.routine.RoutineConfig,
    lastResultCompleted: Boolean?,
    lastResultSets: String?,
    lastResultDistractions: Int?,
    onStart: () -> Unit
) {
    if (lastResultCompleted != null && lastResultSets != null && lastResultDistractions != null) {
        ResultCard(lastResultCompleted, lastResultSets, lastResultDistractions)
    }
    Text(
        text = "Tu próxima rutina",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    StatusPill(
        text = if (configReceived) "RECIBIDA DEL CELULAR" else "CONFIGURACIÓN LOCAL",
        background = if (configReceived) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        content = if (configReceived) Mint else MaterialTheme.colorScheme.onSecondaryContainer
    )
    ConfigCard(config.studyMinutes, config.restMinutes, config.sets)
    WatchButton("Iniciar rutina", onStart, WatchButtonStyle.Primary)
}

@Composable
private fun ResultCard(completed: Boolean, sets: String, distractions: Int) {
    val color = if (completed) Mint else Warning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (completed) Evergreen
                else MaterialTheme.colorScheme.tertiaryContainer
            )
            .border(
                1.dp,
                color.copy(alpha = 0.35f),
                RoundedCornerShape(26.dp)
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (completed) "¡Rutina completada!" else "Rutina finalizada",
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            "$sets sets  ·  $distractions distracciones",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ConfigCard(study: Int, rest: Int, sets: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(WatchSurface)
            .border(1.dp, Evergreen, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ConfigRow("Estudio", "$study min", StudyGreen)
        ConfigRow("Descanso", "$rest min", RestGold)
        ConfigRow("Sets", sets.toString(), Mint)
    }
}

@Composable
private fun ConfigRow(label: String, value: String, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhaseBadge(label: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StatusPill(text: String, background: Color, content: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = content, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun NoticeCard(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AlarmWarning(action: () -> Unit) {
    NoticeCard("Los avisos en segundo plano pueden retrasarse.")
    WatchButton("Permitir alarmas", action, WatchButtonStyle.Secondary)
}

private enum class WatchButtonStyle { Primary, Secondary, Ghost, Warning }

@Composable
private fun WatchButton(label: String, action: () -> Unit, style: WatchButtonStyle) {
    val colors = when (style) {
        WatchButtonStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = Mint,
            contentColor = DeepGreen
        )
        WatchButtonStyle.Secondary -> ButtonDefaults.buttonColors(
            containerColor = Evergreen,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        WatchButtonStyle.Ghost -> ButtonDefaults.buttonColors(
            containerColor = WatchSurface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WatchButtonStyle.Warning -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
    Button(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        onClick = action,
        colors = colors
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
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

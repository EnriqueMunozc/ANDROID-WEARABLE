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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.focuswear.Phase
import com.example.focuswear.WatchRoutine
import com.example.focuswear.presentation.theme.FocusWearTheme
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
                // Wake the UI when the next displayed second changes.
                delay((deadline - now - 1) % 1_000L + 1)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("FocusWear")
        if (confirming) {
            Text("¿Finalizar la rutina antes de tiempo?", textAlign = TextAlign.Center)
            WatchButton("Sí, finalizar") {
                confirmId = null
                routine.finish(session.id)
            }
            WatchButton("Volver") { confirmId = null }
        } else if (session != null) {
            val label = if (session.phase == Phase.STUDY) "Estudio" else "Descanso"
            val set = session.setsCompleted + if (session.phase == Phase.STUDY) 1 else 0
            Text("$label · $set/${session.config.sets}")
            val seconds = (session.remaining(now) + 999L) / 1_000L
            Text("%02d:%02d".format(seconds / 60, seconds % 60))
            if (session.paused) Text("En pausa")
            if (state.recoveredAfterRestart) {
                Text("Rutina recuperada en pausa", textAlign = TextAlign.Center)
            }
            Text("Distracciones: ${session.distractions}")
            if (session.phase == Phase.STUDY && !session.paused) {
                WatchButton("Me distraje") { routine.distract(session.id) }
            }
            WatchButton(if (session.paused) "Reanudar" else "Pausar") { routine.togglePause(session.id) }
            WatchButton("Finalizar") { confirmId = session.id }
        } else {
            state.lastResult?.let {
                Text(if (it.completed) "¡Rutina terminada!" else "Rutina finalizada antes de tiempo",
                    textAlign = TextAlign.Center)
                Text("Sets completados: ${it.completedSets}/${it.config.sets}")
                Text("Distracciones: ${it.distractions}")
            }
            Text(if (state.configReceived) "Configuración recibida para la próxima rutina" else "Rutina local",
                textAlign = TextAlign.Center)
            val config = state.nextConfig
            Text(
                "${config.studyMinutes} min de estudio\n${config.restMinutes} min de descanso\n${config.sets} sets",
                textAlign = TextAlign.Center
            )
            WatchButton("Iniciar rutina", routine::start)
        }
        if (!routine.exactAlarmsAllowed) {
            Text("Los avisos en segundo plano pueden retrasarse.", textAlign = TextAlign.Center)
            WatchButton("Permitir alarmas", requestExactAlarms)
        }
    }
}

@Composable
private fun WatchButton(label: String, action: () -> Unit) {
    Button(modifier = Modifier.fillMaxWidth(), onClick = action) {
        Text(label)
    }
}


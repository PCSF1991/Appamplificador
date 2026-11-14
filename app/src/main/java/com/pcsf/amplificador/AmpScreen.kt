@file:OptIn(ExperimentalMaterial3Api::class)

package com.pcsf.amplificador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

private val Accent = Color(0xFF00C2A8)
private val Warn = Color(0xFFFFB74D)

@Composable
fun AmpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            secondary = Color(0xFF3FE0C5),
            background = Color(0xFF0E1116),
            surface = Color(0xFF0E1116),
            surfaceVariant = Color(0xFF1A1F27),
            surfaceContainer = Color(0xFF161B22),
            surfaceContainerHigh = Color(0xFF1A1F27),
        ),
        content = content,
    )
}

@Composable
fun AmpScreen(onPowerChange: (Boolean) -> Unit) {
    val s by EqRepository.settings.collectAsStateWithLifecycle()
    val output by EqRepository.output.collectAsStateWithLifecycle()
    val isBt by EqRepository.isBluetooth.collectAsStateWithLifecycle()
    val engine by EqRepository.engine.collectAsStateWithLifecycle()
    val perDevice by EqRepository.perDevice.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("C&C Amplificador", fontWeight = FontWeight.Bold) })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Liga/desliga + saída
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (s.enabled) "Ligado" else "Desligado",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (s.enabled) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text((if (isBt) "🎧 " else "🔈 ") + output, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = s.enabled, onCheckedChange = onPowerChange)
                }
                if (s.enabled) {
                    Text(engine, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Amplificação
            Section(title = "Amplificação") {
                ValueRow("Ganho", "%+.1f dB".format(s.gainDb))
                Slider(
                    value = s.gainDb,
                    onValueChange = { v -> EqRepository.update { it.copy(gainDb = (v * 2).roundToInt() / 2f) } },
                    valueRange = 0f..GAIN_MAX_DB,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Limitador anti-distorção")
                        Text("Segura os picos para o som não estourar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.limiter, onCheckedChange = { on -> EqRepository.update { it.copy(limiter = on) } })
                }
                if (s.gainDb >= 9f) {
                    Text("⚠ Volume muito alto por muito tempo pode prejudicar a audição.",
                        color = Warn, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---- Equalizador
            Section(title = "Equalizador") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRESETS.forEach { p ->
                        FilterChip(
                            selected = s.preset == p.name,
                            onClick = { EqRepository.update { it.copy(bands = p.bands, preset = p.name) } },
                            label = { Text(p.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    s.bands.forEach { v ->
                        Text(
                            if (v == 0f) "0" else "%+.0f".format(v),
                            Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                EqGraph(
                    values = s.bands,
                    onChange = { band, v ->
                        EqRepository.update {
                            val nb = it.bands.toMutableList().also { l -> l[band] = v }
                            it.copy(bands = nb, preset = "Personalizado")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    BAND_FREQS.forEach { f ->
                        Text(bandLabel(f), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Arraste os pontos • Hz", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        EqRepository.update { it.copy(bands = List(BAND_FREQS.size) { 0f }, preset = "Plano") }
                    }) { Text("Zerar") }
                }
            }

            // ---- Efeitos
            Section(title = "Efeitos") {
                ValueRow("Reforço de graves", "${(s.bass * 100).roundToInt()}%")
                Slider(value = s.bass, onValueChange = { v -> EqRepository.update { it.copy(bass = v) } })
                ValueRow("Som espacial (3D)", "${(s.spatial * 100).roundToInt()}%")
                Slider(value = s.spatial, onValueChange = { v -> EqRepository.update { it.copy(spatial = v) } })
            }

            // ---- Perfis
            Section(title = "Perfis") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Perfil automático por dispositivo")
                        Text("Cada fone/caixa Bluetooth guarda seu próprio ajuste",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = perDevice, onCheckedChange = EqRepository::setPerDevice)
                }
            }

            Text(
                "Dica: se algum app não for afetado, abra o app depois de ligar o amplificador " +
                    "ou ative o \"Equalizador\" nas configurações do player (Spotify, YouTube Music…).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = Accent, fontWeight = FontWeight.SemiBold)
    }
}

/** Gráfico do equalizador: toque/arraste na coluna da banda para ajustar. */
@Composable
private fun EqGraph(values: List<Float>, onChange: (Int, Float) -> Unit, modifier: Modifier = Modifier) {
    val n = values.size
    val onChangeState by rememberUpdatedState(onChange)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val range = BAND_MAX_DB - BAND_MIN_DB

    fun valueAt(y: Float, h: Float): Float {
        val v = BAND_MAX_DB - (y / h) * range
        return ((v.coerceIn(BAND_MIN_DB, BAND_MAX_DB)) * 2).roundToInt() / 2f
    }

    Canvas(
        modifier.pointerInput(n) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val band = ((down.position.x / w) * n).toInt().coerceIn(0, n - 1)
                onChangeState(band, valueAt(down.position.y, h))
                down.consume()
                drag(down.id) { ch ->
                    onChangeState(band, valueAt(ch.position.y, h))
                    ch.consume()
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        fun yOf(db: Float) = (BAND_MAX_DB - db) / range * h
        val colW = w / n

        // grade horizontal: +12, +6, 0, -6, -12
        listOf(12f, 6f, 0f, -6f, -12f).forEach { db ->
            drawLine(
                color = if (db == 0f) grid.copy(alpha = 0.9f) else grid.copy(alpha = 0.35f),
                start = Offset(0f, yOf(db)), end = Offset(w, yOf(db)),
                strokeWidth = if (db == 0f) 2f else 1f,
            )
        }
        // trilhos verticais
        for (i in 0 until n) {
            val x = colW * (i + 0.5f)
            drawLine(grid.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, h), strokeWidth = 6f, cap = StrokeCap.Round)
        }
        // curva suave
        val pts = values.mapIndexed { i, v -> Offset(colW * (i + 0.5f), yOf(v)) }
        val path = Path().apply {
            moveTo(0f, pts.first().y)
            lineTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2f
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
            lineTo(w, pts.last().y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(w, yOf(0f))
            lineTo(0f, yOf(0f))
            close()
        }
        drawPath(fill, Accent.copy(alpha = 0.15f))
        drawPath(path, Accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
        // pontos
        pts.forEach { p ->
            drawCircle(Color.Black, radius = 14f, center = p)
            drawCircle(Accent, radius = 11f, center = p)
        }
    }
}

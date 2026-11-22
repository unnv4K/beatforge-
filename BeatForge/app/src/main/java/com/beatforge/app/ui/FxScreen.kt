package com.beatforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.app.BeatViewModel

@Composable
fun FxScreen(vm: BeatViewModel) {
    vm.observe()
    val m = vm.project.master
    var bars by remember { mutableIntStateOf(2) }

    Column(
        Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card("МАСТЕР") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Knob("Filter LP◄►HP", m.filter, filterText(m.filter), default = 0.5f, color = BF.teal) { vm.edit { m.filter = it } }
                Knob("Resonance", m.resonance, "${(m.resonance * 100).toInt()}%", default = 0.15f, color = BF.teal) { vm.edit { m.resonance = it } }
                Knob("Bitcrusher", m.crush, bitsText(m.crush)) { vm.edit { m.crush = it } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Knob("Reverb Mix", m.reverbMix, "${(m.reverbMix * 100).toInt()}%") { vm.edit { m.reverbMix = it } }
                Knob("Reverb Size", m.reverbSize, "${(m.reverbSize * 100).toInt()}%", default = 0.5f) { vm.edit { m.reverbSize = it } }
                Knob("Master", m.volume, "${(m.volume * 100).toInt()}%", default = 0.85f, color = BF.teal) { vm.edit { m.volume = it } }
            }
            Text("Лимитер на выходе включён всегда — звук не хрипит и не клиппит.", color = BF.dim, fontSize = 12.sp)
        }

        Card("SP-404 · VINYL SIM") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Knob("Vinyl", m.vinyl, if (m.vinyl < 0.01f) "выкл" else "${(m.vinyl * 100).toInt()}%", size = 78.dp) { vm.edit { m.vinyl = it } }
                Text("Компрессор + лёгкий перегруз +\nприглушённый верх + шум и треск\nпластинки.", color = BF.dim, fontSize = 12.sp)
            }
        }

        Card("SP-404 · ISOLATOR (3 полосы)") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                VFader("Low", m.isoLow, isoText(m.isoLow), color = BF.orange) { vm.edit { m.isoLow = it } }
                VFader("Mid", m.isoMid, isoText(m.isoMid), color = BF.teal) { vm.edit { m.isoMid = it } }
                VFader("High", m.isoHigh, isoText(m.isoHigh), color = BF.orange) { vm.edit { m.isoHigh = it } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Kill Low", m.isoLow < 0.05f, accent = BF.red) { vm.edit { m.isoLow = if (m.isoLow < 0.05f) 1f else 0f } }
                Chip("Kill Mid", m.isoMid < 0.05f, accent = BF.red) { vm.edit { m.isoMid = if (m.isoMid < 0.05f) 1f else 0f } }
                Chip("Kill High", m.isoHigh < 0.05f, accent = BF.red) { vm.edit { m.isoHigh = if (m.isoHigh < 0.05f) 1f else 0f } }
                Chip("Reset", false) { vm.edit { m.isoLow = 1f; m.isoMid = 1f; m.isoHigh = 1f } }
            }
        }

        Card("SP-404 · DJFX LOOPER") {
            Text("Зажми кнопку — последний кусочек звука зациклится, пока держишь.", color = BF.dim, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, beats) in listOf("1/2" to 0.5f, "1/4" to 0.25f, "1/8" to 0.125f, "1/16" to 0.0625f)) {
                    HoldButton(
                        "$label доли", false, Modifier.weight(1f),
                        onDown = { vm.edit { m.loopBeats = beats; m.loopActive = true } },
                        onUp = { vm.edit { m.loopActive = false } }
                    )
                }
            }
        }

        Card("ЭКСПОРТ В WAV") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Тактов:", color = BF.dim, fontSize = 13.sp)
                for (b in listOf(1, 2, 4, 8)) Chip("$b", bars == b, accent = BF.teal) { bars = b }
            }
            Text(
                "Рендерится текущий паттерн (${"ABCD"[vm.project.pattern]}) со всеми эффектами мастера + 3 с хвоста реверба. " +
                    "Файл появится в папке Downloads.",
                color = BF.dim, fontSize = 12.sp
            )
            Chip(if (vm.exportProgress >= 0f) "Рендер…" else "Экспорт в Downloads", false, modifier = Modifier.fillMaxWidth()) {
                vm.exportWav(bars)
            }
            if (vm.exportProgress >= 0f) LinearProgressIndicator(
                progress = { vm.exportProgress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = BF.orange, trackColor = BF.panel2
            )
            if (vm.status.isNotEmpty()) Text(vm.status, color = BF.text, fontSize = 12.sp)
        }
    }
}

private fun filterText(f: Float) = when {
    f < 0.48f -> "LP ${(18000 * Math.pow(80.0 / 18000.0, (0.5 - f) / 0.5)).toInt()} Гц"
    f > 0.52f -> "HP ${(20 * Math.pow(400.0, (f - 0.5) / 0.5)).toInt()} Гц"
    else -> "выкл"
}

private fun bitsText(a: Float) = if (a < 0.01f) "выкл" else "${(16 - 12 * a).toInt()} бит"
private fun isoText(v: Float) = if (v < 0.05f) "KILL" else "${(v * 100).toInt()}%"

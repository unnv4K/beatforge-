package com.beatforge.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.beatforge.app.BeatViewModel
import com.beatforge.app.audio.CaptureService
import com.beatforge.app.audio.PlayMode

@Composable
fun ChannelScreen(vm: BeatViewModel) {
    vm.observe()
    val ctx = LocalContext.current
    val ch = vm.project.channels[vm.selected]
    var source by remember { mutableIntStateOf(0) }   // 0 = микрофон, 1 = системный звук

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadSample(uri)
    }
    val projLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, CaptureService::class.java)
                    .putExtra("code", res.resultCode).putExtra("data", res.data)
            )
            vm.recordSystem()
        }
    }
    fun launchCapture() {
        val mpm = ctx.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projLauncher.launch(mpm.createScreenCaptureIntent())
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (source == 0) vm.recordMic() else launchCapture() }
        else vm.status = "Нужно разрешение на микрофон"
    }
    fun toggleRecord() {
        if (vm.recording) { vm.stopRecording(); return }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else if (source == 0) vm.recordMic() else launchCapture()
    }

    Column(
        Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // выбор канала
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 16) Chip("${i + 1}", vm.selected == i, accent = BF.teal) { vm.selected = i }
        }

        Card("КАНАЛ ${vm.selected + 1}  ·  ${ch.sample?.name ?: "пусто"}") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("▶ Слушать", false) { vm.previewSelected() }
                Chip("Загрузить WAV/MP3", false) { picker.launch(arrayOf("audio/*")) }
                Chip("Сброс", false) { vm.resetSample() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(if (vm.recording) "■ СТОП" else "● REC", vm.recording, accent = BF.red) { toggleRecord() }
                Chip("Микрофон", source == 0, accent = BF.teal) { if (!vm.recording) source = 0 }
                Chip("Системный звук", source == 1, accent = BF.teal) { if (!vm.recording) source = 1 }
            }
            if (vm.recording) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { vm.recLevel.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp), color = BF.red, trackColor = BF.panel2
                )
            }
            if (vm.status.isNotEmpty()) Text(vm.status, color = BF.dim, fontSize = 12.sp)
        }

        Card("МИКШЕР КАНАЛА") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                VFader("Volume", ch.volume, "${(ch.volume * 100).toInt()}%", height = 120.dp) { vm.edit { ch.volume = it } }
                Knob("Pan", ch.pan, panText(ch.pan), min = -1f, max = 1f, default = 0f, color = BF.teal) { vm.edit { ch.pan = it } }
                Knob("Pitch", ch.pitch, "%+.1f st".format(ch.pitch), min = -12f, max = 12f, default = 0f) { vm.edit { ch.pitch = Math.round(it * 2) / 2f } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Knob("Attack", ch.attack, "${(ch.attack * ch.attack * 400).toInt()} мс", default = 0f, color = BF.teal) { vm.edit { ch.attack = it } }
                Knob("Decay", ch.decay, if (ch.decay >= 0.999f) "полный" else "${(ch.decay * 100).toInt()}%", default = 1f) { vm.edit { ch.decay = it } }
                Chip(if (ch.muted) "MUTE: вкл" else "MUTE", ch.muted, accent = BF.red, modifier = Modifier.padding(top = 14.dp)) { vm.edit { ch.muted = !ch.muted } }
            }
        }

        Card("РЕЖИМ ЗАПУСКА") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in PlayMode.values()) Chip(m.label, ch.mode == m, accent = BF.teal) { vm.edit { ch.mode = m } }
            }
            if (ch.mode == PlayMode.MUTE_GROUP) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Группа:", color = BF.dim, fontSize = 13.sp)
                    for (g in 1..4) Chip("$g", ch.group == g) { vm.edit { ch.group = g } }
                }
            }
            Text(
                when (ch.mode) {
                    PlayMode.TRIGGER -> "Касание запускает звук, он доигрывает до конца."
                    PlayMode.GATE -> "Звук играет, пока держишь палец. Отпустил — затухает."
                    PlayMode.MUTE_GROUP -> "Пэды одной группы обрывают друг друга — мелодия не превращается в кашу."
                },
                color = BF.dim, fontSize = 12.sp
            )
        }
    }
}

private fun panText(p: Float) = when {
    p < -0.03f -> "L${(-p * 100).toInt()}"
    p > 0.03f -> "R${(p * 100).toInt()}"
    else -> "C"
}

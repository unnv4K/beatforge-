package com.beatforge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.app.BeatViewModel
import com.beatforge.app.audio.NUM_PADS
import com.beatforge.app.audio.PlayMode
import com.beatforge.app.audio.SynthDrums

@Composable
fun StudioScreen(vm: BeatViewModel) {
    vm.observe()
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransportBar(vm)
        PadGrid(vm, Modifier.weight(1f).fillMaxWidth())
        SequencerPanel(vm)
    }
}

@Composable
private fun TransportBar(vm: BeatViewModel) {
    val p = vm.project
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BF.panel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (vm.playing) BF.red else BF.orange)
                .clickable { vm.togglePlay() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(if (vm.playing) "■" else "▶", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Black) }

        Column(Modifier.weight(1.3f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BPM ${p.bpm}", color = BF.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Chip("TAP", false) { vm.tapTempo() }
            }
            Slider(
                value = p.bpm.toFloat(), onValueChange = { vm.edit { p.bpm = it.toInt() } },
                valueRange = 60f..180f, colors = sliderColors(), modifier = Modifier.height(28.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text("Swing ${(p.swing * 100).toInt()}%", color = BF.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Slider(
                value = p.swing, onValueChange = { vm.edit { p.swing = it } },
                colors = sliderColors(), modifier = Modifier.height(28.dp)
            )
        }
    }
}

private fun modeTag(vm: BeatViewModel, i: Int): String {
    val c = vm.project.channels[i]
    return when (c.mode) {
        PlayMode.TRIGGER -> ""
        PlayMode.GATE -> "GATE"
        PlayMode.MUTE_GROUP -> "MG${c.group}"
    }
}

@Composable
private fun PadGrid(vm: BeatViewModel, modifier: Modifier) {
    // подсветка от секвенсора: следим за счётчиками срабатываний
    var flash by remember { mutableStateOf(FloatArray(NUM_PADS)) }
    LaunchedEffect(Unit) {
        val last = IntArray(NUM_PADS)
        val hitAt = LongArray(NUM_PADS)
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val next = FloatArray(NUM_PADS)
                for (i in 0 until NUM_PADS) {
                    val c = vm.engine.mixer.hits.get(i)
                    if (c != last[i]) { last[i] = c; hitAt[i] = now }
                    val age = (now - hitAt[i]) / 1e9f
                    next[i] = if (hitAt[i] != 0L && age < 0.16f) 1f - age / 0.16f else 0f
                }
                if (!next.contentEquals(flash)) flash = next
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 4) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    // нижний ряд = пэды 1-4, как на MPC
                    val i = (3 - row) * 4 + col
                    Pad(vm, i, flash[i], Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Pad(vm: BeatViewModel, i: Int, flash: Float, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val selected = vm.selected == i
    val glow = if (pressed) 1f else flash
    val base = lerp(BF.panel2, BF.orange, glow)
    val name = vm.project.channels[i].sample?.name ?: "—"
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.08f * glow), base)))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BF.teal else Color(0xFF3A3D44)), RoundedCornerShape(12.dp))
            .pointerInput(i) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true; vm.padDown(i)
                    waitForUpOrCancellation()
                    pressed = false; vm.padUp(i)
                }
            }
    ) {
        Text("${i + 1}", color = if (glow > 0.5f) Color.Black else BF.dim, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        val tag = modeTag(vm, i)
        if (tag.isNotEmpty()) Text(tag, color = BF.teal, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        Text(name, color = if (glow > 0.5f) Color.Black else BF.text, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp))
        if (vm.project.channels[i].muted) Text("MUTE", color = BF.red, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
}

@Composable
private fun SequencerPanel(vm: BeatViewModel) {
    val p = vm.project
    var playStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val s = vm.engine.mixer.uiStep
            if (s != playStep) playStep = s
        }
    }
    val padName = p.channels[vm.selected].sample?.name ?: "—"
    val row = p.patterns[p.pattern][vm.selected]

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BF.panel).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in 0 until 4) Chip("ABCD"[k].toString(), p.pattern == k, accent = BF.teal) { vm.setPattern(k) }
            Text("  ${vm.selected + 1}. $padName", color = BF.text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1)
            Chip("Трек ✕", false) { vm.clearTrack() }
            Chip("Все ✕", false) { vm.clearPattern() }
        }
        for (r in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0 until 8) {
                    val s = r * 8 + c
                    val on = row[s]
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (on) BF.orange
                                else if (s % 4 == 0) Color(0xFF3B3E46) else BF.panel2
                            )
                            .border(
                                BorderStroke(if (playStep == s) 2.dp else 0.dp, BF.teal),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { vm.toggleStep(vm.selected, s) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${s + 1}", color = if (on) Color.Black else BF.dim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

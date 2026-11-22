package com.beatforge.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.app.BeatViewModel
import kotlin.math.abs

@Composable
fun SliceScreen(vm: BeatViewModel) {
    vm.observe()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadSliceFile(uri)
    }
    var lastTapped by remember { mutableIntStateOf(-1) }

    Column(
        Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card("НАРЕЗКА (SLICE)") {
            Text(
                "1. Загрузи длинный файл (луп, семпл с пластинки, трек).\n" +
                    "2. Расставь маркеры: тяни за кружки, тап по участку — послушать кусок.\n" +
                    "3. «Применить» раскидает 16 кусков по пэдам 1–16.",
                color = BF.dim, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Загрузить файл", false) { picker.launch(arrayOf("audio/*")) }
                Chip("Поровну", false) { vm.sliceEven() }
                Chip("По ударам", false) { vm.sliceTransients() }
            }
        }

        val src = vm.sliceSource
        if (src == null) {
            Text("Файл не загружен.", color = BF.dim, fontSize = 13.sp)
        } else {
            Card("ВОЛНА · ${src.name}") {
                Waveform(vm, lastTapped) { lastTapped = it }
                Text("Куски 1…16 слева направо. Выбран: ${if (lastTapped >= 0) lastTapped + 1 else "—"}", color = BF.dim, fontSize = 12.sp)
                Chip("Применить: куски → пэды 1–16", true, modifier = Modifier.fillMaxWidth()) { vm.applySlices() }
            }
        }
        if (vm.status.isNotEmpty()) Text(vm.status, color = BF.text, fontSize = 12.sp)
    }
}

@Composable
private fun Waveform(vm: BeatViewModel, selected: Int, onSelect: (Int) -> Unit) {
    val peaks = vm.peaks
    @Suppress("UNUSED_VARIABLE") val t = vm.markerTick
    val markers = vm.markers
    var dragIdx by remember { mutableIntStateOf(-1) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(Color(0xFF101113))
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val x = off.x / size.width
                    var idx = 0
                    for (m in vm.markers) if (x > m) idx++
                    onSelect(idx)
                    vm.previewSlice(idx)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        val x = off.x / size.width
                        var best = -1; var bd = 28f / size.width
                        for (i in vm.markers.indices) {
                            val d = abs(vm.markers[i] - x)
                            if (d < bd) { bd = d; best = i }
                        }
                        dragIdx = best
                    },
                    onDragEnd = { dragIdx = -1 },
                    onDragCancel = { dragIdx = -1 },
                    onDrag = { change, _ ->
                        if (dragIdx >= 0) {
                            change.consume()
                            vm.moveMarker(dragIdx, change.position.x / size.width)
                        }
                    }
                )
            }
    ) {
        val w = size.width; val h = size.height; val mid = h / 2
        // выбранный кусок
        if (selected in 0..markers.size) {
            val a = if (selected == 0) 0f else markers[selected - 1]
            val b = if (selected == markers.size) 1f else markers[selected]
            drawRect(Color(0x3322D3C5), Offset(a * w, 0f), androidx.compose.ui.geometry.Size((b - a) * w, h))
        }
        // волна
        if (peaks.isNotEmpty()) {
            val n = w.toInt()
            for (x in 0 until n) {
                val p = peaks[(x.toLong() * peaks.size / n).toInt().coerceIn(0, peaks.size - 1)].coerceIn(0f, 1f)
                drawLine(BF.orange, Offset(x.toFloat(), mid - p * mid), Offset(x.toFloat(), mid + p * mid), 1f)
            }
        }
        // маркеры
        for (i in markers.indices) {
            val x = markers[i] * w
            drawLine(BF.teal, Offset(x, 0f), Offset(x, h), 2f)
            drawCircle(BF.teal, 11f, Offset(x, 14f))
        }
    }
}

package com.beatforge.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beatforge.app.audio.AudioEngine
import com.beatforge.app.audio.CaptureHolder
import com.beatforge.app.audio.CaptureService
import com.beatforge.app.audio.NUM_PADS
import com.beatforge.app.audio.PlayMode
import com.beatforge.app.audio.Project
import com.beatforge.app.audio.Recorder
import com.beatforge.app.audio.Sample
import com.beatforge.app.audio.SampleIO
import com.beatforge.app.audio.SynthDrums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BeatViewModel(app: Application) : AndroidViewModel(app) {
    val project = Project().also {
        it.loadDefaultSounds()
        it.loadPreset(0, 0)   // стартовый бит — Boom Bap в паттерне A
        it.loadPreset(1, 1)   // Trap в B (BPM останется от последнего пресета — вернём ниже)
        it.loadPreset(2, 2)   // House в C
        it.bpm = 90
    }
    val engine = AudioEngine(project)

    /** Любое изменение параметров увеличивает tick — экраны, читающие observe(), перерисовываются. */
    private var tick by mutableIntStateOf(0)
    fun observe() { tick }
    fun edit(block: () -> Unit) { block(); tick++ }

    var selected by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var status by mutableStateOf("")
    var exportProgress by mutableStateOf(-1f)

    init { engine.start(app) }

    override fun onCleared() { engine.release(); CaptureHolder.release() }

    // ---------- пэды и транспорт ----------
    fun padDown(i: Int) { selected = i; engine.mixer.padDown(i) }
    fun padUp(i: Int) { engine.mixer.padUp(i) }

    fun togglePlay() {
        playing = !playing
        engine.mixer.playRequested = playing
    }

    fun toggleStep(pad: Int, step: Int) = edit {
        val row = project.patterns[project.pattern][pad]
        row[step] = !row[step]
    }

    fun setPattern(p: Int) = edit { project.pattern = p }
    fun clearTrack() = edit { project.patterns[project.pattern][selected].fill(false) }
    fun clearPattern() = edit { project.clearPattern(project.pattern) }
    fun loadPreset(kind: Int, pat: Int = project.pattern) = edit { project.loadPreset(kind, pat); project.pattern = pat }

    private val taps = ArrayList<Long>()
    fun tapTempo() {
        val now = System.nanoTime()
        if (taps.isNotEmpty() && now - taps.last() > 2_000_000_000L) taps.clear()
        taps.add(now)
        if (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()) / (taps.size - 1) / 1e9
            edit { project.bpm = (60.0 / avg).toInt().coerceIn(60, 180) }
        }
    }

    // ---------- сэмплы ----------
    fun loadSample(uri: Uri, pad: Int = selected) {
        viewModelScope.launch {
            status = "Загрузка…"
            try {
                val s = withContext(Dispatchers.IO) { SampleIO.decode(getApplication<Application>(), uri, 60) }
                edit { project.channels[pad].sample = s }
                status = "Пэд ${pad + 1}: ${s.name}"
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            }
        }
    }

    fun resetSample(pad: Int = selected) = edit {
        project.channels[pad].sample = SynthDrums.make(pad)
        status = "Пэд ${pad + 1}: заводской звук"
    }

    fun previewSelected() {
        project.channels[selected].sample?.let { engine.mixer.preview(it) }
    }

    // ---------- запись ----------
    private val recorder = Recorder()
    var recording by mutableStateOf(false)
    var recLevel by mutableStateOf(0f)
    private var recSystem = false

    fun recordMic() = beginRecord(false)

    fun recordSystem() = viewModelScope.launch {
        var n = 0
        while (CaptureHolder.projection == null && n++ < 30) delay(100)
        beginRecord(true)
    }

    private fun beginRecord(system: Boolean) {
        val proj = if (system) CaptureHolder.projection else null
        if (system && proj == null) { status = "Нет доступа к системному звуку"; return }
        if (recorder.start(proj)) {
            recSystem = system
            recording = true
            status = "Запись… нажми ■ чтобы остановить"
            viewModelScope.launch {
                while (recording) {
                    recLevel = recorder.level
                    if (!recorder.isRecording) { stopRecording(); break }
                    delay(50)
                }
            }
        } else status = "Не удалось начать запись (проверь разрешение микрофона)"
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        val s = recorder.stop()
        if (recSystem) {
            getApplication<Application>().startService(
                Intent(getApplication<Application>(), CaptureService::class.java).setAction(CaptureService.ACTION_STOP)
            )
        }
        if (s == null) { status = "Запись пустая"; return }
        edit { project.channels[selected].sample = Sample("Rec ${selected + 1}", s.data) }
        status = "Записано на пэд ${selected + 1} (%.1f с)".format(s.data.size / 48000f)
        engine.mixer.preview(s)
    }

    // ---------- нарезка ----------
    var sliceSource by mutableStateOf<Sample?>(null)
    var peaks by mutableStateOf(FloatArray(0))
    val markers = mutableStateListOf<Float>()   // 15 позиций (0..1) => 16 кусков
    var markerTick by mutableIntStateOf(0)

    fun loadSliceFile(uri: Uri) {
        viewModelScope.launch {
            status = "Загрузка…"
            try {
                val s = withContext(Dispatchers.IO) { SampleIO.decode(getApplication<Application>(), uri, 180) }
                sliceSource = s
                peaks = computePeaks(s.data, 1000)
                sliceEven()
                status = "%s — %.1f с".format(s.name, s.data.size / 48000f)
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            }
        }
    }

    private fun computePeaks(d: FloatArray, n: Int): FloatArray {
        val out = FloatArray(n)
        val per = max(1, d.size / n)
        for (i in 0 until n) {
            var m = 0f
            val a = i * per
            val b = min(d.size, a + per)
            for (j in a until b) m = max(m, abs(d[j]))
            out[i] = m
        }
        return out
    }

    fun sliceEven() {
        markers.clear()
        for (i in 1..15) markers.add(i / 16f)
        markerTick++
    }

    /** Автопоиск ударов: рост энергии между окнами, берём 15 самых сильных с минимальным интервалом. */
    fun sliceTransients() {
        val d = sliceSource?.data ?: return
        val hop = 512
        val n = d.size / hop
        if (n < 40) { sliceEven(); return }
        val e = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (j in i * hop until (i + 1) * hop) s += d[j] * d[j]
            e[i] = s
        }
        val flux = FloatArray(n) { if (it == 0) 0f else max(0f, e[it] - e[it - 1]) }
        val order = (2 until n - 2).sortedByDescending { flux[it] }
        val minGap = max(4, n / 40)
        val picks = ArrayList<Int>()
        for (i in order) {
            if (picks.size >= 15) break
            if (picks.none { abs(it - i) < minGap }) picks.add(i)
        }
        picks.sort()
        val pos = picks.map { it.toFloat() / n }.toMutableList()
        // если ударов нашлось меньше 15 — дополняем равномерно
        var k = 1
        while (pos.size < 15 && k <= 15) {
            val c = k / 16f
            if (pos.none { abs(it - c) < 0.5f / 16f }) pos.add(c)
            k++
        }
        var extra = 0.995f
        while (pos.size < 15) { pos.add(extra); extra -= 0.003f }
        pos.sort()
        markers.clear(); markers.addAll(pos.take(15))
        markerTick++
    }

    fun moveMarker(i: Int, v: Float) {
        val lo = if (i == 0) 0.001f else markers[i - 1] + 0.002f
        val hi = if (i == markers.lastIndex) 0.999f else markers[i + 1] - 0.002f
        markers[i] = v.coerceIn(lo, max(lo, hi))
    }

    private fun slice(i: Int): Sample? {
        val src = sliceSource ?: return null
        val b = ArrayList<Float>(17)
        b.add(0f); b.addAll(markers); b.add(1f)
        val a = (b[i] * src.data.size).toInt()
        val e = (b[i + 1] * src.data.size).toInt()
        if (e - a < 16) return null
        val d = src.data.copyOfRange(a, min(e, src.data.size))
        val fade = min(d.size / 2, 96)
        for (k in 0 until fade) {
            d[d.size - 1 - k] *= k / fade.toFloat()
            d[k] *= k / fade.toFloat()
        }
        return Sample("Slice ${i + 1}", d)
    }

    fun previewSlice(i: Int) { slice(i)?.let { engine.mixer.preview(it) } }

    fun applySlices() = edit {
        var ok = 0
        for (i in 0 until NUM_PADS) {
            val s = slice(i) ?: continue
            val ch = project.channels[i]
            ch.sample = s
            ch.mode = PlayMode.TRIGGER
            ch.pitch = 0f; ch.attack = 0f; ch.decay = 1f
            ok++
        }
        status = "Нарезка применена: $ok кусков → пэды 1–$ok"
    }

    // ---------- экспорт ----------
    fun exportWav(bars: Int) {
        if (exportProgress >= 0f) return
        viewModelScope.launch {
            exportProgress = 0f
            status = "Экспорт…"
            try {
                val name = withContext(Dispatchers.IO) {
                    SampleIO.exportWav(getApplication<Application>(), project, bars) { exportProgress = it }
                }
                status = "Готово: Downloads/$name"
            } catch (e: Exception) {
                status = "Ошибка экспорта: ${e.message}"
            }
            exportProgress = -1f
        }
    }
}

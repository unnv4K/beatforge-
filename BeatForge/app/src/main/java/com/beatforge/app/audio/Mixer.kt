package com.beatforge.app.audio

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MAX_VOICES = 48
private const val MAX_BLOCK = 4096
private const val RING = SR * 4

private class Voice {
    @JvmField var active = false
    @JvmField var data = FloatArray(0)
    @JvmField var pad = -1
    @JvmField var groupMode = false
    @JvmField var group = 0
    @JvmField var pos = 0.0
    @JvmField var rate = 1.0
    @JvmField var limit = 0.0
    @JvmField var fadeStart = 0.0
    @JvmField var atkFrames = 0
    @JvmField var age = 0
    @JvmField var gl = 0f
    @JvmField var gr = 0f
    @JvmField var fading = false
    @JvmField var fade = 1f
    @JvmField var fadeDec = 0f
    @JvmField var autoRel = 0

    fun startFade(seconds: Float) {
        if (!fading) { fading = true; fadeDec = 1f / (seconds * SR) }
    }

    fun render(l: FloatArray, r: FloatArray, from: Int, to: Int) {
        val d = data
        val last = d.size - 1
        var i = from
        while (i < to) {
            val ip = pos.toInt()
            if (pos >= limit || ip >= last) { active = false; return }
            val fr = (pos - ip).toFloat()
            var s = d[ip] + (d[ip + 1] - d[ip]) * fr
            var amp = 1f
            if (age < atkFrames) amp = age.toFloat() / atkFrames
            if (pos > fadeStart) amp *= ((limit - pos) / (limit - fadeStart)).toFloat()
            if (autoRel > 0 && --autoRel == 0) startFade(0.012f)
            if (fading) {
                fade -= fadeDec
                if (fade <= 0f) { active = false; return }
                amp *= fade
            }
            s *= amp
            l[i] += s * gl
            r[i] += s * gr
            pos += rate
            age++
            i++
        }
    }
}

/**
 * Весь звук: голоса сэмплов, секвенсор (sample-accurate, со свингом) и мастер-цепочка.
 * Не знает про Android — поэтому один и тот же код играет в реальном времени и рендерит экспорт.
 */
class Mixer(private val p: Project, private val offline: Boolean = false) {
    private val voices = Array(MAX_VOICES) { Voice() }
    private val events = ConcurrentLinkedQueue<Int>()
    private val previews = ConcurrentLinkedQueue<Sample>()
    val hits = AtomicIntegerArray(NUM_PADS)

    @Volatile var playRequested = false
    @Volatile var uiStep = -1

    // --- секвенсор
    private var playing = false
    private var clock = 0L
    private var gridTime = 0.0
    private var stepCount = 0L
    private var nextFire = 0.0

    // --- шины
    private val busL = FloatArray(MAX_BLOCK)
    private val busR = FloatArray(MAX_BLOCK)

    // --- мастер-эффекты
    private val isolator = Isolator()
    private val filtL = Biquad()
    private val filtR = Biquad()
    private var lastFilter = -1f
    private var lastRes = -1f
    private var filterOn = false
    private val crusher = Bitcrusher()
    private val vinyl = Vinyl()
    private val reverb = Reverb()
    private val limiter = Limiter()

    // --- DJFX Looper
    private val ringL = FloatArray(RING)
    private val ringR = FloatArray(RING)
    private var wIdx = 0
    private var looping = false
    private var loopStart = 0
    private var loopLen = 1
    private var loopPos = 0

    // ---------- API для UI-потока ----------
    fun padDown(i: Int) { events.add(i * 2 + 1) }
    fun padUp(i: Int) { events.add(i * 2) }
    fun preview(s: Sample) { previews.add(s) }

    // ---------- рендер ----------
    fun render(out: FloatArray, frames: Int) {
        require(frames <= MAX_BLOCK)
        while (true) {
            val e = events.poll() ?: break
            val pad = e shr 1
            if ((e and 1) == 1) startVoice(pad, 0) else releasePad(pad)
        }
        while (true) { startPreview(previews.poll() ?: break) }

        val want = playRequested
        if (want && !playing) {
            playing = true; stepCount = 0; gridTime = clock.toDouble(); nextFire = gridTime
        } else if (!want && playing) {
            playing = false; uiStep = -1
        }

        java.util.Arrays.fill(busL, 0, frames, 0f)
        java.util.Arrays.fill(busR, 0, frames, 0f)

        var pos = 0
        while (pos < frames) {
            var end = frames
            if (playing) {
                while (nextFire <= clock + pos) fireStep()
                end = min(frames, ceil(nextFire - clock).toInt())
                if (end <= pos) end = pos + 1
            }
            for (v in voices) if (v.active) v.render(busL, busR, pos, end)
            pos = end
        }
        clock += frames
        masterChain(frames, out)
    }

    private fun stepLen() = SR * 60.0 / p.bpm / 4.0

    private fun fireStep() {
        val s = (stepCount % NUM_STEPS).toInt()
        val pat = p.patterns[p.pattern]
        val len = stepLen()
        for (c in 0 until NUM_PADS) {
            if (pat[c][s] && !p.channels[c].muted) startVoice(c, len.toInt())
        }
        uiStep = s
        stepCount++
        gridTime += len
        // свинг сдвигает каждую вторую шестнадцатую
        nextFire = gridTime + if (stepCount % 2 == 1L) p.swing * 0.4 * len else 0.0
    }

    private fun alloc(): Voice {
        var oldest = voices[0]
        for (v in voices) {
            if (!v.active) return v
            if (v.age > oldest.age) oldest = v
        }
        return oldest
    }

    private fun startVoice(ci: Int, autoRelease: Int) {
        val ch = p.channels[ci]
        val smp = ch.sample ?: return
        if (smp.data.size < 4) return
        if (ch.mode == PlayMode.MUTE_GROUP) {
            for (v in voices) if (v.active && v.pad >= 0 && v.groupMode && v.group == ch.group) v.startFade(0.006f)
        } else if (ch.mode == PlayMode.GATE) {
            for (v in voices) if (v.active && v.pad == ci) v.startFade(0.006f)
        }
        val v = alloc()
        val full = (smp.data.size - 1).toDouble()
        val dec = ch.decay
        v.data = smp.data
        v.pad = ci
        v.groupMode = ch.mode == PlayMode.MUTE_GROUP
        v.group = ch.group
        v.pos = 0.0
        v.rate = Math.pow(2.0, ch.pitch / 12.0)
        v.limit = if (dec >= 0.999f) full else min(full, (0.02 + dec * smp.data.size / SR.toDouble()) * SR)
        val fadeLen = if (dec >= 0.999f) min(240.0, v.limit) else v.limit * 0.6
        v.fadeStart = v.limit - kotlin.math.max(fadeLen, 1.0)
        val a = ch.attack
        v.atkFrames = (a * a * 0.4 * SR).toInt()
        v.age = 0
        applyGain(v, ch.volume, ch.pan)
        v.fading = false; v.fade = 1f
        v.autoRel = if (ch.mode == PlayMode.GATE) autoRelease else 0
        v.active = true
        hits.incrementAndGet(ci)
    }

    private fun applyGain(v: Voice, vol: Float, pan: Float) {
        val ang = (pan.coerceIn(-1f, 1f) + 1f) * (PI / 4).toFloat()
        v.gl = cos(ang) * 1.4142f * vol
        v.gr = sin(ang) * 1.4142f * vol
    }

    private fun releasePad(ci: Int) {
        if (p.channels[ci].mode != PlayMode.GATE) return
        for (v in voices) if (v.active && v.pad == ci) v.startFade(0.012f)
    }

    private fun startPreview(s: Sample) {
        for (v in voices) if (v.active && v.pad == -1) v.startFade(0.01f)
        if (s.data.size < 4) return
        val v = alloc()
        v.data = s.data; v.pad = -1; v.groupMode = false; v.group = 0
        v.pos = 0.0; v.rate = 1.0
        v.limit = (s.data.size - 1).toDouble(); v.fadeStart = v.limit - 240.0
        if (v.fadeStart < 0) v.fadeStart = v.limit * 0.5
        v.atkFrames = 0; v.age = 0
        applyGain(v, 0.9f, 0f)
        v.fading = false; v.fade = 1f; v.autoRel = 0
        v.active = true
    }

    // ---------- мастер ----------
    private fun updateFilter(m: MasterFx) {
        val f = m.filter; val res = m.resonance
        if (f == lastFilter && res == lastRes) return
        lastFilter = f; lastRes = res
        val q = 0.7071 + res * 9.0
        when {
            f < 0.48f -> {          // Low-pass: 18 кГц -> 80 Гц
                val x = (0.5 - f) / 0.5
                filtL.lowpass(18000.0 * Math.pow(80.0 / 18000.0, x), q)
                filterOn = true
            }
            f > 0.52f -> {          // High-pass: 20 Гц -> 8 кГц
                val x = (f - 0.5) / 0.5
                filtL.highpass(20.0 * Math.pow(8000.0 / 20.0, x), q)
                filterOn = true
            }
            else -> filterOn = false
        }
        filtR.copyFrom(filtL)
    }

    private fun masterChain(frames: Int, out: FloatArray) {
        val m = p.master
        val wantLoop = m.loopActive && !offline
        if (wantLoop && !looping) {
            val beats = m.loopBeats
            loopLen = (SR * 60.0 / p.bpm * beats).toInt().coerceIn(64, RING - 1)
            loopStart = ((wIdx - loopLen) % RING + RING) % RING
            loopPos = 0
            looping = true
        } else if (!wantLoop && looping) looping = false

        updateFilter(m)
        val gl = m.isoLow; val gm = m.isoMid; val gh = m.isoHigh
        val isoOn = gl < 0.999f || gm < 0.999f || gh < 0.999f
        val crush = m.crush
        val vin = m.vinyl
        val rvMix = m.reverbMix
        val rvSize = m.reverbSize
        val vol = m.volume * 1.2f

        for (i in 0 until frames) {
            var l = busL[i]
            var r = busR[i]

            // DJFX Looper
            if (looping) {
                val idx = (loopStart + loopPos) % RING
                l = ringL[idx]; r = ringR[idx]
                if (++loopPos >= loopLen) loopPos = 0
            } else {
                ringL[wIdx] = l; ringR[wIdx] = r
                if (++wIdx >= RING) wIdx = 0
            }

            if (isoOn) {
                l = isolator.process(0, l, gl, gm, gh)
                r = isolator.process(1, r, gl, gm, gh)
            }
            if (filterOn) { l = filtL.process(l); r = filtR.process(r) }
            if (crush > 0.01f) { crusher.process(l, r, crush); l = crusher.outL; r = crusher.outR }
            if (vin > 0.01f) { vinyl.process(l, r, vin); l = vinyl.outL; r = vinyl.outR }
            if (rvMix > 0.01f) {
                reverb.process(l, r, rvSize)
                l += reverb.wetL * rvMix
                r += reverb.wetR * rvMix
            }
            limiter.process(l * vol, r * vol)
            out[2 * i] = limiter.outL
            out[2 * i + 1] = limiter.outR
        }
    }
}

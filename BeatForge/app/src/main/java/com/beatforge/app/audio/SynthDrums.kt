package com.beatforge.app.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/** Математический синтез ударных. Всё генерируется при старте — внешние файлы не нужны. */
object SynthDrums {
    val NAMES = listOf(
        "Kick", "Kick 808", "Snare", "Clap",
        "HH Closed", "HH Open", "Rim", "Tom Low",
        "Tom Hi", "Conga", "Cowbell", "Shaker",
        "808 Bass", "Bass Short", "Crash", "Zap FX"
    )

    private const val TWO_PI = 2.0 * PI
    private val rnd = Random(20240607L)
    private fun noise() = rnd.nextDouble() * 2.0 - 1.0
    private fun frames(sec: Double) = (sec * SR).toInt()

    private fun gen(sec: Double, f: (t: Double, i: Int) -> Double): FloatArray {
        val a = FloatArray(frames(sec))
        for (i in a.indices) a[i] = f(i / SR.toDouble(), i).toFloat()
        return a
    }

    private fun lowpass(x: FloatArray, fc: Double): FloatArray {
        val a = (1.0 - exp(-TWO_PI * fc / SR)).toFloat()
        var y = 0f
        return FloatArray(x.size) { y += a * (x[it] - y); y }
    }

    private fun highpass(x: FloatArray, fc: Double): FloatArray {
        val lp = lowpass(x, fc)
        return FloatArray(x.size) { x[it] - lp[it] }
    }

    private fun square(phase: Double) = if (phase - Math.floor(phase) < 0.5) 1.0 else -1.0

    private fun finish(x: FloatArray, peak: Float = 0.9f): FloatArray {
        var m = 0f
        for (v in x) m = max(m, abs(v))
        if (m > 0f) { val g = peak / m; for (i in x.indices) x[i] *= g }
        val fade = min(x.size, frames(0.004))
        for (i in 0 until fade) x[x.size - 1 - i] *= i / fade.toFloat()
        return x
    }

    private fun kick(f0: Double, fSweep: Double, k: Double, dec: Double, len: Double, drive: Double): FloatArray {
        var ph = 0.0
        return finish(gen(len) { t, _ ->
            ph += TWO_PI * (f0 + fSweep * exp(-t * k)) / SR
            val s = sin(ph) * exp(-t * dec) + noise() * exp(-t * 700.0) * 0.25
            tanh(s * drive)
        })
    }

    private fun tom(base: Double, dec: Double, len: Double): FloatArray {
        var ph = 0.0
        return finish(gen(len) { t, _ ->
            ph += TWO_PI * base * (1.0 + 0.7 * exp(-t * 22.0)) / SR
            sin(ph) * exp(-t * dec) + noise() * exp(-t * 400.0) * 0.1
        })
    }

    private fun snare(): FloatArray {
        val n = highpass(FloatArray(frames(0.35)) { noise().toFloat() }, 1800.0)
        var p1 = 0.0; var p2 = 0.0
        return finish(gen(0.35) { t, i ->
            p1 += TWO_PI * (185.0 + 60.0 * exp(-t * 60.0)) / SR
            p2 += TWO_PI * 330.0 / SR
            sin(p1) * exp(-t * 22.0) * 0.6 + sin(p2) * exp(-t * 32.0) * 0.25 + n[i] * exp(-t * 15.0) * 0.9
        })
    }

    private fun clap(): FloatArray {
        val n = lowpass(highpass(FloatArray(frames(0.4)) { noise().toFloat() }, 700.0), 4000.0)
        val bursts = doubleArrayOf(0.0, 0.011, 0.022)
        return finish(gen(0.4) { t, i ->
            var e = 0.0
            for (b in bursts) if (t >= b) e = max(e, exp(-(t - b) * 230.0))
            if (t >= 0.033) e = max(e, 0.7 * exp(-(t - 0.033) * 26.0))
            n[i] * e
        })
    }

    private val HAT_FREQS = doubleArrayOf(205.3, 304.4, 369.6, 522.7, 540.0, 800.0)

    private fun hat(len: Double, dec: Double): FloatArray {
        val raw = gen(len) { t, _ ->
            var s = 0.0
            for (f in HAT_FREQS) s += square(t * f * 2.0)
            s / 6.0
        }
        val hp = highpass(highpass(raw, 6500.0), 6500.0)
        return finish(FloatArray(hp.size) { hp[it] * exp(-(it / SR.toDouble()) * dec).toFloat() })
    }

    private fun rim(): FloatArray = finish(gen(0.12) { t, _ ->
        (sin(TWO_PI * 1750.0 * t) * 0.6 + sin(TWO_PI * 480.0 * t) * 0.6) * exp(-t * 85.0) +
            noise() * exp(-t * 900.0) * 0.4
    })

    private fun conga(): FloatArray {
        var ph = 0.0
        return finish(gen(0.4) { t, _ ->
            ph += TWO_PI * 330.0 * (1.0 + 0.25 * exp(-t * 40.0)) / SR
            sin(ph) * exp(-t * 13.0) + noise() * exp(-t * 500.0) * 0.15
        })
    }

    private fun cowbell(): FloatArray {
        val raw = gen(0.5) { t, _ -> (square(t * 540.0) + square(t * 800.0)) * 0.5 }
        val f = lowpass(highpass(raw, 500.0), 5500.0)
        return finish(FloatArray(f.size) {
            val t = it / SR.toDouble()
            f[it] * (0.6 * exp(-t * 60.0) + 0.4 * exp(-t * 8.0)).toFloat()
        })
    }

    private fun shaker(): FloatArray {
        val n = highpass(FloatArray(frames(0.25)) { noise().toFloat() }, 5000.0)
        return finish(FloatArray(n.size) {
            val t = it / SR.toDouble()
            n[it] * (min(t / 0.015, 1.0) * exp(-t * 26.0)).toFloat()
        })
    }

    private fun bass808(f0: Double, dec: Double, len: Double): FloatArray {
        var ph = 0.0
        return finish(gen(len) { t, _ ->
            ph += TWO_PI * f0 * (1.0 + 0.35 * exp(-t * 35.0)) / SR
            val env = min(t / 0.004, 1.0) * exp(-t * dec)
            tanh(sin(ph) * env * 2.0) * 0.9 + sin(ph * 2.0) * env * 0.05
        })
    }

    private fun crash(): FloatArray {
        val raw = gen(1.8) { t, _ ->
            var s = noise() * 0.7
            for (f in HAT_FREQS) s += square(t * f * 3.1) * 0.08
            s
        }
        val hp = highpass(raw, 4000.0)
        return finish(FloatArray(hp.size) {
            val t = it / SR.toDouble()
            hp[it] * (min(t / 0.003, 1.0) * exp(-t * 2.6)).toFloat()
        })
    }

    private fun zap(): FloatArray {
        var ph = 0.0
        return finish(gen(0.5) { t, _ ->
            ph += TWO_PI * (120.0 + 3000.0 * exp(-t * 9.0)) / SR
            (sin(ph) + noise() * 0.12) * exp(-t * 6.0)
        })
    }

    fun make(i: Int): Sample {
        val data = when (i) {
            0 -> kick(48.0, 130.0, 35.0, 6.5, 0.5, 1.4)
            1 -> kick(42.0, 90.0, 18.0, 3.2, 1.1, 2.0)
            2 -> snare()
            3 -> clap()
            4 -> hat(0.12, 60.0)
            5 -> hat(0.6, 9.0)
            6 -> rim()
            7 -> tom(110.0, 8.0, 0.55)
            8 -> tom(200.0, 10.0, 0.45)
            9 -> conga()
            10 -> cowbell()
            11 -> shaker()
            12 -> bass808(41.2, 1.7, 1.6)
            13 -> bass808(55.0, 6.0, 0.6)
            14 -> crash()
            else -> zap()
        }
        return Sample(NAMES[i], data)
    }
}

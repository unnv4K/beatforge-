package com.beatforge.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tanh

/** Биквад (RBJ cookbook), transposed direct form II. */
class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun lowpass(f: Double, q: Double) = set(f, q, false)
    fun highpass(f: Double, q: Double) = set(f, q, true)

    private fun set(f: Double, q: Double, hp: Boolean) {
        val fc = f.coerceIn(10.0, SR * 0.45)
        val w = 2.0 * PI * fc / SR
        val cw = cos(w)
        val alpha = sin(w) / (2.0 * q)
        val a0 = 1.0 + alpha
        val nb0: Double; val nb1: Double; val nb2: Double
        if (hp) {
            nb0 = (1 + cw) / 2; nb1 = -(1 + cw); nb2 = (1 + cw) / 2
        } else {
            nb0 = (1 - cw) / 2; nb1 = 1 - cw; nb2 = (1 - cw) / 2
        }
        b0 = (nb0 / a0).toFloat(); b1 = (nb1 / a0).toFloat(); b2 = (nb2 / a0).toFloat()
        a1 = (-2 * cw / a0).toFloat(); a2 = ((1 - alpha) / a0).toFloat()
    }

    fun copyFrom(o: Biquad) { b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2 }
}

/** Изолятор: 3 полосы (Linkwitz-Riley 4-го порядка на 200 Гц и 2.5 кГц). */
class Isolator {
    private class Split(f: Double) {
        val lpA = Biquad(); val lpB = Biquad(); val hpA = Biquad(); val hpB = Biquad()
        init {
            lpA.lowpass(f, 0.7071); lpB.lowpass(f, 0.7071)
            hpA.highpass(f, 0.7071); hpB.highpass(f, 0.7071)
        }
        fun low(x: Float) = lpB.process(lpA.process(x))
        fun high(x: Float) = hpB.process(hpA.process(x))
    }

    private val s1 = Array(2) { Split(200.0) }
    private val s2 = Array(2) { Split(2500.0) }

    fun process(ch: Int, x: Float, gl: Float, gm: Float, gh: Float): Float {
        val low = s1[ch].low(x)
        val rest = s1[ch].high(x)
        val mid = s2[ch].low(rest)
        val high = s2[ch].high(rest)
        return low * gl + mid * gm + high * gh
    }
}

class Bitcrusher {
    private var cnt = 0f
    private var hl = 0f
    private var hr = 0f
    var outL = 0f; private set
    var outR = 0f; private set

    fun process(l: Float, r: Float, amount: Float) {
        val bits = 16f - 12f * amount
        val step = Math.pow(2.0, (bits - 1f).toDouble()).toFloat()
        val factor = 1f + 15f * amount * amount
        cnt += 1f
        if (cnt >= factor) {
            cnt -= factor
            hl = round(l * step) / step
            hr = round(r * step) / step
        }
        outL = hl; outR = hr
    }
}

/** Freeverb (Jezar): 8 comb + 4 allpass на канал. */
class Reverb {
    private class Comb(size: Int) {
        val buf = FloatArray(size); var idx = 0; var store = 0f
        fun process(x: Float, fb: Float, damp: Float): Float {
            val out = buf[idx]
            store = out * (1f - damp) + store * damp
            buf[idx] = x + store * fb
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private class Allpass(size: Int) {
        val buf = FloatArray(size); var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            val out = -x + b
            buf[idx] = x + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private val combT = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val apT = intArrayOf(556, 441, 341, 225)
    private fun sc(n: Int) = max(8, (n * SR / 44100.0).toInt())

    private val combL = Array(8) { Comb(sc(combT[it])) }
    private val combR = Array(8) { Comb(sc(combT[it] + 23)) }
    private val apL = Array(4) { Allpass(sc(apT[it])) }
    private val apR = Array(4) { Allpass(sc(apT[it] + 23)) }

    var wetL = 0f; private set
    var wetR = 0f; private set

    fun process(l: Float, r: Float, size: Float) {
        val fb = 0.70f + 0.26f * size
        val damp = 0.35f
        val input = (l + r) * 0.015f
        var sl = 0f; var sr = 0f
        for (c in combL) sl += c.process(input, fb, damp)
        for (c in combR) sr += c.process(input, fb, damp)
        for (a in apL) sl = a.process(sl)
        for (a in apR) sr = a.process(sr)
        wetL = sl * 3f; wetR = sr * 3f
    }
}

/** Винил: компрессор + сатурация + мягкий ВЧ-срез + шум и треск. */
class Vinyl {
    private var env = 0f
    private var lpL = 0f
    private var lpR = 0f
    private var crackle = 0f
    private var seed = 12345
    private val atk = 1f - exp(-1.0 / (0.005 * SR)).toFloat()
    private val rel = 1f - exp(-1.0 / (0.12 * SR)).toFloat()

    private fun rand(): Float {   // xorshift -> -1..1
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return seed / 2147483648f
    }

    var outL = 0f; private set
    var outR = 0f; private set

    fun process(l: Float, r: Float, amt: Float) {
        // компрессор (feed-forward, порог 0.3, ratio 4)
        val lvl = max(abs(l), abs(r))
        env += (lvl - env) * (if (lvl > env) atk else rel)
        val thr = 0.3f
        val g = if (env > thr) (thr + (env - thr) / (1f + 3f * amt)) / env else 1f
        val makeup = 1f + 0.5f * amt
        var xl = l * g * makeup
        var xr = r * g * makeup
        // сатурация
        val drive = 1f + amt * 2.5f
        val norm = tanh(drive)
        xl = tanh(xl * drive) / norm
        xr = tanh(xr * drive) / norm
        // «приглушённый» верх
        val a = 1f - exp(-2.0 * PI * (16000.0 - 11000.0 * amt) / SR).toFloat()
        lpL += a * (xl - lpL); lpR += a * (xr - lpR)
        // шум + треск
        if (abs(rand()) < amt * 0.00005f) crackle = (0.15f + 0.35f * abs(rand())) * (if (rand() > 0f) 1f else -1f)
        crackle *= 0.88f
        val hiss = rand() * 0.004f * amt
        val n = hiss + crackle * amt
        outL = lpL + n
        outR = lpR + n * 0.8f
    }
}

class Limiter {
    private var gain = 1f
    private val ceil = 0.97f
    private val relStep = (1.0 / (0.08 * SR)).toFloat()
    var outL = 0f; private set
    var outR = 0f; private set

    fun process(l: Float, r: Float) {
        val peak = max(abs(l), abs(r))
        val target = if (peak > ceil) ceil / peak else 1f
        gain = if (target < gain) target else min(1f, gain + relStep)
        outL = (l * gain).coerceIn(-1f, 1f)
        outR = (r * gain).coerceIn(-1f, 1f)
    }
}

package com.beatforge.app.audio

const val SR = 48000
const val NUM_PADS = 16
const val NUM_STEPS = 16
const val NUM_PATTERNS = 4

enum class PlayMode(val label: String) {
    TRIGGER("Trigger"),
    GATE("Gate"),
    MUTE_GROUP("Mute Group")
}

/** Моно-сэмпл, уже приведённый к частоте движка SR. */
class Sample(val name: String, val data: FloatArray)

/** Канал = пэд. Все поля читаются аудио-потоком, поэтому @Volatile. */
class Channel(val index: Int) {
    @Volatile var sample: Sample? = null
    @Volatile var volume = 0.85f      // 0..1
    @Volatile var pan = 0f            // -1..1
    @Volatile var pitch = 0f          // полутона -12..+12
    @Volatile var attack = 0f         // 0..1 (0..400 мс, нелинейно)
    @Volatile var decay = 1f          // 0..1 (1 = играть целиком)
    @Volatile var mode = PlayMode.TRIGGER
    @Volatile var group = 1           // 1..4 (для Mute Group)
    @Volatile var muted = false       // влияет только на секвенсор
}

class MasterFx {
    @Volatile var volume = 0.85f
    @Volatile var filter = 0.5f       // 0..1: <0.5 Low-pass, >0.5 High-pass, 0.5 = выкл
    @Volatile var resonance = 0.15f   // 0..1
    @Volatile var crush = 0f          // 0..1
    @Volatile var reverbMix = 0f      // 0..1
    @Volatile var reverbSize = 0.5f   // 0..1
    @Volatile var vinyl = 0f          // 0..1
    @Volatile var isoLow = 1f         // 0..1 (0 = вырезано)
    @Volatile var isoMid = 1f
    @Volatile var isoHigh = 1f
    @Volatile var loopActive = false  // DJFX Looper
    @Volatile var loopBeats = 0.25f   // длина петли в долях (1/4 доли = 1/16 такта)
}

object Pads {
    const val KICK = 0
    const val KICK2 = 1
    const val SNARE = 2
    const val CLAP = 3
    const val CHH = 4
    const val OHH = 5
    const val RIM = 6
    const val TOM_LO = 7
    const val TOM_HI = 8
    const val CONGA = 9
    const val COWBELL = 10
    const val SHAKER = 11
    const val BASS = 12
    const val BASS_SHORT = 13
    const val CRASH = 14
    const val ZAP = 15
}

class Project {
    val channels = Array(NUM_PADS) { Channel(it) }
    val patterns = Array(NUM_PATTERNS) { Array(NUM_PADS) { BooleanArray(NUM_STEPS) } }
    @Volatile var pattern = 0
    @Volatile var bpm = 90
    @Volatile var swing = 0f          // 0..1
    val master = MasterFx()

    fun loadDefaultSounds() {
        for (i in 0 until NUM_PADS) channels[i].sample = SynthDrums.make(i)
        channels[Pads.CHH].apply { mode = PlayMode.MUTE_GROUP; group = 1 }
        channels[Pads.OHH].apply { mode = PlayMode.MUTE_GROUP; group = 1 }   // закрытый глушит открытый
        channels[Pads.BASS].apply { mode = PlayMode.MUTE_GROUP; group = 2 }
        channels[Pads.BASS_SHORT].apply { mode = PlayMode.MUTE_GROUP; group = 2 }
        channels[Pads.BASS].volume = 0.9f
        channels[Pads.SHAKER].volume = 0.55f
        channels[Pads.CHH].volume = 0.65f
        channels[Pads.OHH].volume = 0.6f
        channels[Pads.CLAP].pan = 0.1f
        channels[Pads.TOM_LO].pan = -0.3f
        channels[Pads.TOM_HI].pan = 0.3f
    }

    fun clearPattern(p: Int) { for (row in patterns[p]) row.fill(false) }

    private fun steps(p: Int, pad: Int, vararg s: Int) {
        val row = patterns[p][pad]; row.fill(false); for (x in s) row[x] = true
    }

    /** 0 = Boom Bap, 1 = Trap, 2 = House */
    fun loadPreset(which: Int, p: Int) {
        clearPattern(p)
        when (which) {
            0 -> {
                bpm = 90
                steps(p, Pads.KICK, 0, 7, 10)
                steps(p, Pads.SNARE, 4, 12)
                steps(p, Pads.CHH, 0, 2, 4, 6, 8, 10, 12)
                steps(p, Pads.OHH, 14)
                steps(p, Pads.SHAKER, 3, 11)
            }
            1 -> {
                bpm = 140
                steps(p, Pads.KICK, 0, 3, 10)
                steps(p, Pads.BASS, 0, 10)
                steps(p, Pads.CLAP, 8)
                steps(p, Pads.SNARE, 8)
                steps(p, Pads.CHH, 0, 2, 4, 6, 8, 10, 12, 13, 14, 15)
                steps(p, Pads.OHH, 7)
            }
            else -> {
                bpm = 124
                steps(p, Pads.KICK, 0, 4, 8, 12)
                steps(p, Pads.CLAP, 4, 12)
                steps(p, Pads.OHH, 2, 6, 10, 14)
                steps(p, Pads.CHH, 0, 1, 4, 5, 8, 9, 12, 13)
                steps(p, Pads.SHAKER, 1, 3, 5, 7, 9, 11, 13, 15)
            }
        }
        channels[Pads.CLAP].muted = false
    }
}

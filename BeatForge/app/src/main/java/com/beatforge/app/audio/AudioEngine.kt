package com.beatforge.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlin.math.max

/**
 * Низколатентный вывод: AudioTrack (PCM_FLOAT, PERFORMANCE_MODE_LOW_LATENCY) c буфером в 2 «burst»
 * устройства и блоком записи = 1 burst. Буфер автоматически растёт, если случаются underrun-ы.
 */
class AudioEngine(project: Project) {
    val mixer = Mixer(project)
    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null

    /** Оценка задержки вывода в мс (для отладки/UI). */
    @Volatile var latencyMs = 0

    fun setPaused(p: Boolean) { paused = p }

    fun start(ctx: Context) {
        if (running) return
        running = true
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            ?.coerceIn(64, 1024) ?: 256

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
                )
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuf, burst * 2 * 8))
                .build()

            var target = burst * 2
            track.bufferSizeInFrames = target
            val maxTarget = burst * 8
            var lastUnderruns = 0
            val buf = FloatArray(burst * 2)
            track.play()
            var counter = 0
            var wasPaused = false
            try {
                while (running) {
                    if (paused) {
                        if (!wasPaused) { track.pause(); track.flush(); wasPaused = true }
                        Thread.sleep(20); continue
                    }
                    if (wasPaused) { track.play(); wasPaused = false }
                    mixer.render(buf, burst)
                    track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                    if (++counter % 64 == 0) {
                        val u = track.underrunCount
                        if (u > lastUnderruns && target < maxTarget) {
                            target += burst
                            track.bufferSizeInFrames = target
                        }
                        lastUnderruns = u
                        latencyMs = (target * 1000f / SR).toInt()
                    }
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }, "BeatForge-Audio").also { it.start() }
    }

    fun release() {
        running = false
        thread?.join(500)
        thread = null
    }
}

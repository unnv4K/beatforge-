package com.beatforge.app.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.math.abs
import kotlin.math.max

/** Держит MediaProjection, пока работает CaptureService. */
object CaptureHolder {
    @Volatile var projection: MediaProjection? = null
    fun release() { runCatching { projection?.stop() }; projection = null }
}

class CaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CaptureHolder.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Захват звука", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH)
            .setContentTitle("BeatForge")
            .setContentText("Идёт захват системного звука")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java)
        else intent?.getParcelableExtra("data")
        if (data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { CaptureHolder.projection = null }
            }, Handler(Looper.getMainLooper()))
            CaptureHolder.projection = proj
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.beatforge.app.STOP_CAPTURE"
        private const val CH = "capture"
    }
}

/** Запись с микрофона или системного звука (AudioPlaybackCapture) в моно-сэмпл. */
class Recorder {
    @Volatile var level = 0f
    @Volatile private var recording = false
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    private val buf = FloatBuf(SR * 4)
    private val inRate = 44100
    private var channels = 1

    @SuppressLint("MissingPermission")
    fun start(projection: MediaProjection?, maxSeconds: Int = 20): Boolean {
        if (recording) return false
        val ch = if (projection != null) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        channels = if (projection != null) 2 else 1
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(inRate).setChannelMask(ch).build()
        val minBuf = AudioRecord.getMinBufferSize(inRate, ch, AudioFormat.ENCODING_PCM_16BIT)
        val b = AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(max(minBuf * 2, 8192))
        if (projection != null) {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            b.setAudioPlaybackCaptureConfig(cfg)
        } else {
            b.setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        val r = try { b.build() } catch (e: Exception) { return false }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r
        buf.size = 0
        recording = true
        r.startRecording()
        val limit = inRate * maxSeconds
        thread = Thread {
            val s = ShortArray(1024)
            while (recording && buf.size < limit) {
                val n = r.read(s, 0, s.size)
                if (n <= 0) continue
                var peak = 0f
                var i = 0
                while (i + channels <= n) {
                    var sum = 0f
                    for (c in 0 until channels) sum += s[i + c]
                    val v = sum / channels / 32768f
                    buf.add(v)
                    peak = max(peak, abs(v))
                    i += channels
                }
                level = peak
            }
            recording = false
        }.also { it.start() }
        return true
    }

    val isRecording get() = recording

    /** Останавливает запись и возвращает сэмпл (обрезка тишины по краям, нормализация). */
    fun stop(): Sample? {
        recording = false
        thread?.join(1500)
        rec?.let { runCatching { it.stop() }; it.release() }
        rec = null
        level = 0f
        var d = buf.toArray()
        if (d.size < 512) return null
        var a = 0
        var e = d.size - 1
        while (a < e && abs(d[a]) < 0.01f) a++
        while (e > a && abs(d[e]) < 0.01f) e--
        d = d.copyOfRange(a, e + 1)
        var pk = 0f
        for (v in d) pk = max(pk, abs(v))
        if (pk > 0f) { val g = 0.9f / pk; for (i in d.indices) d[i] *= g }
        return Sample("Rec", SampleIO.resample(d, inRate))
    }
}

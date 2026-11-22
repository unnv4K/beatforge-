package com.beatforge.app.audio

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class FloatBuf(cap: Int = 1 shl 16) {
    var a = FloatArray(cap); var size = 0
    fun add(v: Float) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }
    fun toArray() = a.copyOf(size)
}

object SampleIO {

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0).substringBeforeLast('.')
            }
        }
        return "Sample"
    }

    fun resample(x: FloatArray, from: Int): FloatArray {
        if (from == SR || x.isEmpty()) return x
        val ratio = from.toDouble() / SR
        val n = (x.size / ratio).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val p = i * ratio
            val ip = p.toInt()
            val f = (p - ip).toFloat()
            val a = x[ip]
            val b = if (ip + 1 < x.size) x[ip + 1] else a
            out[i] = a + (b - a) * f
        }
        return out
    }

    /** Декодирует любой аудиофайл (WAV/MP3/AAC/OGG/FLAC...) в моно-float на частоте SR. */
    fun decode(ctx: Context, uri: Uri, maxSeconds: Int = 180): Sample {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; fmt = f; break }
        }
        require(track >= 0 && fmt != null) { "В файле нет аудиодорожки" }
        ex.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()

        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        val out = FloatBuf()
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        try {
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    val buf = codec.getOutputBuffer(oi)!!
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    if (isFloat) {
                        val fb = buf.asFloatBuffer()
                        while (fb.remaining() >= ch) {
                            var s = 0f
                            for (c in 0 until ch) s += fb.get()
                            out.add(s / ch)
                        }
                    } else {
                        val sb = buf.asShortBuffer()
                        while (sb.remaining() >= ch) {
                            var s = 0f
                            for (c in 0 until ch) s += sb.get()
                            out.add(s / ch / 32768f)
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                if (out.size > rate.toLong() * maxSeconds) break
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        require(out.size > 0) { "Не удалось декодировать файл" }
        return Sample(displayName(ctx, uri), resample(out.toArray(), rate))
    }

    // ---------- WAV ----------
    private fun le(v: Int, n: Int): ByteArray = ByteArray(n) { ((v shr (8 * it)) and 0xFF).toByte() }

    fun writeWavHeader(os: OutputStream, frames: Int) {
        val dataBytes = frames * 4
        os.write("RIFF".toByteArray()); os.write(le(36 + dataBytes, 4))
        os.write("WAVE".toByteArray()); os.write("fmt ".toByteArray())
        os.write(le(16, 4)); os.write(le(1, 2)); os.write(le(2, 2))
        os.write(le(SR, 4)); os.write(le(SR * 4, 4)); os.write(le(4, 2)); os.write(le(16, 2))
        os.write("data".toByteArray()); os.write(le(dataBytes, 4))
    }

    /** Офлайн-рендер выбранного паттерна (bars тактов) + хвост реверба в Downloads/BeatForge_*.wav. */
    fun exportWav(ctx: Context, project: Project, bars: Int, onProgress: (Float) -> Unit): String {
        val mixer = Mixer(project, offline = true)
        mixer.playRequested = true
        val stepLen = SR * 60.0 / project.bpm / 4.0
        val loopFrames = (stepLen * NUM_STEPS * bars).roundToInt()
        val total = loopFrames + SR * 3

        val name = "BeatForge_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: error("Не удалось создать файл в Downloads")
        try {
            resolver.openOutputStream(uri)!!.buffered().use { os ->
                writeWavHeader(os, total)
                val block = 1024
                val buf = FloatArray(block * 2)
                val bytes = ByteArray(block * 4)
                var done = 0
                while (done < total) {
                    var n = min(block, total - done)
                    if (done >= loopFrames) mixer.playRequested = false
                    else n = min(n, loopFrames - done)
                    mixer.render(buf, n)
                    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n * 2) bb.putShort((buf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    os.write(bytes, 0, n * 4)
                    done += n
                    onProgress(done / total.toFloat())
                }
            }
            cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return name
    }
}

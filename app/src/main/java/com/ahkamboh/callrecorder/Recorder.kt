package com.ahkamboh.callrecorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recording: a MediaRecorder writing AAC/M4A straight into MediaStore under
 * Music/CallRecordings/<number>/, so the files show up in any Files or music app
 * without storage permissions. The number is only known after the call, so the
 * file starts life in the base folder and is moved into <number>/ by [finish].
 */
class Recorder(private val ctx: Context) {

    private val resolver = ctx.contentResolver
    private val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    private var recorder: MediaRecorder? = null
    private var uri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null

    var sourceName: String = ""
        private set

    /** Tries the preferred audio source first, then the others. */
    fun start(preferred: Int): Boolean {
        val order = listOf(preferred) + SOURCES.keys.filter { it != preferred }
        for (source in order) {
            if (tryStart(source)) {
                sourceName = SOURCES[source] ?: source.toString()
                Log.i(TAG, "recording with source $sourceName")
                return true
            }
        }
        return false
    }

    private fun tryStart(source: Int): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Call_$stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            // Base folder until the number is known; an unknown number simply stays here.
            put(MediaStore.Audio.Media.RELATIVE_PATH, basePath())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val u = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val fd = resolver.openFileDescriptor(u, "w")
        if (fd == null) {
            resolver.delete(u, null, null)
            return false
        }
        val r = MediaRecorder(ctx)
        return try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(fd.fileDescriptor)
            r.prepare()
            r.start()
            recorder = r
            uri = u
            pfd = fd
            true
        } catch (e: Exception) {
            Log.w(TAG, "source $source failed", e)
            r.release()
            fd.close()
            resolver.delete(u, null, null)
            false
        }
    }

    /** Stops the encoder. A recording with no data (instant hang-up) is discarded. */
    fun stop() {
        val r = recorder ?: return
        recorder = null
        val hasData = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "stop produced no data", e)
            false
        }
        r.release()
        pfd?.close()
        pfd = null
        if (!hasData) discard()
    }

    /** Stops if needed and deletes the file. Used when the number turns out to be excluded. */
    fun discard() {
        if (recorder != null) stop()
        uri?.let { resolver.delete(it, null, null) }
        uri = null
    }

    /** Publishes the file and, when the call log gave us a number, moves it into that number's folder. */
    fun finish(call: CallInfo?) {
        val u = uri ?: return
        try {
            resolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "publish failed", e)
            return
        }
        if (call == null) return
        val name = "Call_${stamp}_${call.directionTag}_${call.safeNumber}.m4a"
        val move = ContentValues().apply {
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath(call.safeNumber))
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
        }
        try {
            resolver.update(u, move, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "move failed, keeping file in unknown folder", e)
            try {
                resolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, name) }, null, null)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "CallRecorder"
        const val FOLDER = "CallRecordings"
        const val UNKNOWN = "unknown"

        fun basePath() = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"
        fun relativePath(sub: String) = basePath() + sub + "/"

        /** Display names for the sources worth trying without root. */
        val SOURCES: Map<Int, String> = linkedMapOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "Voice recognition",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Voice communication",
            MediaRecorder.AudioSource.MIC to "Microphone",
        )
    }
}

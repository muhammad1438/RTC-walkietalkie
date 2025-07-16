package com.example.walkietalkie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SAMPLE_RATE = 44100
private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class AudioHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private var recorder: AudioRecord? = null
    private var tracker: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playingJob: Job? = null


    fun startRecording(outputStream: java.io.OutputStream) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize
        )

        recorder?.startRecording()

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (true) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    try {
                        outputStream.write(buffer, 0, read)
                    } catch (e: java.io.IOException) {
                        break
                    }
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    fun startPlaying(inputStream: java.io.InputStream) {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        tracker = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        tracker?.play()

        playingJob = coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (true) {
                try {
                    val read = inputStream.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        tracker?.write(buffer, 0, read)
                    }
                } catch (e: java.io.IOException) {
                    break
                }
            }
        }
    }

    fun stopPlaying() {
        playingJob?.cancel()
        tracker?.stop()
        tracker?.release()
        tracker = null
    }
}

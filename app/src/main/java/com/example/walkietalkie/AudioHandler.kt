package com.example.walkietalkie

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder.AudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class AudioHandler(
    private val scope: CoroutineScope,
    private val onDataReady: (ByteArray) -> Unit
) {
    private val sampleRate = 44100
    private val channelConfigRecord = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigPlay = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigRecord, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playingJob: Job? = null

    fun startRecording() {
        if (recordingJob?.isActive == true) return
        audioRecord = AudioRecord(AudioSource.MIC, sampleRate, channelConfigRecord, audioFormat, bufferSize)
        audioRecord?.startRecording()
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    onDataReady(buffer.copyOf(read))
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun startPlaying() {
        if (playingJob?.isActive == true) return
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfigPlay, audioFormat, bufferSize, AudioTrack.MODE_STREAM)
        audioTrack?.play()
    }

    fun playAudio(data: ByteArray) {
        audioTrack?.write(data, 0, data.size)
    }

    fun stopPlaying() {
        playingJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun release() {
        stopRecording()
        stopPlaying()
    }
}

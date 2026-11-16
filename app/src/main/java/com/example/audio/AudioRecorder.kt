package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class AudioRecorder {
  private val recordLock = Any()
  private var audioRecord: AudioRecord? = null
  @Volatile private var isRecording = false
  private val sampleRate = 44100
  private val channelConfig = AudioFormat.CHANNEL_IN_MONO
  private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

  @SuppressLint("MissingPermission")
  fun startRecording(@Suppress("UNUSED_PARAMETER") onAmplitudeUpdate: (Float, Float) -> Unit = { _, _ -> }): Boolean {
    synchronized(recordLock) {
      if (isRecording) return true
      audioRecord?.let { stale ->
        try { stale.release() } catch (_: Exception) {}
        audioRecord = null
      }

      val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
        return false
      }

      val bufferSize = max(minBufferSize, sampleRate * 2)
      return try {
        val recorder = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          channelConfig,
          audioFormat,
          bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          recorder.release()
          return false
        }

        recorder.startRecording()
        audioRecord = recorder
        isRecording = true
        true
      } catch (e: Exception) {
        e.printStackTrace()
        audioRecord = null
        isRecording = false
        false
      }
    }
  }

  suspend fun recordAudio(
    maxDurationSec: Int = 15,
    onProgressUpdate: (Float, Float, FloatArray) -> Unit
  ): FloatArray = withContext(Dispatchers.IO) {
    val outputStream = ByteArrayOutputStream()
    val buffer = ShortArray(2048)
    val maxSamples = sampleRate * maxDurationSec
    var totalSamplesRead = 0

    try {
      while (isRecording && totalSamplesRead < maxSamples) {
        val activeRecord = synchronized(recordLock) { audioRecord } ?: break
        val read = try {
          activeRecord.read(buffer, 0, buffer.size)
        } catch (_: Exception) {
          -1
        }
        if (read > 0) {
          var sumSquares = 0.0
          var peak = 0.0f
          for (i in 0 until read) {
            val sampleShort = buffer[i]
            val sampleFloat = sampleShort / 32768.0f
            sumSquares += (sampleFloat * sampleFloat)
            if (abs(sampleFloat) > peak) peak = abs(sampleFloat)

            outputStream.write(sampleShort.toInt() and 0xFF)
            outputStream.write((sampleShort.toInt() shr 8) and 0xFF)
          }
          totalSamplesRead += read

          val rms = sqrt(sumSquares / read).toFloat()
          val db = if (rms > 0.00001f) (20 * log10(rms.toDouble())).toFloat() else -80f

          val subBlocks = 8
          val subBlockSize = read / subBlocks
          val blockPeaks = FloatArray(subBlocks)
          if (subBlockSize > 0) {
            for (b in 0 until subBlocks) {
              var bPeak = 0.0f
              val start = b * subBlockSize
              for (j in 0 until subBlockSize) {
                val s = abs(buffer[start + j] / 32768.0f)
                if (s > bPeak) bPeak = s
              }
              blockPeaks[b] = if (bPeak.isNaN()) 0.04f else bPeak.coerceIn(0.04f, 1f)
            }
          }

          onProgressUpdate(if (peak.isNaN()) 0f else peak.coerceIn(0f, 1f), db, blockPeaks)
        } else {
          break
        }
      }
    } finally {
      stopRecording()
    }

    val pcmBytes = outputStream.toByteArray()
    val floatArray = FloatArray(pcmBytes.size / 2)
    for (i in floatArray.indices) {
      val low = pcmBytes[i * 2].toInt() and 0xFF
      val high = pcmBytes[i * 2 + 1].toInt()
      val sample = (high shl 8) or low
      floatArray[i] = sample / 32768.0f
    }
    floatArray
  }

  fun stopRecording() {
    synchronized(recordLock) {
      isRecording = false
      val rec = audioRecord
      audioRecord = null
      if (rec != null) {
        try {
          if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            rec.stop()
          }
        } catch (_: Exception) {
        }
        try {
          rec.release()
        } catch (_: Exception) {
        }
      }
    }
  }

  fun isCurrentlyRecording(): Boolean = isRecording
}

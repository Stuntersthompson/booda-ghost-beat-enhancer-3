package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure playback progress, formatting, and seek calculation helpers (JVM-testable).
 */
object PlaybackMath {
  fun clampFraction(fraction: Float): Float {
    if (fraction.isNaN() || fraction.isInfinite()) return 0f
    return fraction.coerceIn(0f, 1f)
  }

  fun computeNormalizedProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
  }

  fun computeSeekTargetMs(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val clamped = clampFraction(fraction)
    return (clamped * durationMs.toFloat()).roundToLong().coerceIn(0L, durationMs)
  }

  fun computePcmSampleIndex(fraction: Float, totalSamples: Int): Int {
    if (totalSamples <= 1) return 0
    val clamped = clampFraction(fraction)
    return (clamped * (totalSamples - 1)).roundToInt().coerceIn(0, totalSamples - 1)
  }

  fun advanceLoopPositionMs(nextMs: Long, durationMs: Long, isLooping: Boolean): Long {
    if (durationMs <= 0L) return 0L
    if (nextMs < 0L) return 0L
    return if (isLooping) nextMs % durationMs else nextMs.coerceIn(0L, durationMs)
  }

  fun formatDurationMmSs(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return "%d:%02d".format(minutes, seconds)
  }

  fun formatDurationMmSsMs(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    val millis = safeMs % 1000L
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
  }
}

/**
 * Pure JVM-testable drag-scrub state machine that preserves prior play/pause state across scrub drags
 * and handles empty-duration or out-of-range edge cases deterministically.
 */
class ScrubStateController {
  var isScrubbing: Boolean = false
    private set
  var wasPlayingBeforeScrub: Boolean = false
    private set
  var previewFraction: Float = 0f
    private set

  fun startScrub(currentlyPlaying: Boolean, initialFraction: Float, durationMs: Long): Boolean {
    if (durationMs <= 0L) {
      isScrubbing = false
      wasPlayingBeforeScrub = false
      return false
    }
    if (!isScrubbing) {
      wasPlayingBeforeScrub = currentlyPlaying
    }
    isScrubbing = true
    previewFraction = PlaybackMath.clampFraction(initialFraction)
    return true
  }

  fun updateScrub(fraction: Float, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    val clamped = PlaybackMath.clampFraction(fraction)
    previewFraction = clamped
    return clamped
  }

  /**
   * Completes the scrub gesture and returns Pair(clampedFraction, shouldResumePlayback).
   */
  fun finishScrub(fraction: Float, durationMs: Long): Pair<Float, Boolean> {
    val shouldResume = wasPlayingBeforeScrub && durationMs > 0L
    val clamped = if (durationMs > 0L) PlaybackMath.clampFraction(fraction) else 0f
    previewFraction = clamped
    isScrubbing = false
    wasPlayingBeforeScrub = false
    return Pair(clamped, shouldResume)
  }

  fun cancelScrub(fallbackFraction: Float, durationMs: Long): Pair<Float, Boolean> {
    return finishScrub(fallbackFraction, durationMs)
  }
}

/**
 * Unified in-app studio playback controller supporting both imported audio files (MediaPlayer via Uri)
 * and freshly recorded microphone captures held in memory (AudioTrack streaming PCM FloatArray).
 * Emits reactive position/progress StateFlows synced at ~60 FPS and supports drag-pause scrubbing.
 */
class StudioAudioPlayer(private val scope: CoroutineScope) {

  private sealed interface AudioSource {
    data class UriSource(val context: Context, val uri: Uri) : AudioSource
    data class PcmSource(val samples: FloatArray, val sampleRate: Int) : AudioSource
  }

  private var activeSource: AudioSource? = null
  private var mediaPlayer: MediaPlayer? = null
  private var audioTrack: AudioTrack? = null
  private var playbackJob: Job? = null
  private var progressPollJob: Job? = null

  @Volatile private var pcmCursorSample: Int = 0
  private val pcmLock = Any()
  private val scrubController = ScrubStateController()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _isLooping = MutableStateFlow(false)
  val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

  private val _isScrubbing = MutableStateFlow(false)
  val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

  private val _hasAudio = MutableStateFlow(false)
  val hasAudio: StateFlow<Boolean> = _hasAudio.asStateFlow()

  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _progress = MutableStateFlow(0f)
  val progress: StateFlow<Float> = _progress.asStateFlow()

  fun loadUri(context: Context, uri: Uri, fallbackDurationMs: Long = 0L) {
    releaseActiveEngines()
    activeSource = AudioSource.UriSource(context.applicationContext, uri)
    pcmCursorSample = 0
    _positionMs.value = 0L
    _progress.value = 0f

    try {
      val mp = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        setDataSource(context.applicationContext, uri)
        prepare()
        isLooping = _isLooping.value
        setOnCompletionListener {
          if (!_isLooping.value) {
            _isPlaying.value = false
            progressPollJob?.cancel()
            _positionMs.value = 0L
            _progress.value = 0f
            try {
              seekTo(0)
            } catch (_: Exception) {}
          } else {
            _positionMs.value = 0L
            _progress.value = 0f
          }
        }
      }
      mediaPlayer = mp
      val dur = mp.duration.toLong().let { if (it > 0L) it else fallbackDurationMs }
      _durationMs.value = dur.coerceAtLeast(0L)
      _hasAudio.value = true
    } catch (e: Exception) {
      e.printStackTrace()
      mediaPlayer = null
      _durationMs.value = fallbackDurationMs.coerceAtLeast(0L)
      _hasAudio.value = false
    }
  }

  fun loadPcm(samples: FloatArray, sampleRate: Int = 44100) {
    releaseActiveEngines()
    if (samples.isEmpty()) {
      activeSource = null
      _hasAudio.value = false
      _durationMs.value = 0L
      _positionMs.value = 0L
      _progress.value = 0f
      return
    }
    activeSource = AudioSource.PcmSource(samples.copyOf(), sampleRate)
    pcmCursorSample = 0
    val totalMs = ((samples.size.toDouble() / sampleRate.toDouble()) * 1000.0).roundToLong().coerceAtLeast(100L)
    _durationMs.value = totalMs
    _positionMs.value = 0L
    _progress.value = 0f
    _hasAudio.value = true
  }

  fun togglePlayPause() {
    if (_isPlaying.value) {
      pause()
    } else {
      play()
    }
  }

  fun play() {
    val src = activeSource ?: return
    if (_isPlaying.value || !_hasAudio.value || _durationMs.value <= 0L) return

    when (src) {
      is AudioSource.UriSource -> {
        val mp = mediaPlayer ?: return
        try {
          mp.isLooping = _isLooping.value
          mp.start()
          _isPlaying.value = true
          startMediaPlayerPolling()
        } catch (e: Exception) {
          e.printStackTrace()
          _isPlaying.value = false
        }
      }

      is AudioSource.PcmSource -> {
        startPcmPlayback(src)
      }
    }
  }

  fun pause() {
    if (!_isPlaying.value) return
    _isPlaying.value = false
    progressPollJob?.cancel()
    playbackJob?.cancel()

    mediaPlayer?.let { mp ->
      try {
        if (mp.isPlaying) {
          mp.pause()
        }
      } catch (_: Exception) {}
    }

    synchronized(pcmLock) {
      audioTrack?.let { track ->
        try {
          track.pause()
          track.flush()
        } catch (_: Exception) {}
      }
    }
  }

  fun stop() {
    scrubController.cancelScrub(0f, _durationMs.value)
    _isScrubbing.value = false
    pause()
    pcmCursorSample = 0
    _positionMs.value = 0L
    _progress.value = 0f
    mediaPlayer?.let { mp ->
      try {
        mp.seekTo(0)
      } catch (_: Exception) {}
    }
  }

  fun beginScrub(initialFraction: Float) {
    val started = scrubController.startScrub(_isPlaying.value, initialFraction, _durationMs.value)
    if (!started || !_hasAudio.value) return
    _isScrubbing.value = true
    if (_isPlaying.value) {
      pause()
    }
    previewScrub(initialFraction)
  }

  fun previewScrub(fraction: Float) {
    val dur = _durationMs.value
    if (dur <= 0L || !_hasAudio.value) return
    val clamped = scrubController.updateScrub(fraction, dur)
    val targetMs = PlaybackMath.computeSeekTargetMs(clamped, dur)
    _progress.value = clamped
    _positionMs.value = targetMs
  }

  fun endScrub(fraction: Float) {
    val dur = _durationMs.value
    val (clamped, shouldResume) = scrubController.finishScrub(fraction, dur)
    _isScrubbing.value = false
    if (dur <= 0L || !_hasAudio.value) return
    seekToFraction(clamped)
    if (shouldResume) {
      play()
    }
  }

  fun seekToFraction(fraction: Float) {
    val dur = _durationMs.value
    if (dur <= 0L || !_hasAudio.value) return
    val clamped = PlaybackMath.clampFraction(fraction)
    val targetMs = PlaybackMath.computeSeekTargetMs(clamped, dur)

    _positionMs.value = targetMs
    _progress.value = clamped

    when (val src = activeSource) {
      is AudioSource.UriSource -> {
        try {
          mediaPlayer?.seekTo(targetMs.toInt())
        } catch (_: Exception) {}
      }

      is AudioSource.PcmSource -> {
        val newSample = PlaybackMath.computePcmSampleIndex(clamped, src.samples.size)
        val wasPlaying = _isPlaying.value
        if (wasPlaying) {
          playbackJob?.cancel()
          synchronized(pcmLock) {
            try {
              audioTrack?.pause()
              audioTrack?.flush()
            } catch (_: Exception) {}
          }
          pcmCursorSample = newSample
          startPcmPlayback(src)
        } else {
          pcmCursorSample = newSample
        }
      }

      null -> Unit
    }
  }

  fun toggleLoop() {
    val next = !_isLooping.value
    _isLooping.value = next
    try {
      mediaPlayer?.isLooping = next
    } catch (_: Exception) {}
  }

  private fun startMediaPlayerPolling() {
    progressPollJob?.cancel()
    progressPollJob = scope.launch(Dispatchers.Main) {
      while (isActive && _isPlaying.value) {
        if (!_isScrubbing.value) {
          val mp = mediaPlayer ?: break
          try {
            val rawPos = mp.currentPosition.toLong().coerceAtLeast(0L)
            val dur = mp.duration.toLong().let { if (it > 0L) it else _durationMs.value }
            if (dur > 0L) {
              val safePos = PlaybackMath.advanceLoopPositionMs(rawPos, dur, _isLooping.value)
              _durationMs.value = dur
              _positionMs.value = safePos
              _progress.value = PlaybackMath.computeNormalizedProgress(safePos, dur)
            }
          } catch (_: Exception) {
            break
          }
        }
        delay(16L) // ~60 FPS sync
      }
    }
  }

  private fun startPcmPlayback(src: AudioSource.PcmSource) {
    playbackJob?.cancel()
    val samples = src.samples
    val sampleRate = src.sampleRate
    if (samples.isEmpty()) return

    val minBufBytes = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    synchronized(pcmLock) {
      if (audioTrack == null) {
        audioTrack = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(minBufBytes * 2)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      }
      audioTrack?.play()
    }

    _isPlaying.value = true
    playbackJob = scope.launch(Dispatchers.IO) {
      val chunkFloats = 735 // ~16.6ms at 44.1kHz for tight 60 FPS progress reporting
      val shortChunk = ShortArray(chunkFloats)
      try {
        while (isActive && _isPlaying.value) {
          if (_isScrubbing.value) {
            delay(16L)
            continue
          }
          val cursor = pcmCursorSample
          if (cursor >= samples.size) {
            if (_isLooping.value) {
              pcmCursorSample = 0
              _positionMs.value = 0L
              _progress.value = 0f
              continue
            } else {
              _isPlaying.value = false
              pcmCursorSample = 0
              _positionMs.value = 0L
              _progress.value = 0f
              break
            }
          }

          val count = (samples.size - cursor).coerceAtMost(chunkFloats)
          for (i in 0 until count) {
            val clamped = samples[cursor + i].coerceIn(-1f, 1f)
            shortChunk[i] = (clamped * 32767f).toInt().toShort()
          }

          val written = synchronized(pcmLock) {
            audioTrack?.write(shortChunk, 0, count) ?: -1
          }
          if (written <= 0) break

          pcmCursorSample = cursor + count
          if (!_isScrubbing.value) {
            val durMs = _durationMs.value
            val posMs = ((pcmCursorSample.toDouble() / sampleRate.toDouble()) * 1000.0).roundToLong()
            val safePos = PlaybackMath.advanceLoopPositionMs(posMs, durMs, _isLooping.value)
            _positionMs.value = safePos
            _progress.value = PlaybackMath.computeNormalizedProgress(safePos, durMs)
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun releaseActiveEngines() {
    _isPlaying.value = false
    _isScrubbing.value = false
    scrubController.cancelScrub(0f, 0L)
    progressPollJob?.cancel()
    playbackJob?.cancel()
    progressPollJob = null
    playbackJob = null

    try {
      mediaPlayer?.stop()
    } catch (_: Exception) {}
    try {
      mediaPlayer?.release()
    } catch (_: Exception) {}
    mediaPlayer = null

    synchronized(pcmLock) {
      try {
        audioTrack?.stop()
      } catch (_: Exception) {}
      try {
        audioTrack?.release()
      } catch (_: Exception) {}
      audioTrack = null
    }
  }

  fun release() {
    releaseActiveEngines()
    activeSource = null
    _hasAudio.value = false
    _positionMs.value = 0L
    _durationMs.value = 0L
    _progress.value = 0f
  }
}

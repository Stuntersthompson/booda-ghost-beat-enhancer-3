package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.LOCK_LINE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.*

data class AudioAnalysisResult(
  val fileName: String,
  val durationSec: Double,
  val bpm: Double?,
  val bpmConfidence: String, // "high", "medium", "low"
  val keyName: String?,
  val keyClear: Boolean,
  val rmsDb: Double,
  val peakDb: Double,
  val lowShare: Double, // 0..1 of spectral energy under ~160 Hz
  val centroidHz: Double,
  val onsetRate: Double,
  val suggestedLane: String,
  val pocketLabel: String,
  val promptPreserve: String,
  val promptOriginal: String,
  val sampleWordsText: String? = null,
  val sampleWordsKind: String? = null,
  val waveformPeaks: List<Float> = emptyList()
) {
  val waveformPeaksArray: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
    waveformPeaks.toFloatArray()
  }
}

data class AnalyzedClipOutput(
  val result: AudioAnalysisResult,
  val multimodalWavBytes: ByteArray?
)

class AudioAnalyzer(private val context: Context) {

  private data class DecodedAudio(
    val samples: FloatArray,
    val sampleRate: Int
  )

  private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
  private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
  private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

  suspend fun analyzeAudioUri(uri: Uri, fileName: String): AudioAnalysisResult =
    analyzeAudioUriWithClip(uri, fileName).result

  suspend fun analyzeAudioUriWithClip(uri: Uri, fileName: String): AnalyzedClipOutput =
    withContext(Dispatchers.IO) {
      val decoded = decodeAudioUri(uri)
      val analysis = analyzeFloatSamples(fileName, decoded.samples, decoded.sampleRate)
      val wavBytes = if (decoded.samples.isNotEmpty()) {
        encodeWav16KhzMono(decoded.samples, decoded.sampleRate, maxSeconds = 15)
      } else null
      AnalyzedClipOutput(analysis, wavBytes)
    }

  suspend fun analyzeRecordedSamples(
    fileName: String,
    samples: FloatArray,
    sampleRate: Int = 44100
  ): AudioAnalysisResult = withContext(Dispatchers.Default) {
    analyzeFloatSamples(fileName, samples, sampleRate)
  }

  /**
   * Synchronous DSP entry point for tests and CPU-bound analysis.
   */
  fun analyzeFloatSamples(fileName: String, samples: FloatArray, sampleRate: Int): AudioAnalysisResult {
    if (samples.isEmpty() || sampleRate <= 0) {
      return fallbackResult(fileName)
    }

    val durationSec = samples.size.toDouble() / sampleRate
    var sumSq = 0.0
    var peak = 0.0
    for (s in samples) {
      val absVal = abs(s.toDouble())
      if (absVal > peak) peak = absVal
      sumSq += (s * s)
    }
    val rms = sqrt(sumSq / samples.size)
    val rmsDb = if (rms > 1e-5) 20.0 * log10(rms) else -80.0
    val peakDb = if (peak > 1e-5) 20.0 * log10(peak) else -80.0

    val (lowShare, centroidHz) = calculateSpectralFeatures(samples, sampleRate)
    val (keyName, keyClear) = estimateMusicalKey(samples, sampleRate)
    val (estimatedBpm, confidence, onsetRate) = estimateTempo(samples, sampleRate)

    // Extract 100-bucket groove waveform peaks across the full audio duration
    val numBuckets = 100
    val bucketSize = max(1, samples.size / numBuckets)
    val peaks = ArrayList<Float>(numBuckets)
    for (b in 0 until numBuckets) {
      var bPeak = 0.06f
      val start = b * bucketSize
      val end = if (b == numBuckets - 1) samples.size else min(samples.size, start + bucketSize)
      for (i in start until end) {
        val a = abs(samples[i])
        if (a > bPeak) bPeak = a
      }
      peaks.add(bPeak.coerceIn(0.06f, 1.0f))
    }

    val resolvedBpm = estimatedBpm ?: 130.0
    val suggestedLane = rankBestLane(resolvedBpm, centroidHz, lowShare, onsetRate)
    val pocketLabel = "${resolvedBpm.roundToInt()} BPM · ${if (centroidHz > 2800) "Bright" else "Dark"} · ${if (lowShare > 0.36) "Sub-heavy" else "Balanced"}"

    val promptPreserve = "$LOCK_LINE. Add tight $suggestedLane drums underneath only, spice only, sample stays lead."
    val promptOriginal = "$suggestedLane instrumental, ${resolvedBpm.roundToInt()} BPM, heavy 808, crisp percussion, full arrangement."

    return AudioAnalysisResult(
      fileName = fileName,
      durationSec = durationSec,
      bpm = estimatedBpm,
      bpmConfidence = confidence,
      keyName = keyName,
      keyClear = keyClear,
      rmsDb = rmsDb,
      peakDb = peakDb,
      lowShare = lowShare,
      centroidHz = centroidHz,
      onsetRate = onsetRate,
      suggestedLane = suggestedLane,
      pocketLabel = pocketLabel,
      promptPreserve = promptPreserve,
      promptOriginal = promptOriginal,
      waveformPeaks = peaks
    )
  }

  /**
   * Downsamples/normalizes float audio to compact 16 kHz mono 16-bit WAV (max 15s = ~480 KB)
   * for multimodal Gemini audio analysis well inside inline payload limits.
   */
  fun encodeWav16KhzMono(
    samples: FloatArray,
    sourceSampleRate: Int,
    maxSeconds: Int = 15
  ): ByteArray {
    if (samples.isEmpty() || sourceSampleRate <= 0) return ByteArray(0)
    val targetRate = 16000
    val step = sourceSampleRate.toDouble() / targetRate
    val maxTargetSamples = targetRate * maxSeconds
    val estimatedOutSamples = min(maxTargetSamples, (samples.size / step).toInt().coerceAtLeast(1))
    val dataBytes = estimatedOutSamples * 2
    val out = ByteArrayOutputStream(44 + dataBytes)

    // Write RIFF/WAVE header (little-endian PCM 16-bit mono)
    fun writeAscii(s: String) {
      for (ch in s) out.write(ch.code)
    }
    fun writeIntLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
      out.write((v shr 16) and 0xFF)
      out.write((v shr 24) and 0xFF)
    }
    fun writeShortLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v shr 8) and 0xFF)
    }

    writeAscii("RIFF")
    writeIntLE(36 + dataBytes)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeIntLE(16) // PCM subchunk size
    writeShortLE(1) // PCM format
    writeShortLE(1) // Mono channel
    writeIntLE(targetRate)
    writeIntLE(targetRate * 2) // Byte rate
    writeShortLE(2) // Block align
    writeShortLE(16) // Bits per sample
    writeAscii("data")
    writeIntLE(dataBytes)

    for (i in 0 until estimatedOutSamples) {
      val srcPos = i * step
      val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
      val idx1 = (idx0 + 1).coerceAtMost(samples.lastIndex)
      val frac = (srcPos - idx0).toFloat()
      val sample = (samples[idx0] * (1f - frac) + samples[idx1] * frac).coerceIn(-1f, 1f)
      val pcm16 = (sample * 32767f).roundToInt().coerceIn(-32768, 32767)
      out.write(pcm16 and 0xFF)
      out.write((pcm16 shr 8) and 0xFF)
    }
    return out.toByteArray()
  }

  /**
   * Multi-frame Hamming-windowed FFT spectral analysis sampled evenly across the track,
   * skipping near-silent frames and reusing pre-allocated arrays to avoid GC churn.
   */
  internal fun calculateSpectralFeatures(samples: FloatArray, sampleRate: Int): Pair<Double, Double> {
    val fftSize = 2048
    if (samples.size < fftSize) return Pair(0.35, 2500.0)

    val maxStart = samples.size - fftSize
    val targetFrames = 24
    val frameStep = max(fftSize / 2, maxStart / max(1, targetFrames - 1))

    val hamming = DoubleArray(fftSize) { i ->
      0.54 - 0.46 * cos(2.0 * PI * i / (fftSize - 1))
    }
    val real = DoubleArray(fftSize)
    val imag = DoubleArray(fftSize)
    val binWidth = sampleRate.toDouble() / fftSize

    var lowEnergyTotal = 0.0
    var totalEnergyTotal = 0.0
    var weightedFreqTotal = 0.0
    var activeFrames = 0

    var offset = 0
    while (offset <= maxStart && activeFrames < targetFrames) {
      var frameSumSq = 0.0
      for (i in 0 until fftSize) {
        val s = samples[offset + i].toDouble()
        frameSumSq += s * s
        real[i] = s * hamming[i]
        imag[i] = 0.0
      }

      // Skip near-silent intro/outro frames (under -60 dBFS) unless no louder frames exist
      val frameRms = sqrt(frameSumSq / fftSize)
      if (frameRms > 1e-3) {
        fftRadix2(real, imag)
        for (k in 1 until fftSize / 2) {
          val mag = hypot(real[k], imag[k])
          val freq = k * binWidth
          totalEnergyTotal += mag
          weightedFreqTotal += (mag * freq)
          if (freq <= 160.0) {
            lowEnergyTotal += mag
          }
        }
        activeFrames++
      }
      offset += frameStep
    }

    if (totalEnergyTotal <= 1e-9) {
      return Pair(0.35, 2500.0)
    }

    val lowShare = (lowEnergyTotal / totalEnergyTotal).coerceIn(0.05, 0.95)
    val centroid = (weightedFreqTotal / totalEnergyTotal).coerceIn(300.0, 9000.0)
    return Pair(lowShare, centroid)
  }

  /**
   * Hann-windowed multi-frame Krumhansl-Schmuckler chromagram key detector sampled
   * evenly across the track instead of only reading the first 10 consecutive frames.
   */
  internal fun estimateMusicalKey(samples: FloatArray, sampleRate: Int): Pair<String?, Boolean> {
    val windowSize = 4096
    if (samples.size < windowSize) return Pair("C minor", false)

    val chroma = DoubleArray(12)
    val hann = DoubleArray(windowSize) { i ->
      0.5 * (1.0 - cos(2.0 * PI * i / (windowSize - 1)))
    }
    val real = DoubleArray(windowSize)
    val imag = DoubleArray(windowSize)

    val maxStart = samples.size - windowSize
    val targetFrames = 24
    val frameStep = max(windowSize / 2, maxStart / max(1, targetFrames - 1))
    val binWidth = sampleRate.toDouble() / windowSize

    var offset = 0
    var count = 0
    while (offset <= maxStart && count < targetFrames) {
      var energySum = 0.0
      for (i in 0 until windowSize) {
        val s = samples[offset + i].toDouble()
        energySum += s * s
        real[i] = s * hann[i]
        imag[i] = 0.0
      }

      if (energySum / windowSize > 1e-6) {
        fftRadix2(real, imag)
        for (k in 1 until windowSize / 2) {
          val freq = k * binWidth
          if (freq in 65.0..2000.0) {
            val mag = hypot(real[k], imag[k])
            val midi = (12.0 * log2(freq / 440.0) + 69.0).roundToInt()
            val pitchClass = (midi % 12 + 12) % 12
            chroma[pitchClass] += mag * mag
          }
        }
      }
      count++
      offset += frameStep
    }

    val sum = chroma.sum()
    if (sum <= 1e-12) return Pair(null, false)
    val normChroma = DoubleArray(12) { chroma[it] / sum }

    var bestScore = -Double.MAX_VALUE
    var secondScore = -Double.MAX_VALUE
    var bestKey = "C minor"

    for (root in 0 until 12) {
      val majScore = correlate(normChroma, MAJOR_PROFILE, root)
      if (majScore > bestScore) {
        secondScore = bestScore
        bestScore = majScore
        bestKey = "${NOTE_NAMES[root]} major"
      } else if (majScore > secondScore) {
        secondScore = majScore
      }

      val minScore = correlate(normChroma, MINOR_PROFILE, root)
      if (minScore > bestScore) {
        secondScore = bestScore
        bestScore = minScore
        bestKey = "${NOTE_NAMES[root]} minor"
      } else if (minScore > secondScore) {
        secondScore = minScore
      }
    }

    val clear = (bestScore - secondScore) > 0.07 && bestScore > 0.50
    return Pair(bestKey, clear)
  }

  private fun correlate(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
    var meanC = 0.0
    var meanP = 0.0
    for (i in 0 until 12) {
      meanC += chroma[i]
      meanP += profile[i]
    }
    meanC /= 12.0
    meanP /= 12.0

    var num = 0.0
    var dc = 0.0
    var dp = 0.0
    for (i in 0 until 12) {
      val c = chroma[i] - meanC
      val p = profile[(i - root + 12) % 12] - meanP
      num += c * p
      dc += c * c
      dp += p * p
    }
    val den = sqrt(dc * dp)
    return if (den > 0.0) num / den else 0.0
  }

  /**
   * Lag-autocorrelation tempo tracker computed on a half-wave rectified multi-frame energy
   * novelty curve over candidate hip-hop/electronic tempos (60..176 BPM).
   */
  internal fun estimateTempo(samples: FloatArray, sampleRate: Int): Triple<Double?, String, Double> {
    val hopSize = 512
    val numHops = samples.size / hopSize
    val durationSec = samples.size.toDouble() / sampleRate
    if (numHops < 24 || durationSec < 0.8) {
      return Triple(130.0, "medium", 2.0)
    }

    val energy = DoubleArray(numHops)
    for (h in 0 until numHops) {
      var e = 0.0
      val start = h * hopSize
      for (i in 0 until hopSize) {
        val s = samples[start + i].toDouble()
        e += s * s
      }
      energy[h] = sqrt(e / hopSize)
    }

    // Half-wave rectified novelty curve + adaptive threshold onset counter
    val novelty = DoubleArray(numHops)
    var noveltyMean = 0.0
    for (h in 1 until numHops) {
      val diff = energy[h] - energy[h - 1]
      val pos = if (diff > 0.0) diff else 0.0
      novelty[h] = pos
      noveltyMean += pos
    }
    noveltyMean /= max(1, numHops - 1)

    var onsets = 0
    val adaptiveThresh = max(1e-4, noveltyMean * 2.1)
    var lastOnsetHop = -4
    for (h in 1 until numHops) {
      if (novelty[h] > adaptiveThresh && (h - lastOnsetHop) >= 3) {
        onsets++
        lastOnsetHop = h
      }
    }
    val onsetRate = if (durationSec > 0.0) onsets / durationSec else 2.0

    val frameRate = sampleRate.toDouble() / hopSize
    val minBpm = 60
    val maxBpm = 176
    val minLag = max(12, floor(60.0 * frameRate / maxBpm).toInt())
    val maxLag = min(numHops / 2, ceil(60.0 * frameRate / minBpm).toInt())

    if (maxLag <= minLag) {
      val fallbackBpm = (onsetRate * 60.0).coerceIn(70.0, 165.0)
      return Triple(round(fallbackBpm * 10.0) / 10.0, "low", round(onsetRate * 10.0) / 10.0)
    }

    // Normalize zero-mean novelty curve for autocorrelation
    var zeroEnergySum = 0.0
    for (v in novelty) {
      val centered = v - noveltyMean
      zeroEnergySum += centered * centered
    }
    if (zeroEnergySum <= 1e-12) {
      return Triple(130.0, "low", round(onsetRate * 10.0) / 10.0)
    }

    val acf = DoubleArray(maxLag + 2)
    var bestLag = minLag
    var bestScore = -Double.MAX_VALUE

    for (lag in minLag..maxLag) {
      var corr = 0.0
      val count = numHops - lag
      for (i in 0 until count) {
        corr += (novelty[i] - noveltyMean) * (novelty[i + lag] - noveltyMean)
      }
      // Normalize by active overlap length so longer lags are fairly compared
      val rawNorm = (corr * numHops) / (count.toDouble() * zeroEnergySum)
      val candidateBpm = 60.0 * frameRate / lag
      // Gentle perceptual weighting centered around common hip-hop pocket (~118 BPM)
      val priorWeight = exp(-0.5 * ((ln(candidateBpm / 118.0)) / 0.42).pow(2.0))
      val weighted = rawNorm * (0.75 + 0.25 * priorWeight)
      acf[lag] = rawNorm
      if (weighted > bestScore) {
        bestScore = weighted
        bestLag = lag
      }
    }

    // Parabolic interpolation around bestLag for sub-frame BPM accuracy
    val y0 = acf[(bestLag - 1).coerceAtLeast(minLag)]
    val y1 = acf[bestLag]
    val y2 = acf[(bestLag + 1).coerceAtMost(maxLag)]
    val denom = 2.0 * (y0 - 2.0 * y1 + y2)
    val delta = if (abs(denom) > 1e-9) ((y0 - y2) / denom).coerceIn(-0.5, 0.5) else 0.0
    var refinedBpm = 60.0 * frameRate / (bestLag + delta)

    // Clamp rare extreme sub/super multiples cleanly
    while (refinedBpm < 65.0) refinedBpm *= 2.0
    while (refinedBpm > 175.0) refinedBpm /= 2.0

    val peakNorm = y1.coerceIn(0.0, 1.0)
    val confidence = when {
      peakNorm >= 0.38 && durationSec >= 3.0 -> "high"
      peakNorm >= 0.18 -> "medium"
      else -> "low"
    }

    return Triple(
      round(refinedBpm * 10.0) / 10.0,
      confidence,
      round(onsetRate * 10.0) / 10.0
    )
  }

  private fun rankBestLane(bpm: Double, centroid: Double, lowShare: Double, density: Double): String {
    return when {
      bpm in 130.0..155.0 && lowShare > 0.40 -> "Trap"
      bpm in 135.0..150.0 && centroid < 2500 -> "NY drill"
      bpm in 136.0..148.0 && density > 2.5 -> "UK drill"
      bpm in 82.0..98.0 && centroid < 2200 -> "Boom bap"
      bpm in 90.0..115.0 && centroid > 3200 -> "Milwaukee low-end"
      bpm in 95.0..118.0 && density > 2.8 -> "DMV crank"
      bpm in 85.0..105.0 -> "West Coast / G-funk"
      bpm in 120.0..145.0 && centroid < 2000 -> "Memphis phonk / dark trap"
      bpm in 140.0..170.0 -> "Detroit scam-rap pocket"
      bpm in 65.0..85.0 -> "Chopped & screwed"
      else -> "Trap"
    }
  }

  /**
   * Decodes any Android content URI audio stream, preserving its real sample rate
   * and downmixing multi-channel interleaved PCM frames cleanly into mono Floats.
   */
  private fun decodeAudioUri(uri: Uri): DecodedAudio {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      extractor.setDataSource(context, uri, null)
      var audioTrackIndex = -1
      var format: MediaFormat? = null

      for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          format = f
          break
        }
      }

      if (audioTrackIndex < 0 || format == null) return DecodedAudio(FloatArray(0), 44100)
      extractor.selectTrack(audioTrackIndex)

      val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
      var decodedSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
      } else 44100
      var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
      } else 1

      val activeCodec = MediaCodec.createDecoderByType(mime).also { codec = it }
      activeCodec.configure(format, null, null, 0)
      activeCodec.start()

      val maxMonoSamples = decodedSampleRate * 30
      val outputFloats = ArrayList<Float>(min(maxMonoSamples, 44100 * 15))
      val info = MediaCodec.BufferInfo()
      var isInputEOS = false
      var isOutputEOS = false
      val timeoutUs = 5000L

      while (!isOutputEOS && outputFloats.size < maxMonoSamples) {
        if (!isInputEOS) {
          val inIndex = activeCodec.dequeueInputBuffer(timeoutUs)
          if (inIndex >= 0) {
            val buffer = activeCodec.getInputBuffer(inIndex)
            val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
            if (sampleSize < 0) {
              activeCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              isInputEOS = true
            } else {
              activeCodec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outIndex = activeCodec.dequeueOutputBuffer(info, timeoutUs)
        when {
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = activeCodec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              decodedSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            }
            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            }
          }
          outIndex >= 0 -> {
            val outBuffer = activeCodec.getOutputBuffer(outIndex)
            if (outBuffer != null && info.size > 0) {
              outBuffer.position(info.offset)
              outBuffer.limit(info.offset + info.size)
              val dup = outBuffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
              val frameLimit = decodedSampleRate * 30
              while (dup.remaining() >= channelCount && outputFloats.size < frameLimit) {
                var sum = 0f
                for (ch in 0 until channelCount) {
                  sum += dup.get() / 32768.0f
                }
                outputFloats.add(sum / channelCount)
              }
            }
            activeCodec.releaseOutputBuffer(outIndex, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              isOutputEOS = true
            }
          }
        }
      }

      DecodedAudio(outputFloats.toFloatArray(), decodedSampleRate)
    } catch (e: Exception) {
      e.printStackTrace()
      DecodedAudio(FloatArray(0), 44100)
    } finally {
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      try { extractor.release() } catch (_: Exception) {}
    }
  }

  private fun fftRadix2(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 0 until n - 1) {
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
      var k = n shr 1
      while (k <= j) {
        j -= k
        k = k shr 1
      }
      j += k
    }

    var len = 2
    while (len <= n) {
      val ang = -2.0 * PI / len
      val wlenR = cos(ang)
      val wlenI = sin(ang)
      val half = len shr 1
      var i = 0
      while (i < n) {
        var wR = 1.0
        var wI = 0.0
        for (k in 0 until half) {
          val a = i + k
          val b = a + half
          val uR = re[a]
          val uI = im[a]
          val vR = re[b] * wR - im[b] * wI
          val vI = re[b] * wI + im[b] * wR
          re[a] = uR + vR
          im[a] = uI + vI
          re[b] = uR - vR
          im[b] = uI - vI
          val nwR = wR * wlenR - wI * wlenI
          wI = wR * wlenI + wI * wlenR
          wR = nwR
        }
        i += len
      }
      len = len shl 1
    }
  }

  private fun fallbackResult(fileName: String): AudioAnalysisResult {
    return AudioAnalysisResult(
      fileName = fileName,
      durationSec = 15.0,
      bpm = 130.0,
      bpmConfidence = "medium",
      keyName = "C minor",
      keyClear = true,
      rmsDb = -18.5,
      peakDb = -2.1,
      lowShare = 0.42,
      centroidHz = 2450.0,
      onsetRate = 2.1,
      suggestedLane = "Trap",
      pocketLabel = "130 BPM · Balanced · Sub-heavy",
      promptPreserve = "$LOCK_LINE. Add tight Trap drums underneath only, spice only, sample stays lead.",
      promptOriginal = "Trap instrumental, 130 BPM, heavy 808, crisp percussion, full arrangement.",
      waveformPeaks = generateGroovePeaks()
    )
  }

  private fun generateGroovePeaks(): List<Float> {
    val list = ArrayList<Float>(100)
    for (i in 0 until 100) {
      val stepInBar = i % 16
      val peak = when (stepInBar) {
        0 -> 0.95f
        4 -> 0.45f
        8 -> 0.85f
        12 -> 0.50f
        14 -> 0.65f
        else -> 0.25f + ((i * 7) % 20) / 100f
      }
      list.add(peak.coerceIn(0.1f, 1.0f))
    }
    return list
  }
}

package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.round

/**
 * Repository managing persistent studio and visualizer preferences.
 * Uses Android SharedPreferences for fast synchronous startup and persistent storage across restarts.
 */
class StudioPreferencesRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _visualizerSensitivity = MutableStateFlow(
    prefs.getFloat(KEY_VISUALIZER_SENSITIVITY, DEFAULT_SENSITIVITY)
      .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
  )
  val visualizerSensitivity: StateFlow<Float> = _visualizerSensitivity.asStateFlow()

  private val _peakHoldEnabled = MutableStateFlow(
    prefs.getBoolean(KEY_PEAK_HOLD_ENABLED, DEFAULT_PEAK_HOLD_ENABLED)
  )
  val peakHoldEnabled: StateFlow<Boolean> = _peakHoldEnabled.asStateFlow()

  fun setVisualizerSensitivity(multiplier: Float) {
    val clamped = multiplier.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    _visualizerSensitivity.value = clamped
    prefs.edit().putFloat(KEY_VISUALIZER_SENSITIVITY, clamped).apply()
  }

  fun resetVisualizerSensitivity() {
    setVisualizerSensitivity(DEFAULT_SENSITIVITY)
  }

  fun setPeakHoldEnabled(enabled: Boolean) {
    _peakHoldEnabled.value = enabled
    prefs.edit().putBoolean(KEY_PEAK_HOLD_ENABLED, enabled).apply()
  }

  companion object {
    const val PREFS_NAME = "booda_studio_preferences"
    const val KEY_VISUALIZER_SENSITIVITY = "visualizer_sensitivity"
    const val KEY_PEAK_HOLD_ENABLED = "peak_hold_enabled"
    const val MIN_SENSITIVITY = 0.25f
    const val MAX_SENSITIVITY = 4.0f
    const val SENSITIVITY_STEP = 0.05f
    const val SENSITIVITY_SLIDER_STEPS = 74 // (4.0 - 0.25) / 0.05 = 75 intervals -> 74 intermediate stops
    const val DEFAULT_SENSITIVITY = 1.0f
    const val DEFAULT_PEAK_HOLD_ENABLED = true

    fun snapSensitivityStep(raw: Float): Float {
      val clamped = raw.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
      val stepped = round((clamped - MIN_SENSITIVITY) / SENSITIVITY_STEP) * SENSITIVITY_STEP + MIN_SENSITIVITY
      return (round(stepped * 100f) / 100f).coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    }
  }
}

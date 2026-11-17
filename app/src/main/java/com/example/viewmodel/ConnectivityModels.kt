package com.example.viewmodel

enum class FirebaseHealthStatus(val label: String) {
  HEALTHY("Ready"),
  INITIALIZING("Checking"),
  UNREACHABLE("Unreachable"),
  OFFLINE("Offline")
}

enum class GeminiHealthStatus(val label: String) {
  HEALTHY("Ready"),
  UNREACHABLE("Unreachable"),
  TIMED_OUT("Timed Out"),
  OFFLINE("Offline")
}

enum class FailedCloudRequestType(val label: String) {
  BEAT_CONSULTATION("AI Beat Consultation"),
  MIC_TRANSCRIPTION("Voice Transcription"),
  SEARCH_GROUNDING("Search Grounding")
}

data class GlobalConnectivityState(
  val isNetworkOnline: Boolean = true,
  val firebaseHealth: FirebaseHealthStatus = FirebaseHealthStatus.HEALTHY,
  val geminiHealth: GeminiHealthStatus = GeminiHealthStatus.HEALTHY,
  val lastErrorMessage: String? = null,
  val lastFailedRequest: FailedCloudRequestType? = null,
  val isRetrying: Boolean = false,
  val isBannerDismissed: Boolean = false
) {
  val isFirebaseReachable: Boolean
    get() = isNetworkOnline && firebaseHealth == FirebaseHealthStatus.HEALTHY

  val isGeminiReachable: Boolean
    get() = isNetworkOnline && geminiHealth == GeminiHealthStatus.HEALTHY

  val hasUnreachableService: Boolean
    get() = !isNetworkOnline ||
      firebaseHealth != FirebaseHealthStatus.HEALTHY ||
      geminiHealth != GeminiHealthStatus.HEALTHY

  val shouldShowBanner: Boolean
    get() = hasUnreachableService && !isBannerDismissed
}

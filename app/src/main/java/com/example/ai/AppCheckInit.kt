package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"

/**
 * Configures Firebase App Check before Compose UI, ViewModels, or Firebase AI calls run.
 * Delegates to flavor/build-type-specific [installPlatformAppCheckProvider] so debug
 * App Check tokens and providers are compiled out of release APKs/AABs.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    installPlatformAppCheckProvider(context, intent)
  } catch (e: Exception) {
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}

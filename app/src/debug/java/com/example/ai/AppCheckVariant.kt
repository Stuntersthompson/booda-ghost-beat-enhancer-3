package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheckVariant"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

internal fun installPlatformAppCheckProvider(context: Context, intent: Intent?) {
  val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
  if (!debugToken.isNullOrEmpty()) {
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs = context.getSharedPreferences(
      "$DEBUG_STORE_PREFIX$persistenceKey",
      Context.MODE_PRIVATE
    )
    prefs.edit()
      .putString(DEBUG_SECRET_KEY, debugToken)
      .commit()
    Log.d(TAG, "App Check debug secret configured successfully.")
  } else {
    Log.w(TAG, "No debug token found in Intent extra.")
  }

  FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
    DebugAppCheckProviderFactory.getInstance()
  )
  Log.d(TAG, "DebugAppCheckProviderFactory installed.")
}

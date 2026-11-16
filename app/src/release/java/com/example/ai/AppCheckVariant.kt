package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckVariant"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

internal fun installPlatformAppCheckProvider(context: Context, @Suppress("UNUSED_PARAMETER") intent: Intent?) {
  val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
  if (resId != 0) {
    val siteKey = context.getString(resId)
    if (siteKey.isNotBlank()) {
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
      )
      Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    } else {
      Log.e(TAG, "recaptcha_site_key resource string is blank.")
    }
  } else {
    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
  }
}

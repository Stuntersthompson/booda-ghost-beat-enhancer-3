# ProGuard / R8 rules for BooDa Beat Enhancer

# Keep source file and line numbers for clear crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# App entrypoints and Room database classes
-keep class com.example.MainActivity { *; }
-keep class com.example.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract ** *(...);
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Models & audio DSP state
-keep class com.example.data.model.** { *; }
-keep class com.example.audio.AudioAnalysisResult { *; }

# Firebase & App Check
-keepattributes *Annotation*
-keepclassmembers class com.google.firebase.** { *; }
-dontwarn com.google.firebase.appcheck.debug.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

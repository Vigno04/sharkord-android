# Sharkord ProGuard / R8 Rules

# WebRTC and Mediasoup (JNI methods and callbacks)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class org.mediasoup.droid.** { *; }
-dontwarn org.mediasoup.droid.**
-keep class io.github.haiyangwu.mediasoup.** { *; }
-dontwarn io.github.haiyangwu.mediasoup.**

# Data Models used with Gson
-keep class com.sharkord.android.data.model.** { *; }
-keepclassmembers class com.sharkord.android.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keepattributes EnclosingMethod
-keepclassmembers enum * { *; }
-dontwarn com.google.gson.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# AndroidX WorkManager and Room (reflection-instantiated database & workers)
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
-keep class com.sharkord.android.data.network.MessageSyncWorker { *; }

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
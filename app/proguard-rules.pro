# Nimbus Weather ProGuard Rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sysadmindoc.nimbus.**$$serializer { *; }
-keepclassmembers class com.sysadmindoc.nimbus.** { *** Companion; }
-keepclasseswithmembers class com.sysadmindoc.nimbus.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# MapLibre
-keep class org.maplibre.** { *; }

# Compose Material3 Window Size Class
-keep class androidx.compose.material3.windowsizeclass.** { *; }

# Compose Stability annotations
-keep class androidx.compose.runtime.Stable
-keep class androidx.compose.runtime.Immutable

# Glance Widgets
-keep class androidx.glance.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Lottie Animations
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# Coil Image Loading
-dontwarn coil3.**

# Firebase Firestore
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# DataStore Preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# Hilt generated components
-keep class **_HiltModules* { *; }
-keep class dagger.hilt.** { *; }

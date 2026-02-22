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

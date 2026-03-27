# Wear OS ProGuard Rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.sysadmindoc.nimbus.wear.**$$serializer { *; }
-keepclassmembers class com.sysadmindoc.nimbus.wear.** { *** Companion; }
-keepclasseswithmembers class com.sysadmindoc.nimbus.wear.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# Wear OS Tiles
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }

# Complications
-keep class androidx.wear.watchface.complications.** { *; }

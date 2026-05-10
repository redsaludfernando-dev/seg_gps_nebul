# ── Kotlin Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.redsalud.seggpsnebul.**$$serializer { *; }
-keepclassmembers class com.redsalud.seggpsnebul.** {
    *** Companion;
}
-keepclasseswithmembers class com.redsalud.seggpsnebul.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── App domain models (serialized to/from Supabase) ──────────────────────────
-keep class com.redsalud.seggpsnebul.domain.model.** { *; }
-keep class com.redsalud.seggpsnebul.data.remote.*Dto { *; }
-keep class com.redsalud.seggpsnebul.data.remote.*Row { *; }
-keep class com.redsalud.seggpsnebul.data.remote.SessionStats { *; }

# ── Supabase / Ktor ───────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }

# ── SQLDelight ────────────────────────────────────────────────────────────────
-keep class com.squareup.sqldelight.** { *; }
-keep class app.cash.sqldelight.** { *; }
-dontwarn com.squareup.sqldelight.**
-dontwarn app.cash.sqldelight.**
# Clases generadas por SQLDelight (data classes Sessions, Gps_tracks, Alerts,
# Block_assignments, Allowed_users, Users + SegGpsDatabaseQueries). Viven en
# packageName declarado en shared/build.gradle.kts. Sin esto R8 podria
# eliminar las data classes que solo se construyen via reflection.
-keep class com.redsalud.seggpsnebul.data.local.** { *; }

# ── OkHttp (used by Ktor) ─────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Kotlin reflection ─────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { <fields>; }

# ── Android ───────────────────────────────────────────────────────────────────
-keepclassmembers class * extends android.app.Activity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

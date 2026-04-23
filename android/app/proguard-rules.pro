# SearchAid ProGuard Rules

# ---- Kotlin ----
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# ---- Hilt / Dagger ----
-dontwarn dagger.hilt.internal.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers,allowobfuscation class * { @dagger.* <fields>; @javax.inject.* <fields>; }
-keep,allowobfuscation,allowshrinking class * extends dagger.internal.Factory
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Google Maps ----
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }

# ---- Retrofit / OkHttp / Gson ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.searchaid.data.remote.model.** { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.internal.** { *; }

# ---- WorkManager ----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# ---- Data classes (domain models) ----
-keep class com.searchaid.domain.model.** { *; }
-keep class com.searchaid.data.local.entity.** { *; }

# ---- Enums ----
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ---- Lifecycle ----
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

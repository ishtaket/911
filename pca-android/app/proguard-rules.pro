# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclasseswithmembernames class * { @dagger.hilt.android.AndroidEntryPoint <init>(...); }

# Keep Room entities + DAOs
-keep class com.pca.assistant.data.db.entity.** { *; }
-keep interface com.pca.assistant.data.db.dao.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.pca.assistant.**$$serializer { *; }
-keepclassmembers class com.pca.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.pca.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Retrofit / OkHttp
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Keep model classes used by LLM contract
-keep class com.pca.assistant.llm.contract.** { *; }

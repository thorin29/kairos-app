# kotlinx.serialization: keep @Serializable metadata and generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.kairos.app.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kairos.app.data.remote.dto.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.kairos.app.data.remote.dto.** { *; }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**

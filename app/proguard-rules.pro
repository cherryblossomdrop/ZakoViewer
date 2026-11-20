# ProGuard rules for ZakoViewer
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room annotations and members
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all project classes and their members
-keep class moe.zako.viewer.** { *; }
-keepclassmembers class moe.zako.viewer.** { *; }

# Keep Gson library, TypeToken, Adapters and Serializers
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep fields annotated with SerializedName / Expose
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

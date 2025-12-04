# Hannsapp ProGuard Rules

# Keep Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep libsu
-keep class com.topjohnwu.superuser.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.hannsapp.fpscounter.data.** { *; }
-keep class com.hannsapp.fpscounter.models.** { *; }

# Keep services
-keep class com.hannsapp.fpscounter.services.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Lottie
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# General Android
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

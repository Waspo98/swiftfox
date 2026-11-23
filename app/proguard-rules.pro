# Keep JavaScript interface bridge methods intact under R8
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep native adblock-rust JNI bridge class and methods
-keep class app.overbay.swiftfox.features.adblock.NativeAdblockBridge { *; }

# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep JNA, Mozilla Application Services UniFFI bindings, and Mozilla Components
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class mozilla.appservices.** { *; }
-keepclassmembers class mozilla.appservices.** { *; }
-keep class mozilla.components.** { *; }
-dontwarn com.sun.jna.**
-dontwarn org.mozilla.**

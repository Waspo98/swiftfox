# Keep JavaScript interface bridge methods intact under R8
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep native adblock-rust JNI bridge class and methods
-keep class app.overbay.swiftfox.features.adblock.NativeAdblockBridge { *; }

# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

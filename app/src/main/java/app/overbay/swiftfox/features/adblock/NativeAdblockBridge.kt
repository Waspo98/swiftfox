package app.overbay.swiftfox.features.adblock

/**
 * JNI bridge to the native Rust `swiftfox_adblock` (`adblock-rust`) shared library.
 */
object NativeAdblockBridge {

    val isLoaded: Boolean = runCatching {
        System.loadLibrary("swiftfox_adblock")
    }.isSuccess

    @JvmStatic
    external fun nativeCreateFromRules(rulesText: String): Long

    @JvmStatic
    external fun nativeDeserialize(data: ByteArray): Long

    @JvmStatic
    external fun nativeSerialize(handle: Long): ByteArray?

    @JvmStatic
    external fun nativeCheckNetworkRequest(
        handle: Long,
        url: String,
        sourceUrl: String,
        requestType: String
    ): Boolean

    @JvmStatic
    external fun nativeCosmeticForUrl(handle: Long, url: String): String?

    @JvmStatic
    external fun nativeHiddenClassIdSelectors(
        handle: Long,
        classesJson: String,
        idsJson: String,
        exceptionsJson: String
    ): String?

    @JvmStatic
    external fun nativeDestroy(handle: Long)
}

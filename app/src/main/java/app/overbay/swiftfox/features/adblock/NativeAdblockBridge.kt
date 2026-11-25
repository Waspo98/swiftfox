package app.overbay.swiftfox.features.adblock

/**
 * JNI bridge to the native Rust `swiftfox_adblock` (`adblock-rust`) shared library.
 */
object NativeAdblockBridge {

    const val TYPE_OTHER = 0
    const val TYPE_SCRIPT = 1
    const val TYPE_STYLESHEET = 2
    const val TYPE_IMAGE = 3
    const val TYPE_FONT = 4
    const val TYPE_MEDIA = 5
    const val TYPE_XHR = 6
    const val TYPE_SUBDOCUMENT = 7
    const val TYPE_DOCUMENT = 8

    val isLoaded: Boolean = runCatching {
        System.loadLibrary("swiftfox_adblock")
    }.isSuccess

    fun codeForRequestType(requestType: String): Int = when (requestType) {
        "script" -> TYPE_SCRIPT
        "stylesheet" -> TYPE_STYLESHEET
        "image" -> TYPE_IMAGE
        "font" -> TYPE_FONT
        "media" -> TYPE_MEDIA
        "xmlhttprequest" -> TYPE_XHR
        "subdocument" -> TYPE_SUBDOCUMENT
        "document" -> TYPE_DOCUMENT
        else -> TYPE_OTHER
    }

    @JvmStatic
    external fun nativeCreateFromRules(rulesText: String): Long

    @JvmStatic
    external fun nativeCreateFromFiles(starterText: String, filePaths: Array<String>): Long

    @JvmStatic
    external fun nativeDeserializeFromFile(path: String): Long

    @JvmStatic
    external fun nativeSerializeToFile(handle: Long, path: String): Boolean

    @JvmStatic
    external fun nativeCheckNetworkRequestCode(
        handle: Long,
        url: String,
        sourceUrl: String,
        requestTypeCode: Int
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

package app.overbay.swiftfox.features.userscripts

/**
 * Injection timing phase supported by the Swiftfox `.user.js` runtime.
 */
enum class UserscriptRunAt(val headerValue: String) {
    DOCUMENT_START("document-start"),
    DOCUMENT_END("document-end"),
    DOCUMENT_IDLE("document-idle");

    companion object {
        fun fromHeader(raw: String?): UserscriptRunAt {
            val normalized = raw?.trim()?.lowercase() ?: return DOCUMENT_END
            return entries.firstOrNull { it.headerValue == normalized } ?: DOCUMENT_END
        }
    }
}

/**
 * Parsed metadata extracted from a `// ==UserScript==` header block.
 */
data class ParsedUserscriptMetadata(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matches: List<String>,
    val excludes: List<String>,
    val runAt: UserscriptRunAt,
    val grants: List<String>
)

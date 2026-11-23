package app.overbay.swiftfox.features.userscripts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Parses `// ==UserScript== ... // ==/UserScript==` metadata blocks and handles JSON serialization
 * of list metadata fields (`matchesJson`, `excludesJson`, `grantsJson`).
 */
object UserscriptParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val blockStartRegex = Regex("""^\s*//\s*==UserScript==\s*$""")
    private val blockEndRegex = Regex("""^\s*//\s*==/UserScript==\s*$""")
    private val directiveRegex = Regex("""^\s*//\s*@([A-Za-z0-9_-]+)(?:\s+(.*))?$""")

    fun parse(
        code: String,
        fallbackName: String = "Untitled Userscript"
    ): ParsedUserscriptMetadata {
        val cleanCode = code.removePrefix("\uFEFF")
        val lines = cleanCode.replace("\r\n", "\n").replace("\r", "\n").split('\n')

        var inBlock = false
        var name: String? = null
        var namespace: String? = null
        var version: String? = null
        var description: String? = null
        var runAtRaw: String? = null
        val matches = LinkedHashSet<String>()
        val excludes = LinkedHashSet<String>()
        val grants = LinkedHashSet<String>()
        val connects = LinkedHashSet<String>()
        val unsupported = LinkedHashSet<String>()

        for (line in lines) {
            if (!inBlock) {
                if (blockStartRegex.matches(line)) {
                    inBlock = true
                }
                continue
            }
            if (blockEndRegex.matches(line)) {
                break
            }
            val match = directiveRegex.matchEntire(line) ?: continue
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues.getOrElse(2) { "" }.trim()
            if (value.isEmpty()) continue

            when (key) {
                "name" -> if (name == null) name = value
                "namespace" -> if (namespace == null) namespace = value
                "version" -> if (version == null) version = value
                "description" -> if (description == null) description = value
                "match", "include" -> matches.add(value)
                "exclude", "exclude-match" -> excludes.add(value)
                "run-at" -> runAtRaw = value
                "grant" -> grants.add(value)
                "connect" -> connects.add(value.lowercase())
                "require", "resource" -> unsupported.add("@$key")
            }
        }

        return ParsedUserscriptMetadata(
            name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            namespace = namespace.orEmpty(),
            version = version?.takeIf { it.isNotBlank() } ?: "1.0",
            description = description.orEmpty(),
            matches = matches.toList(),
            excludes = excludes.toList(),
            runAt = UserscriptRunAt.fromHeader(runAtRaw),
            grants = grants.toList(),
            connects = connects.toList(),
            unsupportedDirectives = unsupported.toList()
        )
    }

    fun encodeStringList(items: List<String>): String = json.encodeToString(items)

    fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }
}

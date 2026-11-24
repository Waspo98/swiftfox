package app.overbay.swiftfox.features.icons

import java.text.BreakIterator
import java.util.Locale

/**
 * Unified encoding for user-selected custom icons across Workspaces, Essentials, and Tabs.
 *
 * Wire format:
 * - `null` / blank -> [None] (fall back to website favicon or letter monogram)
 * - `"emoji:<graphemes>"` -> [Emoji]
 * - `"image:<filename>"` -> [LocalImage] stored under `filesDir/custom_icons/<filename>`
 * - `"icon:<key>"` or plain legacy key (e.g. `"home"`, `"work"`) -> [MaterialIcon]
 */
sealed class CustomIconSpec {
    data object None : CustomIconSpec()
    data class MaterialIcon(val key: String) : CustomIconSpec()
    data class Emoji(val text: String) : CustomIconSpec()
    data class LocalImage(val fileName: String) : CustomIconSpec()

    fun encode(): String? = when (this) {
        None -> null
        is MaterialIcon -> key
        is Emoji -> "$EMOJI_PREFIX$text"
        is LocalImage -> "$IMAGE_PREFIX$fileName"
    }

    /**
     * Encodes an explicitly user-chosen icon, prefixing [MaterialIcon] with [ICON_PREFIX]
     * so sync engines (e.g. Zen Spaces) can distinguish manual icon overrides from synced defaults.
     */
    fun encodeExplicit(): String? = when (this) {
        None -> null
        is MaterialIcon -> "$ICON_PREFIX$key"
        is Emoji -> "$EMOJI_PREFIX$text"
        is LocalImage -> "$IMAGE_PREFIX$fileName"
    }

    companion object {
        const val EMOJI_PREFIX = "emoji:"
        const val IMAGE_PREFIX = "image:"
        const val ICON_PREFIX = "icon:"

        fun isExplicitCustomIcon(raw: String?): Boolean {
            val trimmed = raw?.trim().orEmpty()
            return trimmed.startsWith(EMOJI_PREFIX) ||
                trimmed.startsWith(IMAGE_PREFIX) ||
                trimmed.startsWith(ICON_PREFIX)
        }

        val BUILTIN_ICON_KEYS: List<String> = listOf(
            "home", "work", "book", "code", "shop", "star",
            "layers", "rocket", "bolt", "globe", "music", "video",
            "games", "news", "mail", "chat", "cloud", "server",
            "terminal", "finance", "fitness", "food", "travel", "school",
            "science", "camera", "palette", "heart", "bookmark", "shield",
            "wrench", "folder"
        )

        val QUICK_EMOJIS: List<String> = listOf(
            "🦊", "🏠", "💼", "📚", "💻", "🎮",
            "🎵", "🎬", "🛒", "⚡", "🌐", "🔥",
            "⭐", "💡", "🛠️", "📰", "🚀", "🔒"
        )

        fun parse(raw: String?): CustomIconSpec {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return None
            return when {
                trimmed.startsWith(EMOJI_PREFIX) -> {
                    val value = trimToMaxGraphemes(trimmed.removePrefix(EMOJI_PREFIX), maxGraphemes = 2)
                    if (value.isEmpty()) None else Emoji(value)
                }
                trimmed.startsWith(IMAGE_PREFIX) -> {
                    val fileName = sanitizeImageFileName(trimmed.removePrefix(IMAGE_PREFIX))
                    if (fileName.isEmpty()) None else LocalImage(fileName)
                }
                trimmed.startsWith(ICON_PREFIX) -> {
                    val key = trimmed.removePrefix(ICON_PREFIX).trim().lowercase(Locale.ROOT)
                    if (key.isEmpty()) None else MaterialIcon(key)
                }
                else -> MaterialIcon(trimmed.lowercase(Locale.ROOT))
            }
        }

        fun emojiSpec(rawInput: String): Emoji? {
            val clean = trimToMaxGraphemes(rawInput, maxGraphemes = 2)
            return if (clean.isEmpty()) null else Emoji(clean)
        }

        fun trimToMaxGraphemes(input: String, maxGraphemes: Int = 2): String {
            val cleaned = input.trim()
            if (cleaned.isEmpty() || maxGraphemes <= 0) return ""
            val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
            iterator.setText(cleaned)
            var count = 0
            var end = 0
            var next = iterator.next()
            while (next != BreakIterator.DONE && count < maxGraphemes) {
                end = next
                count++
                next = iterator.next()
            }
            return cleaned.substring(0, end.coerceAtMost(cleaned.length)).trim()
        }

        fun sanitizeImageFileName(raw: String): String {
            val clean = raw.trim()
            if (clean.isEmpty()) return ""
            if (clean.contains('/') || clean.contains('\\') || clean.contains("..")) return ""
            return clean.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
        }
    }
}

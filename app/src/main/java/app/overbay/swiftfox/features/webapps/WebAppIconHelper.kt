package app.overbay.swiftfox.features.webapps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.webkit.CookieManager
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.graphics.drawable.IconCompat
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.icons.FaviconRepository
import app.overbay.swiftfox.ui.theme.spaceIconFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

/**
 * Downloads, caches, and renders high-resolution icons and Android adaptive launcher icons
 * for installed PWAs and home-screen web shortcuts.
 */
class WebAppIconHelper(
    private val context: Context,
    private val httpClientProvider: () -> OkHttpClient,
    private val faviconRepository: FaviconRepository
) {

    private val iconsDir: File by lazy {
        File(context.filesDir, "pwa_icons").apply { mkdirs() }
    }

    suspend fun fetchBestIconBitmap(
        iconUrl: String?,
        pageUrl: String,
        cookieHeader: String? = null
    ): Bitmap? {
        if (!iconUrl.isNullOrBlank()) {
            val fromData = decodeDataImageUrl(iconUrl)
            if (fromData != null) return fromData

            val downloaded = downloadBitmap(iconUrl, cookieHeader)
            if (downloaded != null) return downloaded
        }
        return faviconRepository.getCachedFavicon(pageUrl)
            ?: faviconRepository.getOrFetchFavicon(pageUrl)
    }

    suspend fun resolveDisplayBitmap(
        appId: String?,
        iconPath: String?,
        iconUrl: String?,
        pageUrl: String,
        customIconSpec: String?,
        fallbackLabel: String,
        backgroundColorHex: String?
    ): Bitmap {
        val spec = CustomIconSpec.parse(customIconSpec)
        when (spec) {
            is CustomIconSpec.LocalImage -> {
                faviconRepository.loadCustomImage(spec.fileName)?.let { return it }
            }
            is CustomIconSpec.Emoji -> {
                return renderEmojiBitmap(spec.text, backgroundColorHex)
            }
            is CustomIconSpec.MaterialIcon -> {
                return renderMaterialIconBitmap(spec.key, backgroundColorHex)
            }
            CustomIconSpec.None -> Unit
        }

        if (!iconPath.isNullOrBlank()) {
            loadCachedIconFile(iconPath)?.let { return it }
        }
        if (!appId.isNullOrBlank()) {
            val candidateFile = File(iconsDir, "$appId.png")
            if (candidateFile.isFile) {
                loadCachedIconFile(candidateFile.absolutePath)?.let { return it }
            }
        }

        val cookie = runCatching { CookieManager.getInstance().getCookie(iconUrl ?: pageUrl) }.getOrNull()
        val fetched = fetchBestIconBitmap(iconUrl = iconUrl, pageUrl = pageUrl, cookieHeader = cookie)
        if (fetched != null) return fetched

        return renderMonogramBitmap(fallbackLabel, backgroundColorHex)
    }

    suspend fun saveAppIconBitmap(appId: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(iconsDir, "$appId.png")
            val scaled = scaleDownIfLarge(bitmap, MAX_STORED_ICON_PX)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            outFile.absolutePath
        }.getOrNull()
    }

    fun loadCachedIconFile(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun deleteCachedIconFile(appId: String, iconPath: String?) {
        if (!iconPath.isNullOrBlank()) {
            runCatching { File(iconPath).takeIf { it.isFile }?.delete() }
        }
        runCatching { File(iconsDir, "$appId.png").takeIf { it.isFile }?.delete() }
    }

    internal fun deleteCustomImageIfUnused(encodedSpec: String?) {
        faviconRepository.deleteCustomImageIfUnused(encodedSpec)
    }

    fun buildAdaptiveShortcutIcon(
        source: Bitmap,
        maskable: Boolean,
        backgroundColorHex: String?
    ): IconCompat {
        if (maskable) {
            val full = scaleSquare(source, ADAPTIVE_SIZE_PX)
            return IconCompat.createWithAdaptiveBitmap(full)
        }
        val canvasBmp = Bitmap.createBitmap(ADAPTIVE_SIZE_PX, ADAPTIVE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val bgColor = resolveBackdropColor(source, backgroundColorHex)
        canvas.drawColor(bgColor)

        val safeMargin = (ADAPTIVE_SIZE_PX * (1f - ADAPTIVE_SAFE_FRACTION) / 2f)
        val targetSize = (ADAPTIVE_SIZE_PX * ADAPTIVE_SAFE_FRACTION).toInt().coerceAtLeast(1)
        val srcRect = Rect(0, 0, source.width, source.height)
        val dstRect = fitRectCentered(source.width, source.height, safeMargin, safeMargin, targetSize.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, srcRect, dstRect, paint)
        return IconCompat.createWithAdaptiveBitmap(canvasBmp)
    }

    private suspend fun downloadBitmap(url: String, cookieHeader: String?): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
                )
                .apply {
                    if (!cookieHeader.isNullOrBlank()) {
                        header("Cookie", cookieHeader)
                    }
                }
                .build()
            httpClientProvider().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("svg", ignoreCase = true)) return@runCatching null
                val body = resp.body
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_DOWNLOAD_BYTES) return@runCatching null
                decodeBoundedBitmap(bytes)
            }
        }.getOrNull()
    }

    private fun decodeDataImageUrl(dataUrl: String): Bitmap? {
        if (!dataUrl.startsWith("data:image/", ignoreCase = true)) return null
        if (dataUrl.startsWith("data:image/svg", ignoreCase = true)) return null
        val commaIdx = dataUrl.indexOf(',')
        if (commaIdx <= 5) return null
        val header = dataUrl.substring(5, commaIdx)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val rawBase64 = dataUrl.substring(commaIdx + 1)
        if (rawBase64.length > MAX_DOWNLOAD_BYTES * 2) return null
        return runCatching {
            val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
            decodeBoundedBitmap(bytes)
        }.getOrNull()
    }

    private fun decodeBoundedBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxEdge / (sample * 2) >= MAX_STORED_ICON_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun scaleDownIfLarge(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxEdge && h <= maxEdge) return src
        val scale = maxEdge.toFloat() / maxOf(w, h).toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun scaleSquare(src: Bitmap, sizePx: Int): Bitmap {
        if (src.width == sizePx && src.height == sizePx) return src
        return Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
    }

    private fun resolveBackdropColor(source: Bitmap, backgroundColorHex: String?): Int {
        WebAppManifestParser.parseColorInt(backgroundColorHex)?.let { parsed ->
            if ((parsed ushr 24) >= 0xE0) return parsed
        }
        val avgLuminance = estimateForegroundLuminance(source)
        return if (avgLuminance > 0.72f) 0xFF1C1B1F.toInt() else Color.WHITE
    }

    private fun estimateForegroundLuminance(bmp: Bitmap): Float {
        val stepX = (bmp.width / 8).coerceAtLeast(1)
        val stepY = (bmp.height / 8).coerceAtLeast(1)
        var sum = 0f
        var count = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val px = bmp.getPixel(x, y)
                val a = (px ushr 24) and 0xFF
                if (a > 64) {
                    val r = ((px ushr 16) and 0xFF) / 255f
                    val g = ((px ushr 8) and 0xFF) / 255f
                    val b = (px and 0xFF) / 255f
                    sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else sum / count
    }

    private fun fitRectCentered(
        srcW: Int,
        srcH: Int,
        left: Float,
        top: Float,
        boxSize: Float
    ): RectF {
        if (srcW <= 0 || srcH <= 0) return RectF(left, top, left + boxSize, top + boxSize)
        val scale = minOf(boxSize / srcW.toFloat(), boxSize / srcH.toFloat())
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = left + (boxSize - dw) / 2f
        val dy = top + (boxSize - dh) / 2f
        return RectF(dx, dy, dx + dw, dy + dh)
    }

    private fun renderMonogramBitmap(label: String, bgHex: String?): Bitmap {
        val bmp = Bitmap.createBitmap(ADAPTIVE_SIZE_PX, ADAPTIVE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = WebAppManifestParser.parseColorInt(bgHex) ?: 0xFFE86A17.toInt()
        canvas.drawColor(bg)
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "W"
        val fg = if (WebAppManifestParser.isLightColor(bg)) 0xFF1C1B1F.toInt() else Color.WHITE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = ADAPTIVE_SIZE_PX * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = paint.fontMetrics
        val baseline = ADAPTIVE_SIZE_PX / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(letter, ADAPTIVE_SIZE_PX / 2f, baseline, paint)
        return bmp
    }

    private fun renderEmojiBitmap(emoji: String, bgHex: String?): Bitmap {
        val bmp = Bitmap.createBitmap(ADAPTIVE_SIZE_PX, ADAPTIVE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = WebAppManifestParser.parseColorInt(bgHex) ?: 0xFF23201E.toInt()
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = ADAPTIVE_SIZE_PX * 0.44f
            textAlign = Paint.Align.CENTER
        }
        val fm = paint.fontMetrics
        val baseline = ADAPTIVE_SIZE_PX / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(emoji, ADAPTIVE_SIZE_PX / 2f, baseline, paint)
        return bmp
    }

    private fun renderMaterialIconBitmap(key: String, bgHex: String?): Bitmap {
        val bmp = Bitmap.createBitmap(ADAPTIVE_SIZE_PX, ADAPTIVE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = WebAppManifestParser.parseColorInt(bgHex) ?: 0xFF2B221B.toInt()
        canvas.drawColor(bg)
        val fg = if (WebAppManifestParser.isLightColor(bg)) 0xFFE86A17.toInt() else 0xFFFFB68B.toInt()
        val vec = spaceIconFor(key)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            style = Paint.Style.FILL
        }
        val targetSize = ADAPTIVE_SIZE_PX * 0.44f
        val offset = (ADAPTIVE_SIZE_PX - targetSize) / 2f
        val matrix = Matrix().apply {
            setScale(targetSize / vec.viewportWidth.coerceAtLeast(1f), targetSize / vec.viewportHeight.coerceAtLeast(1f))
            postTranslate(offset, offset)
        }
        var drewAny = false
        fun drawGroup(group: VectorGroup) {
            for (i in 0 until group.size) {
                when (val node: VectorNode = group[i]) {
                    is VectorPath -> {
                        val androidPath = node.pathData.toPath().asAndroidPath()
                        androidPath.transform(matrix)
                        canvas.drawPath(androidPath, paint)
                        drewAny = true
                    }
                    is VectorGroup -> drawGroup(node)
                }
            }
        }
        drawGroup(vec.root)
        if (!drewAny) {
            return renderMonogramBitmap(key.uppercase(Locale.ROOT), bgHex)
        }
        return bmp
    }

    companion object {
        private const val ADAPTIVE_SIZE_PX = 432
        private const val ADAPTIVE_SAFE_FRACTION = 0.64f
        private const val MAX_STORED_ICON_PX = 432
        private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    }
}

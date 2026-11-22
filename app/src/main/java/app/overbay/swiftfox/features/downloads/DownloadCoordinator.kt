package app.overbay.swiftfox.features.downloads

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import app.overbay.swiftfox.data.db.DownloadRecordDao
import app.overbay.swiftfox.data.db.DownloadRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadCoordinator(
    private val context: Context,
    private val downloadRecordDao: DownloadRecordDao
) {

    suspend fun enqueueHttpDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?
    ): Result<DownloadRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val safeMime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val fileName = URLUtil.guessFileName(url, contentDisposition, safeMime)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading with Swiftfox")
                setMimeType(safeMime)
                addRequestHeader("User-Agent", userAgent)
                val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            val record = DownloadRecordEntity(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                url = url,
                mimeType = safeMime,
                systemDownloadId = downloadId,
                status = "Queued"
            )
            downloadRecordDao.upsert(record)
            record
        }
    }

    suspend fun saveBase64Blob(
        fileName: String,
        mimeType: String,
        base64Payload: String,
        sourceUrl: String
    ): Result<DownloadRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase64 = base64Payload.substringAfter("base64,", base64Payload)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val safeMime = mimeType.ifBlank { "application/octet-stream" }
            val safeName = fileName.ifBlank { "swiftfox_download_${System.currentTimeMillis()}" }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, safeMime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore Downloads entry")

            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
            } ?: error("Could not open Downloads output stream")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            val record = DownloadRecordEntity(
                id = UUID.randomUUID().toString(),
                fileName = safeName,
                url = sourceUrl,
                mimeType = safeMime,
                systemDownloadId = -1L,
                status = "Saved to Downloads"
            )
            downloadRecordDao.upsert(record)
            record
        }
    }
}

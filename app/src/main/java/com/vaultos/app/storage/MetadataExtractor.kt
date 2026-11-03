package com.vaultos.app.storage

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.vaultos.app.data.ItemMetadata
import com.vaultos.app.data.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/** Explicit, on-device metadata reader. It never uploads content or changes source files. */
object MetadataExtractor {
    suspend fun extract(context: Context, item: VaultItem): ItemMetadata = withContext(Dispatchers.IO) {
        val file = item.archivedPath?.let(::File)?.takeIf { it.isFile && it.canRead() }
        val uri = item.originalUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        var title: String? = null
        var author: String? = null
        var subject: String? = null
        var pageCount: Int? = null
        var width: Int? = null
        var height: Int? = null
        var duration: Long? = null
        var capturedAt: String? = null
        var camera: String? = null
        var packageName: String? = null
        var packageVersion: String? = null
        var packageVersionCode: Long? = null

        fun open(): InputStream? = when {
            file != null -> runCatching { file.inputStream() }.getOrNull()
            uri != null -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            else -> null
        }

        when {
            item.mimeType.startsWith("image/", true) -> {
                open()?.use { input ->
                    runCatching {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(input, null, options)
                        width = options.outWidth.takeIf { it > 0 }
                        height = options.outHeight.takeIf { it > 0 }
                    }
                }
                open()?.use { input ->
                    runCatching {
                        val exif = ExifInterface(input)
                        capturedAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                        camera = exif.getAttribute(ExifInterface.TAG_MODEL)
                    }
                }
            }
            item.mimeType.equals("application/pdf", true) || item.name.endsWith(".pdf", true) -> {
                open()?.use { input ->
                    val bytes = input.readBounded(MAX_METADATA_BYTES)
                    val parsed = MetadataTextParser.readPdfInfo(String(bytes, StandardCharsets.ISO_8859_1))
                    title = parsed.title
                    author = parsed.author
                    subject = parsed.subject
                }
                pageCount = runCatching {
                    val descriptor = when {
                        file != null -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        uri != null -> context.contentResolver.openFileDescriptor(uri, "r")
                        else -> null
                    }
                    descriptor?.use { fd -> PdfRenderer(fd).use { it.pageCount } }
                }.getOrNull()
            }
            item.name.endsWith(".docx", true) || item.name.endsWith(".odt", true) -> {
                open()?.use { input ->
                    val coreXml = ZipInputStream(input).use { zip ->
                        var result: String? = null
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == "docProps/core.xml" || entry.name == "meta.xml") {
                                result = String(zip.readBounded(MAX_OFFICE_XML_BYTES), StandardCharsets.UTF_8)
                                break
                            }
                        }
                        result
                    }
                    if (coreXml != null) {
                        title = MetadataTextParser.readXmlValue(coreXml, "title")
                        author = MetadataTextParser.readXmlValue(coreXml, "creator")
                            ?: MetadataTextParser.readXmlValue(coreXml, "initial-creator")
                        subject = MetadataTextParser.readXmlValue(coreXml, "subject")
                    }
                }
            }
            item.mimeType.startsWith("video/", true) || item.mimeType.startsWith("audio/", true) -> {
                val retriever = MediaMetadataRetriever()
                try {
                    when {
                        file != null -> retriever.setDataSource(file.absolutePath)
                        uri != null -> retriever.setDataSource(context, uri)
                    }
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    capturedAt = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                } catch (_: Exception) {
                    // Some containers/codecs expose no metadata. Keep the rest of the item usable.
                } finally {
                    runCatching { retriever.release() }
                }
            }
            item.name.endsWith(".apk", true) && file != null -> {
                val info = runCatching {
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    }
                }.getOrNull()
                packageName = info?.packageName
                packageVersion = info?.versionName
                packageVersionCode = info?.let {
                    if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
                }
                title = packageName
            }
            item.mimeType.startsWith("text/", true) || item.category == "Text" || item.category == "Code" -> {
                val firstLine = item.textContent?.lineSequence()?.firstOrNull()?.trim()
                title = firstLine?.takeIf { it.isNotBlank() && it != item.name }?.take(200)
            }
        }

        ItemMetadata(
            itemId = item.id,
            title = title.cleanOrNull(), author = author.cleanOrNull(), subject = subject.cleanOrNull(),
            pageCount = pageCount, widthPx = width, heightPx = height, durationMs = duration,
            capturedAt = capturedAt.cleanOrNull(), cameraModel = camera.cleanOrNull(), packageName = packageName,
            packageVersionName = packageVersion, packageVersionCode = packageVersionCode,
            extractedAt = System.currentTimeMillis()
        )
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }?.take(512)

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var remaining = limit
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private const val MAX_METADATA_BYTES = 4 * 1024 * 1024
    private const val MAX_OFFICE_XML_BYTES = 1024 * 1024
}

/** Small, bounded parsers for the uncompressed metadata blocks used by common formats. */
internal object MetadataTextParser {
    data class PdfInfo(val title: String?, val author: String?, val subject: String?)

    fun readPdfInfo(source: String): PdfInfo = PdfInfo(
        title = readPdfValue(source, "Title"),
        author = readPdfValue(source, "Author"),
        subject = readPdfValue(source, "Subject")
    )

    private fun readPdfValue(source: String, key: String): String? {
        val literal = Regex("/$key\\s*\\((.{1,1024}?)\\)", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.getOrNull(1)
            ?.replace("\\\\(", "(")?.replace("\\\\)", ")")?.replace("\\\\\\\\", "\\")
            ?.replace(Regex("\\s+"), " ")?.trim()
        if (!literal.isNullOrBlank()) return literal
        val hex = Regex("/$key\\s*<([0-9A-Fa-f\\s]{2,2048})>").find(source)?.groupValues?.getOrNull(1) ?: return null
        val bytes = hex.filterNot(Char::isWhitespace).chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        val decoded = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        } else String(bytes, StandardCharsets.ISO_8859_1)
        return decoded.trim().takeIf { it.isNotBlank() }
    }

    fun readXmlValue(xml: String, localName: String): String? {
        val escaped = Regex("<[^>]*:?$localName(?:\\s[^>]*)?>(.*?)</[^>]*:?$localName\\s*>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.getOrNull(1) ?: return null
        return escaped.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").trim().takeIf { it.isNotBlank() }
    }
}

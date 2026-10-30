package com.vaultos.app.util

/** Deterministic, offline file classification. Prefer reliable filename signatures
 * over broad MIME guesses (for example, YAML is often reported as text/plain). */
object FileClassifier {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff", "svg", "avif")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "mpeg", "mpg")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "mid", "midi")
    private val documentExtensions = setOf("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "ppt", "pptx", "epub", "csv")
    private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz")
    private val codeExtensions = setOf("kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "json", "xml", "html", "htm", "css", "yml", "yaml", "sh", "sql", "gradle", "toml", "properties", "md", "ipynb", "c", "h", "cpp", "hpp", "rs", "go", "swift", "dart")
    private val textExtensions = setOf("txt", "log", "text", "ini", "cfg", "conf")

    fun classify(name: String, mimeType: String, isText: Boolean = false): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("apk", "aab", "apks", "xapk") || mimeType == "application/vnd.android.package-archive" -> "Apps & APKs"
            ext in imageExtensions || mimeType.startsWith("image/") -> "Images"
            ext in videoExtensions || mimeType.startsWith("video/") -> "Videos"
            ext in audioExtensions || mimeType.startsWith("audio/") -> "Audio"
            ext in documentExtensions || mimeType == "application/pdf" -> "Documents"
            ext in archiveExtensions -> "Archives"
            ext in codeExtensions -> "Code"
            ext in textExtensions || isText || mimeType.startsWith("text/") -> "Text"
            else -> "Other"
        }
    }

    fun safeFileName(name: String): String = name
        .replace(Regex("[/\\\\\\u0000-\\u001f]"), "_")
        .trim()
        .take(180)
        .ifBlank { "Imported_${System.currentTimeMillis()}" }
}

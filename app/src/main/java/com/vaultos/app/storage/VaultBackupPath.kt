package com.vaultos.app.storage

import java.io.File

/**
 * Resolves an untrusted manifest path beneath the app-private archive.
 *
 * The manifest stores slash-separated relative paths. Absolute paths, drive-prefixed
 * paths, traversal segments, empty segments and canonical paths that escape through a
 * symlink are rejected. The final target does not need to exist (for example, a
 * trashed item's previous location may currently be empty).
 */
internal fun resolveVaultRelativePath(vaultRoot: File, relativePath: String?): File? {
    if (relativePath.isNullOrBlank()) return null
    val normalized = relativePath.replace('\\\\', '/')
    if (normalized.startsWith('/') ||
        normalized.matches(Regex("^[A-Za-z]:.*")) ||
        normalized.any { it == '\\u0000' }
    ) return null

    val segments = normalized.split('/')
    if (segments.any { it.isEmpty() || it == ".." }) return null
    if (segments.all { it == "." }) return null

    return runCatching {
        val root = vaultRoot.canonicalFile
        val candidate = File(root, normalized).canonicalFile
        if (root.isDirectory && candidate != root && candidate.toPath().startsWith(root.toPath())) {
            candidate
        } else {
            null
        }
    }.getOrNull()
}

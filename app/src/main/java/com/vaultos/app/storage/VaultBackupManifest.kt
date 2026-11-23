package com.vaultos.app.storage

internal fun isSupportedBackupFormat(format: String): Boolean =
    format == "vaultos-backup-v1" || format == "vaultos-backup-v2"

internal fun isValidBackupSha256(value: String?): Boolean =
    value != null && value.matches(Regex("^[a-fA-F0-9]{64}$"))

internal fun isSafeBackupRelativePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') || path.contains('\\')) return false
    if (path.any { it.code < 0x20 || it.code == 0x7f }) return false
    val segments = path.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
    if (path.matches(Regex("^[A-Za-z]:.*"))) return false
    return true
}

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
internal fun validateBackupManifestRecords(records: org.json.JSONArray) {
    val ids = mutableSetOf<String>()
    for (i in 0 until records.length()) {
        val row = records.optJSONObject(i)
            ?: throw IllegalArgumentException("Backup manifest contains an invalid record")
        val id = row.optString("id").takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalArgumentException("Backup manifest record has no ID")
        require(ids.add(id)) { "Backup manifest contains duplicate record IDs" }
        val relative = row.optString("relativePath").takeIf { it.isNotBlank() && it != "null" }
        if (relative != null) require(isSafeBackupRelativePath(relative)) {
            "Backup manifest contains an unsafe relative path"
        }
        val hash = row.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
        if (hash != null) require(isValidBackupSha256(hash)) {
            "Backup manifest contains an invalid SHA-256 value"
        }
    }
}

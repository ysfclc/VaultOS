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
        if (relative != null) {
            require(isValidBackupSha256(hash)) {
                "Backup manifest archived payload is missing a valid SHA-256 value"
            }
        } else if (hash != null) {
            require(isValidBackupSha256(hash)) {
                "Backup manifest contains an invalid SHA-256 value"
            }
        }
    }
}

/**
 * V2 organization data is consumed after payload files have been copied. Validate its
 * complete shape before restore starts so malformed rows cannot cause a late,
 * partially-applied restore.
 */
internal fun validateBackupManifestV2Sections(root: org.json.JSONObject) {
    fun rows(section: String, requiredFields: List<String>, uniqueField: String? = null) {
        val array = root.optJSONArray(section)
            ?: throw IllegalArgumentException("V2 backup manifest is missing $section")
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index)
                ?: throw IllegalArgumentException("V2 backup manifest contains an invalid $section record")
            for (field in requiredFields) {
                val value = row.optString(field)
                require(value.isNotBlank() && value != "null") {
                    "V2 backup manifest $section record is missing $field"
                }
            }
            if (uniqueField != null) {
                val value = row.optString(uniqueField)
                require(seen.add(value)) {
                    "V2 backup manifest contains duplicate $section $uniqueField values"
                }
            }
        }
    }

    rows("userTags", listOf("id", "name", "normalizedName"), "id")
    rows("itemTags", listOf("itemId", "tagId"))
    rows("userCollections", listOf("id", "name", "normalizedName"), "id")
    rows("collectionItems", listOf("collectionId", "itemId"))
    rows("savedSearches", listOf("id", "name", "normalizedName", "query"), "id")
    rows("itemMetadata", listOf("itemId"), "itemId")
}

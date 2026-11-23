package com.vaultos.app.storage

internal fun isSupportedBackupFormat(format: String): Boolean =
    format == "vaultos-backup-v1" || format == "vaultos-backup-v2"

internal fun isValidBackupSha256(value: String?): Boolean =
    value != null && value.matches(Regex("^[a-fA-F0-9]{64}$"))

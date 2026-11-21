package com.vaultos.app.storage

import java.io.File

/**
 * Resolve a candidate archive path only when it names a regular file whose canonical
 * location remains inside the app-private VaultOS archive. Canonicalization rejects
 * traversal and symlink escapes before a FileProvider URI is issued.
 */
internal fun resolveArchivedVaultFile(vaultRoot: File, candidatePath: String?): File? {
    if (candidatePath.isNullOrBlank()) return null
    return runCatching {
        val root = vaultRoot.canonicalFile
        val candidate = File(candidatePath).canonicalFile
        if (candidate.isFile && candidate.toPath().startsWith(root.toPath())) candidate else null
    }.getOrNull()
}

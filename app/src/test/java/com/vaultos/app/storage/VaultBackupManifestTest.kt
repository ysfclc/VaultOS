package com.vaultos.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBackupManifestTest {
    @Test
    fun acceptsOnlySupportedManifestVersions() {
        assertTrue(isSupportedBackupFormat("vaultos-backup-v1"))
        assertTrue(isSupportedBackupFormat("vaultos-backup-v2"))
        assertFalse(isSupportedBackupFormat(""))
        assertFalse(isSupportedBackupFormat("vaultos-backup-v3"))
    }

    @Test
    fun acceptsOnlyCompleteSha256Digests() {
        assertTrue(isValidBackupSha256("a".repeat(64)))
        assertTrue(isValidBackupSha256("0123456789abcdef".repeat(4)))
        assertFalse(isValidBackupSha256(null))
        assertFalse(isValidBackupSha256("a".repeat(63)))
        assertFalse(isValidBackupSha256("g".repeat(64)))
    }
    @Test
    fun validatesBackupRelativePathsWithoutTraversalOrAmbiguousSegments() {
        assertTrue(isSafeBackupRelativePath("documents/report.pdf"))
        assertTrue(isSafeBackupRelativePath("folder with spaces/note.txt"))
        assertTrue(isSafeBackupRelativePath("folder:name/note.txt"))
        assertFalse(isSafeBackupRelativePath(""))
        assertFalse(isSafeBackupRelativePath("/absolute/file"))
        assertFalse(isSafeBackupRelativePath("../escape"))
        assertFalse(isSafeBackupRelativePath("folder/../escape"))
        assertFalse(isSafeBackupRelativePath("folder//file"))
        assertFalse(isSafeBackupRelativePath("folder\\file"))
        assertFalse(isSafeBackupRelativePath("C:/drive/file"))
        assertFalse(isSafeBackupRelativePath("folder/./file"))
    }

    @Test
    fun validatesManifestRecordsAndRejectsDuplicateOrMissingIds() {
        val valid = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", "item-1").put("relativePath", "documents/a.pdf").put("sha256", "a".repeat(64)))
            .put(org.json.JSONObject().put("id", "item-2"))
        validateBackupManifestRecords(valid)

        val duplicate = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", "same"))
            .put(org.json.JSONObject().put("id", "same"))
        assertFailsWith<IllegalArgumentException> { validateBackupManifestRecords(duplicate) }

        val missingId = org.json.JSONArray().put(org.json.JSONObject().put("name", "missing-id"))
        assertFailsWith<IllegalArgumentException> { validateBackupManifestRecords(missingId) }

        val unsafePath = org.json.JSONArray().put(org.json.JSONObject().put("id", "bad-path").put("relativePath", "../escape"))
        assertFailsWith<IllegalArgumentException> { validateBackupManifestRecords(unsafePath) }

        val malformedHash = org.json.JSONArray().put(org.json.JSONObject().put("id", "bad-hash").put("sha256", "xyz"))
        assertFailsWith<IllegalArgumentException> { validateBackupManifestRecords(malformedHash) }

        val malformedRow = org.json.JSONArray().put("not an object")
        assertFailsWith<IllegalArgumentException> { validateBackupManifestRecords(malformedRow) }
    }
}

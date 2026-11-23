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
        assertTrue(isValidBackupSha256("A".repeat(64)))
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
        expectIllegalArgument { validateBackupManifestRecords(duplicate) }

        val missingId = org.json.JSONArray().put(org.json.JSONObject().put("name", "missing-id"))
        expectIllegalArgument { validateBackupManifestRecords(missingId) }

        val unsafePath = org.json.JSONArray().put(org.json.JSONObject().put("id", "bad-path").put("relativePath", "../escape"))
        expectIllegalArgument { validateBackupManifestRecords(unsafePath) }

        val malformedHash = org.json.JSONArray().put(org.json.JSONObject().put("id", "bad-hash").put("sha256", "xyz"))
        expectIllegalArgument { validateBackupManifestRecords(malformedHash) }

        val malformedRow = org.json.JSONArray().put("not an object")
        expectIllegalArgument { validateBackupManifestRecords(malformedRow) }
    }
    @Test
    fun validatesEveryV2OrganizationSectionBeforeRestore() {
        val valid = org.json.JSONObject()
            .put("userTags", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", "tag-1").put("name", "Work").put("normalizedName", "work")))
            .put("itemTags", org.json.JSONArray().put(org.json.JSONObject()
                .put("itemId", "item-1").put("tagId", "tag-1")))
            .put("userCollections", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", "collection-1").put("name", "Projects").put("normalizedName", "projects")))
            .put("collectionItems", org.json.JSONArray().put(org.json.JSONObject()
                .put("collectionId", "collection-1").put("itemId", "item-1")))
            .put("savedSearches", org.json.JSONArray().put(org.json.JSONObject()
                .put("id", "search-1").put("name", "PDFs").put("normalizedName", "pdfs").put("query", "type:pdf")))
            .put("itemMetadata", org.json.JSONArray().put(org.json.JSONObject().put("itemId", "item-1")))
        validateBackupManifestV2Sections(valid)

        val missingSection = org.json.JSONObject(valid.toString()).apply { remove("savedSearches") }
        expectIllegalArgument { validateBackupManifestV2Sections(missingSection) }

        val malformedRow = org.json.JSONObject(valid.toString()).apply {
            put("itemMetadata", org.json.JSONArray().put(org.json.JSONObject()))
        }
        expectIllegalArgument { validateBackupManifestV2Sections(malformedRow) }

        val duplicateId = org.json.JSONObject(valid.toString()).apply {
            put("userTags", org.json.JSONArray()
                .put(org.json.JSONObject().put("id", "tag-1").put("name", "A").put("normalizedName", "a"))
                .put(org.json.JSONObject().put("id", "tag-1").put("name", "B").put("normalizedName", "b")))
        }
        expectIllegalArgument { validateBackupManifestV2Sections(duplicateId) }

        val nonObjectRow = org.json.JSONObject(valid.toString()).apply {
            put("collectionItems", org.json.JSONArray().put("not-an-object"))
        }
        expectIllegalArgument { validateBackupManifestV2Sections(nonObjectRow) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        var caught = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Expected IllegalArgumentException", caught)
    }
}

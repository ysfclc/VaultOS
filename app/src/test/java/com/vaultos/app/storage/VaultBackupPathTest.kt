package com.vaultos.app.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Test

class VaultBackupPathTest {
    @Test
    fun resolvesNestedRelativePathEvenWhenTargetDoesNotExist() {
        val parent = Files.createTempDirectory("vault-backup-path").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val resolved = resolveVaultRelativePath(vault, "text/old-note.txt")
            assertNotNull(resolved)
            assertEquals(File(vault, "text/old-note.txt").canonicalFile, resolved)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsAbsoluteTraversalAndDrivePrefixedPaths() {
        val parent = Files.createTempDirectory("vault-backup-path").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside.txt")
            assertNull(resolveVaultRelativePath(vault, outside.absolutePath))
            assertNull(resolveVaultRelativePath(vault, "../outside.txt"))
            assertNull(resolveVaultRelativePath(vault, "text/../../outside.txt"))
            assertNull(resolveVaultRelativePath(vault, "C:/outside.txt"))
            assertNull(resolveVaultRelativePath(vault, "text//note.txt"))
            assertNull(resolveVaultRelativePath(vault, ""))
            assertNull(resolveVaultRelativePath(vault, null))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkParentThatEscapesVault() {
        val parent = Files.createTempDirectory("vault-backup-path").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside").apply { mkdirs() }
            val link = File(vault, "linked")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (failure: Exception) {
                Assume.assumeNoException("Host does not permit symbolic links", failure)
            }
            assertNull(resolveVaultRelativePath(vault, "linked/restore-target.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsVaultRootItself() {
        val parent = Files.createTempDirectory("vault-backup-path").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            assertNull(resolveVaultRelativePath(vault, "."))
        } finally {
            parent.deleteRecursively()
        }
    }
}

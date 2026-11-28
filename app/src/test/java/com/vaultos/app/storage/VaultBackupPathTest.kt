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
    fun rollbackRemovesOnlyCreatedFilesAndEmptyDirectories() {
        val parent = Files.createTempDirectory("vault-restore-rollback").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val createdDirectory = File(vault, "created/nested").apply { mkdirs() }
            val createdFile = File(createdDirectory, "new.bin").apply { writeText("new") }
            val preservedDirectory = File(vault, "pre-existing").apply { mkdirs() }
            val preservedFile = File(preservedDirectory, "keep.txt").apply { writeText("keep") }

            rollbackRestoreArtifacts(
                createdFiles = listOf(createdFile),
                createdDirectories = listOf(File(vault, "created"), createdDirectory, preservedDirectory)
            )

            org.junit.Assert.assertFalse(createdFile.exists())
            org.junit.Assert.assertFalse(createdDirectory.exists())
            org.junit.Assert.assertFalse(File(vault, "created").exists())
            org.junit.Assert.assertTrue(preservedFile.isFile)
            org.junit.Assert.assertEquals("keep", preservedFile.readText())
            org.junit.Assert.assertTrue(preservedDirectory.isDirectory)
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

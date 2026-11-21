package com.vaultos.app.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Test

class ArchivedVaultFileAccessTest {
    @Test
    fun resolvesRegularFileInsidePrivateVault() {
        val parent = Files.createTempDirectory("vault-file-access").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val archived = File(vault, "text/note.txt").apply {
                parentFile?.mkdirs()
                writeText("captured")
            }
            assertEquals(archived.canonicalFile, resolveArchivedVaultFile(vault, archived.path))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsOutsideAndTraversalPaths() {
        val parent = Files.createTempDirectory("vault-file-access").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside.txt").apply { writeText("not archived") }

            assertNull(resolveArchivedVaultFile(vault, outside.path))
            assertNull(resolveArchivedVaultFile(vault, File(vault, "../outside.txt").path))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsDirectoriesAndMissingPaths() {
        val parent = Files.createTempDirectory("vault-file-access").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val directory = File(vault, "folder").apply { mkdirs() }

            assertNull(resolveArchivedVaultFile(vault, directory.path))
            assertNull(resolveArchivedVaultFile(vault, null))
            assertNull(resolveArchivedVaultFile(vault, " "))
            assertNull(resolveArchivedVaultFile(vault, File(vault, "missing.txt").path))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkThatEscapesPrivateVault() {
        val parent = Files.createTempDirectory("vault-file-access").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside").apply { mkdirs() }
            val secret = File(outside, "secret.txt").apply { writeText("not in vault") }
            val link = File(vault, "linked-secret.txt")
            try {
                Files.createSymbolicLink(link.toPath(), secret.toPath())
            } catch (failure: UnsupportedOperationException) {
                Assume.assumeNoException("Host does not support symbolic links", failure)
            } catch (failure: SecurityException) {
                Assume.assumeNoException("Host does not permit symbolic links", failure)
            } catch (failure: IOException) {
                Assume.assumeNoException("Host could not create a symbolic link", failure)
            }

            assertNull(resolveArchivedVaultFile(vault, link.path))
        } finally {
            parent.deleteRecursively()
        }
    }
}

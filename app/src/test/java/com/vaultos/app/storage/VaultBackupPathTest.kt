package com.vaultos.app.storage

import java.io.File
import java.nio.file.Files
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class VaultBackupPathTest {
    @Test
    fun resolvesNestedPathInsideVault() {
        val parent = Files.createTempDirectory("vault-path-test").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val nested = File(vault, "text/notes/example.txt")
            assertEquals(nested.canonicalFile, resolveVaultRelativePath(vault, "text/notes/example.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversal() {
        val parent = Files.createTempDirectory("vault-path-test").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            assertNull(resolveVaultRelativePath(vault, "../outside.txt"))
            assertNull(resolveVaultRelativePath(vault, "text/../../outside.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsAbsolutePaths() {
        val parent = Files.createTempDirectory("vault-path-test").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside.txt")
            assertTrue(outside.isAbsolute)
            assertNull(resolveVaultRelativePath(vault, outside.absolutePath))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsVaultRootAndBlankPaths() {
        val parent = Files.createTempDirectory("vault-path-test").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            assertNull(resolveVaultRelativePath(vault, ""))
            assertNull(resolveVaultRelativePath(vault, "   "))
            assertNull(resolveVaultRelativePath(vault, "."))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkThatEscapesVault() {
        val parent = Files.createTempDirectory("vault-path-test").toFile()
        try {
            val vault = File(parent, "vault").apply { mkdirs() }
            val outside = File(parent, "outside").apply { mkdirs() }
            File(outside, "secret.txt").writeText("not part of the vault")
            val link = File(vault, "linked")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (failure: UnsupportedOperationException) {
                Assume.assumeNoException("Host does not support symbolic links", failure)
            } catch (failure: SecurityException) {
                Assume.assumeNoException("Host does not permit symbolic links", failure)
            } catch (failure: IOException) {
                Assume.assumeNoException("Host could not create a symbolic link", failure)
            }
            assertNull(resolveVaultRelativePath(vault, "linked/secret.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }
}

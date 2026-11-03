package com.vaultos.app.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOperationsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun zipRoundTripPreservesFolderStructureAndBytes() = runBlocking {
        val source = temporary.newFolder("source")
        File(source, "nested").mkdirs()
        File(source, "hello.txt").writeText("VaultOS archive test")
        File(source, "nested/image.bin").writeBytes(ByteArray(32_000) { (it % 251).toByte() })
        val zip = File(temporary.root, "source.zip")
        FileOperations.createZip(listOf(source), zip)
        val destination = File(temporary.root, "restored")
        FileOperations.extractZip(zip, destination)
        val restored = File(destination, "source")
        assertEquals("VaultOS archive test", File(restored, "hello.txt").readText())
        assertEquals(File(source, "nested/image.bin").readBytes().toList(), File(restored, "nested/image.bin").readBytes().toList())
    }


    @Test
    fun copyCancellationKeepsOriginalAndRemovesPartialDestination() = runBlocking {
        val source = temporary.newFile("cancel-source.bin")
        source.writeBytes(ByteArray(256_000) { (it % 239).toByte() })
        val destination = File(temporary.root, "cancel-destination.bin")
        val failure = runCatching {
            FileOperations.copy(source, destination) { throw kotlinx.coroutines.CancellationException("test cancel") }
        }.exceptionOrNull()
        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertTrue(source.isFile)
        assertFalse(destination.exists())
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.endsWith(".partial") })
    }

    @Test
    fun sameVolumeMoveRenamesSourceAndReportsCompletion() = runBlocking {
        val source = temporary.newFile("move-source.txt")
        source.writeText("move without losing bytes")
        val destination = File(temporary.root, "move-destination.txt")
        var progress: FileOperationProgress? = null
        FileOperations.move(source, destination) { progress = it }
        assertFalse(source.exists())
        assertEquals("move without losing bytes", destination.readText())
        assertEquals(destination.length(), progress?.completedBytes)
        assertEquals(1, progress?.completedFiles)
    }

    @Test
    fun extractionRejectsZipSlipAndCleansTemporaryOutput() = runBlocking {
        val archive = File(temporary.root, "unsafe.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("not outside".toByteArray())
            zip.closeEntry()
        }
        val destination = File(temporary.root, "extracted")
        val failure = runCatching { FileOperations.extractZip(archive, destination) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(temporary.root, "escape.txt").exists())
        assertFalse(destination.exists())
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.endsWith(".partial") })
    }

    @Test
    fun copyUsesAtomicDestinationAndReportsProgress() = runBlocking {
        val source = temporary.newFile("large.dat")
        source.writeBytes(ByteArray(200_000) { (it % 127).toByte() })
        val destination = File(temporary.root, "copy.dat")
        var lastProgress = 0L
        FileOperations.copy(source, destination) { lastProgress = it.completedBytes }
        assertEquals(source.readBytes().toList(), destination.readBytes().toList())
        assertEquals(source.length(), lastProgress)
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.contains(".partial") })
    }
}

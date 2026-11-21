package com.vaultos.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultos.app.data.VaultDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Exercises clipboard-event persistence and integrity against the real Room database
 * and app-private archive. Unique payload markers keep cleanup isolated from user data.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardHistoryRepositoryInstrumentedTest {
    private lateinit var repository: ClipboardHistoryRepository
    private lateinit var vault: VaultRepository
    private lateinit var dao: com.vaultos.app.data.VaultDao
    private val eventIds = linkedSetOf<String>()
    private val itemIds = linkedSetOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ClipboardHistoryRepository(context)
        vault = VaultRepository(context)
        dao = VaultDatabase.get(context).dao()
    }

    @After
    fun tearDown() = runBlocking {
        eventIds.forEach { dao.deleteClipboardHistory(it) }
        itemIds.forEach { id ->
            val item = dao.getById(id) ?: return@forEach
            item.archivedPath?.let { path ->
                val file = File(path).canonicalFile
                if (file.toPath().startsWith(vault.root.canonicalFile.toPath())) file.delete()
            }
            dao.deleteChunks(id)
            dao.delete(id)
        }
    }

    @Test
    fun longMultilingualCapturePersistsAndReadsBackExactly() = runBlocking {
        val marker = "VAULTOS-CLIPBOARD-ROUNDTRIP-${UUID.randomUUID()}"
        val content = buildString {
            append(marker)
            repeat(180) { append("\nZażółć gęślą jaźń — İstanbul, ığdır, café, Ελληνικά, 👩‍💻") }
            append("\n")
            append(marker)
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        val entry = capture(content)

        assertTrue(content.length > 2_000)
        assertTrue(bytes.size > content.length)
        assertEquals(bytes.size.toLong(), entry.byteCount)
        assertEquals(content.codePointCount(0, content.length), entry.characterCount)
        assertEquals(content.take(420), entry.preview)
        assertTrue(repository.observe().first().any { it.id == entry.id })
        assertEquals(content, repository.readText(entry))
    }

    @Test
    fun duplicateCapturesRemainSeparateEventsAndShareOneArchiveItem() = runBlocking {
        val content = "VAULTOS-CLIPBOARD-DUPLICATE-${UUID.randomUUID()} — żółć — İstanbul"
        val first = capture(content)
        val second = capture(content)

        assertNotEquals(first.id, second.id)
        assertEquals(first.itemId, second.itemId)
        val persisted = repository.observe().first().filter { it.id == first.id || it.id == second.id }
        assertEquals(setOf(first.id, second.id), persisted.map { it.id }.toSet())
        assertEquals(content, repository.readText(first))
        assertEquals(content, repository.readText(second))
    }

    @Test
    fun eventReadbackRejectsHashByteAndCharacterMetadataMismatch() = runBlocking {
        val content = "VAULTOS-CLIPBOARD-EVENT-INTEGRITY-${UUID.randomUUID()}-żółć-👩‍💻"
        val entry = capture(content)

        assertNull(repository.readText(entry.copy(contentHash = "0".repeat(64))))
        assertNull(repository.readText(entry.copy(byteCount = entry.byteCount + 1)))
        assertNull(repository.readText(entry.copy(characterCount = entry.characterCount + 1)))
        assertEquals(content, repository.readText(entry))
    }

    @Test
    fun sameLengthArchiveTamperingIsRejectedAndSearchFindsBeyondPreview() = runBlocking {
        val marker = "VAULTOS-CLIPBOARD-SEARCH-${UUID.randomUUID()}"
        val content = "early-content-".repeat(300) + marker + "later-content-".repeat(300)
        val entry = capture(content)

        assertFalse(entry.preview.contains(marker))
        assertTrue(repository.search(marker).first().any { it.id == entry.id })
        assertEquals(content, repository.readText(entry))

        val item = dao.getById(requireNotNull(entry.itemId)) ?: error("Captured archive item missing")
        val file = File(requireNotNull(item.archivedPath))
        val replacement = "X" + content.drop(1)
        assertEquals(content.toByteArray(Charsets.UTF_8).size, replacement.toByteArray(Charsets.UTF_8).size)
        file.writeText(replacement, Charsets.UTF_8)

        assertNull(repository.readText(entry))
    }

    private suspend fun capture(content: String) =
        requireNotNull(
            repository.captureText(
                text = content,
                foregroundPackage = "com.vaultos.instrumentedtest",
                clipLabel = "VaultOS instrumented capture",
                mimeTypes = listOf("text/plain", "text/html")
            )
        ).also { entry ->
            eventIds += entry.id
            entry.itemId?.let(itemIds::add)
        }
}

package com.vaultos.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultos.app.data.VaultDatabase
import com.vaultos.app.data.VaultDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Exercises the real Android Room database and private-file archive.
 * Each test uses unique content and removes only the records/files it created.
 */
@RunWith(AndroidJUnit4::class)
class VaultRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var repository: VaultRepository
    private lateinit var dao: VaultDao
    private val createdIds = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = VaultRepository(context)
        dao = VaultDatabase.get(context).dao()
    }

    @After
    fun tearDown() = runBlocking {
        createdIds.forEach { id ->
            val item = dao.getById(id)
            item?.archivedPath?.let { path ->
                val file = File(path).canonicalFile
                if (file.toPath().startsWith(repository.root.canonicalFile.toPath())) {
                    file.delete()
                }
            }
            dao.deleteChunks(id)
            dao.delete(id)
        }
    }

    @Test
    fun longMultilingualTextIsStoredAndReadBackCompletely() = runBlocking {
        val marker = "VAULTOS-ROUNDTRIP-${UUID.randomUUID()}"
        val content = buildString {
            append(marker)
            repeat(900) {
                append("\nZażółć gęślą jaźń — İstanbul, ığdır, café, Ελληνικά, 👩‍💻")
            }
            append("\n")
            append(marker)
        }
        val bytes = content.toByteArray(Charsets.UTF_8)

        val saved = repository.saveText(content, source = "instrumented-test")
        createdIds += saved.id

        assertTrue("Test payload must exceed the normal preview size", content.length > 2_000)
        assertTrue("Multibyte payload should exercise UTF-8 byte accounting", bytes.size > content.length)
        assertEquals(bytes.size.toLong(), saved.sizeBytes)
        assertEquals(sha256(bytes), saved.sha256)
        assertTrue("The catalogue field must remain a preview, not the full payload", saved.textContent!!.length < content.length)
        assertEquals("The complete saved content must round-trip exactly", content, repository.readFullText(saved))
    }

    @Test
    fun globalTextSearchFindsContentBeyondThePreview() = runBlocking {
        val marker = "VAULTOS-SEARCH-${UUID.randomUUID()}"
        val content = buildString {
            repeat(1_200) { append("early-content-") }
            append("\n")
            append(marker)
            append("\n")
            repeat(1_200) { append("later-content-") }
        }

        val saved = repository.saveText(content, source = "instrumented-search-test")
        createdIds += saved.id

        assertTrue("Search marker should not be in the small preview", !saved.textContent.orEmpty().contains(marker))
        val matches = dao.search(marker).first()
        assertTrue("Global search must find full-text chunks outside the preview", matches.any { it.id == saved.id })
        assertEquals(content, repository.readFullText(saved))
    }

    @Test
    fun identicalPayloadReusesVerifiedArchiveObject() = runBlocking {
        val content = "VAULTOS-DUPLICATE-${UUID.randomUUID()}\n" + "same payload — żółć — İstanbul\n".repeat(2_500)

        val first = repository.saveText(content, source = "first-capture")
        createdIds += first.id
        val second = repository.saveText(content, source = "second-capture")

        assertEquals(first.id, second.id)
        assertEquals(first.archivedPath, second.archivedPath)
        assertEquals(content, repository.readFullText(second))
    }

    @Test
    fun modifiedPayloadWithSameByteLengthIsRejected() = runBlocking {
        val content = "VAULTOS-INTEGRITY-${UUID.randomUUID()}-original"
        val saved = repository.saveText(content, source = "instrumented-integrity-test")
        createdIds += saved.id

        val file = File(requireNotNull(saved.archivedPath))
        val replacement = "X" + content.drop(1)
        assertEquals(content.toByteArray(Charsets.UTF_8).size, replacement.toByteArray(Charsets.UTF_8).size)
        file.writeText(replacement, Charsets.UTF_8)

        assertNull("Same-length tampering must fail the SHA-256 check", repository.readFullText(saved))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

package com.vaultos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the clipboard history table and ordering/search actions against a real
 * Room database. In-memory storage keeps the test independent of user archive data.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardHistoryDaoInstrumentedTest {
    private lateinit var database: VaultDatabase
    private lateinit var dao: VaultDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
        dao = database.dao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun identicalPayloadsRemainSeparateTimestampedCaptureEvents() = runBlocking {
        val older = entry(id = "copy-1", capturedAt = 100L)
        val newer = entry(id = "copy-2", capturedAt = 200L)

        dao.insertClipboardHistory(older)
        dao.insertClipboardHistory(newer)

        val history = dao.observeClipboardHistory().first()
        assertEquals(listOf("copy-2", "copy-1"), history.map { it.id })
        assertEquals(older.contentHash, newer.contentHash)
        assertEquals(2, dao.clipboardHistoryCount().first())
    }

    @Test
    fun pinningChangesPriorityAndDeleteRemovesOnlyTheSelectedEvent() = runBlocking {
        dao.insertClipboardHistory(entry(id = "older", capturedAt = 100L))
        dao.insertClipboardHistory(entry(id = "newer", capturedAt = 200L))

        dao.setClipboardPinned("older", true)
        assertEquals("older", dao.observeClipboardHistory().first().first().id)

        dao.deleteClipboardHistory("newer")
        val remaining = dao.observeClipboardHistory().first()
        assertEquals(listOf("older"), remaining.map { it.id })
        assertTrue(remaining.single().isPinned)
    }

    @Test
    fun clearUnpinnedHistoryPreservesPinnedEvents() = runBlocking {
        dao.insertClipboardHistory(entry(id = "pinned", capturedAt = 100L).copy(isPinned = true))
        dao.insertClipboardHistory(entry(id = "ordinary-1", capturedAt = 200L))
        dao.insertClipboardHistory(entry(id = "ordinary-2", capturedAt = 300L))

        dao.clearUnpinnedClipboardHistory()

        val remaining = dao.observeClipboardHistory().first()
        assertEquals(listOf("pinned"), remaining.map { it.id })
        assertEquals(1, dao.clipboardHistoryCount().first())
    }

    @Test
    fun searchFindsPreviewSourceLabelAndMimeMetadata() = runBlocking {
        dao.insertClipboardHistory(
            entry(
                id = "searchable",
                capturedAt = 100L,
                preview = "meeting notes for tomorrow",
                foregroundPackage = "com.example.notes",
                clipLabel = "Project Atlas",
                mimeTypes = "text/plain|text/html"
            )
        )

        assertEquals(listOf("searchable"), dao.searchClipboardHistory("meeting").first().map { it.id })
        assertEquals(listOf("searchable"), dao.searchClipboardHistory("com.example.notes").first().map { it.id })
        assertEquals(listOf("searchable"), dao.searchClipboardHistory("Project Atlas").first().map { it.id })
        assertEquals(listOf("searchable"), dao.searchClipboardHistory("text/html").first().map { it.id })
        assertFalse(dao.searchClipboardHistory("not-present").first().isNotEmpty())
    }

    private fun entry(
        id: String,
        capturedAt: Long,
        preview: String = "same copied payload",
        foregroundPackage: String? = "com.example.editor",
        clipLabel: String? = "Copied text",
        mimeTypes: String = "text/plain"
    ) = ClipboardHistoryEntry(
        id = id,
        itemId = null,
        contentType = "text",
        preview = preview,
        foregroundPackage = foregroundPackage,
        clipLabel = clipLabel,
        mimeTypes = mimeTypes,
        contentHash = "a".repeat(64),
        characterCount = preview.codePointCount(0, preview.length),
        byteCount = preview.toByteArray(Charsets.UTF_8).size.toLong(),
        capturedAt = capturedAt
    )
}

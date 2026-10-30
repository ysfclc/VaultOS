package com.vaultos.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileClassifierTest {
    @Test fun classifiesCommonTypes() {
        assertEquals("Apps & APKs", FileClassifier.classify("release.apk", "application/octet-stream"))
        assertEquals("Images", FileClassifier.classify("photo.jpg", "image/jpeg"))
        assertEquals("Videos", FileClassifier.classify("clip.mp4", "video/mp4"))
        assertEquals("Audio", FileClassifier.classify("voice.m4a", "audio/mp4"))
        assertEquals("Documents", FileClassifier.classify("report.pdf", "application/pdf"))
        assertEquals("Archives", FileClassifier.classify("backup.zip", "application/zip"))
    }

    @Test fun filenameSignatureWinsOverGenericTextMime() {
        assertEquals("Code", FileClassifier.classify("workflow.yml", "text/plain"))
        assertEquals("Code", FileClassifier.classify("settings.json", "text/plain"))
        assertEquals("Text", FileClassifier.classify("notes.txt", "text/plain"))
    }

    @Test fun classifiesSharedTextAndUnknownFiles() {
        assertEquals("Text", FileClassifier.classify("clipboard", "application/octet-stream", isText = true))
        assertEquals("Other", FileClassifier.classify("blob.bin", "application/octet-stream"))
    }

    @Test fun sanitizesFileNames() {
        val safe = FileClassifier.safeFileName("../folder\\secret\u0000.txt")
        assertTrue(!safe.contains('/') && !safe.contains('\\') && !safe.contains('\u0000'))
    }
}

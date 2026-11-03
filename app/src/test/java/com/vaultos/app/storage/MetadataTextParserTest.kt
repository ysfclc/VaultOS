package com.vaultos.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTextParserTest {
    @Test
    fun readsPdfInfoDictionary() {
        val parsed = MetadataTextParser.readPdfInfo("/Title (Project Notes) /Author (Yusuf) /Subject (Field work)")
        assertEquals("Project Notes", parsed.title)
        assertEquals("Yusuf", parsed.author)
        assertEquals("Field work", parsed.subject)
    }

    @Test
    fun readsUtf16HexPdfStrings() {
        val parsed = MetadataTextParser.readPdfInfo("/Title <FEFF005600610075006C0074>")
        assertEquals("Vault", parsed.title)
    }

    @Test
    fun readsDocumentCorePropertiesAndDecodesEntities() {
        val xml = "<cp:coreProperties><dc:title>Plan &amp; Review</dc:title><dc:creator>Yusuf</dc:creator><dc:subject>Operations</dc:subject></cp:coreProperties>"
        assertEquals("Plan & Review", MetadataTextParser.readXmlValue(xml, "title"))
        assertEquals("Yusuf", MetadataTextParser.readXmlValue(xml, "creator"))
        assertEquals("Operations", MetadataTextParser.readXmlValue(xml, "subject"))
    }

    @Test
    fun missingDocumentPropertyReturnsNull() {
        assertNull(MetadataTextParser.readXmlValue("<coreProperties></coreProperties>", "title"))
    }
}

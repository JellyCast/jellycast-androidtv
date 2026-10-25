package nl.jellycast.tv.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WikidataAnswerTest {
    @Test
    fun `every language code in the answer is read once`() {
        val body =
            """
            {"head":{"vars":["code"]},"results":{"bindings":[
              {"code":{"type":"literal","value":"en"}},
              {"code":{"type":"literal","value":"ru"}},
              {"code":{"type":"literal","value":"en"}}
            ]}}
            """.trimIndent()
        assertEquals(listOf("en", "ru"), parseLanguageCodes(body))
    }

    @Test
    fun `an unknown title gives no language`() {
        val body = """{"head":{"vars":["code"]},"results":{"bindings":[]}}"""
        assertEquals(emptyList<String>(), parseLanguageCodes(body))
    }
}

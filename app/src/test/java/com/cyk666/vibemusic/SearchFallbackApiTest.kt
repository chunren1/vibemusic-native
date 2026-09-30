package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchFallbackApiTest {

    private fun searchEnvelope(suggested: String?): String {
        val field = if (suggested == null) "" else ""","suggestedKeyword":"$suggested""""
        return """
        {
          "code": 200,
          "message": "ok",
          "data": {
            "total": 0,
            "list": []$field
          }
        }
        """.trimIndent()
    }

    @Test
    fun parseSearch_suggestedKeywordPresent() {
        val r = VibeApi.parseSearch(searchEnvelope("周杰伦"))
        assertTrue(r.list.isEmpty())
        assertEquals("周杰伦", r.suggestedKeyword)
    }

    @Test
    fun parseSearch_suggestedKeywordAbsentIsNull() {
        val r = VibeApi.parseSearch(searchEnvelope(null))
        assertTrue(r.list.isEmpty())
        assertNull(r.suggestedKeyword)
    }

    @Test
    fun parseHotwords_listOk() {
        val r = VibeApi.parseHotwords(
            """{"code":200,"message":"ok","data":["周杰伦","晴天","陈奕迅"]}"""
        )
        assertEquals(listOf("周杰伦", "晴天", "陈奕迅"), r)
    }

    @Test
    fun parseHotwords_missingDataFallsBackEmpty() {
        val r = VibeApi.parseHotwords("""{"code":200,"message":"ok"}""")
        assertTrue(r.isEmpty())
    }
}

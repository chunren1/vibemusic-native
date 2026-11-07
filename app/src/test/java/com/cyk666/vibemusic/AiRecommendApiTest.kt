package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AI 推荐路径契约（后端 `GET /api/recommend/ai?refresh=`）。
 * 只钉字符串本身：一旦有人把 `refresh` 写死、或把路径拼错，
 * 这里先红，而不是等真机点「换一批」才发现配额没花/接口 404。
 */
class AiRecommendApiTest {

    @Test
    fun path_autoLoad_doesNotSpendQuota() {
        assertEquals("api/recommend/ai?refresh=false", buildAiRecommendPath(false))
    }

    @Test
    fun path_explicitRefresh_spendsQuota() {
        assertEquals("api/recommend/ai?refresh=true", buildAiRecommendPath(true))
    }
}

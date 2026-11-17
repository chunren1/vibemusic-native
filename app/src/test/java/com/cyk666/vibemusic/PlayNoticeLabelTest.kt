package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 换源提示文案。
 *
 * 背景：降级链（网易云全挂 → QQ）命中时后端过去一律回 quality="unknown"，
 * App 端只认 128/PQ/AAC，于是提示里音质那一段是空的——用户看不到自己听到的
 * 到底是 320k 还是标准音质。后端补真实码率后，这里把标签口径钉住。
 */
class PlayNoticeLabelTest {

    @Test
    fun qualityLabel_coversEveryTier() {
        assertEquals(" · 无损", qualityLabel("FLAC"))
        assertEquals(" · 320k", qualityLabel("320"))
        assertEquals(" · 192k", qualityLabel("192"))
        assertEquals(" · 标准音质", qualityLabel("128"))
    }

    @Test
    fun qualityLabel_isCaseInsensitiveAndDropsUnknown() {
        assertEquals(" · 无损", qualityLabel("flac"))
        assertEquals(" · 320k", qualityLabel("320"))
        assertEquals("", qualityLabel("unknown"))
        assertEquals("", qualityLabel(null))
        assertEquals("", qualityLabel(""))
    }

    @Test
    fun platformLabel_mapsAllFiveSources() {
        assertEquals("QQ 音乐", platformLabel("qq"))
        assertEquals("网易云音乐", platformLabel("netease"))
        assertEquals("咪咕音乐", platformLabel("migu"))
        assertEquals("酷狗音乐", platformLabel("kugou"))
        assertEquals("哔哩哔哩", platformLabel("bilibili"))
        assertEquals("其他平台", platformLabel(null))
    }
}

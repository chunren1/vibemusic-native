package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun shouldAnnounce_skipsSameTrackSwitch() {
        // 用户原话：「这个服务器不是有吗，为什么不从服务器取」——
        // 同一首歌换平台取音频（甚至直接来自本地缓存）不该弹提示，否则语义与事实不符。
        val same = PlaySubstituteNotice(
            platform = "qq", title = "唯一", artist = "告五人",
            coverUrl = null, quality = "320", reason = "原曲在其他平台不可播", sameTrack = true
        )
        assertFalse(shouldAnnounce(same))

        val different = same.copy(sameTrack = false)
        assertTrue("真正换版本时必须提示", shouldAnnounce(different))
    }

    @Test
    fun substituteMessage_differentTrack_keepsVersionWarning() {
        val msg = substituteMessage(
            PlaySubstituteNotice(
                platform = "kugou",
                title = "失眠",
                artist = "Suki刘舒妤",
                coverUrl = null,
                quality = "128",
                reason = "原曲在其他平台不可播",
                sameTrack = false
            )
        )
        assertEquals("已切换为酷狗音乐：失眠 - Suki刘舒妤 · 标准音质（可能为不同版本）", msg)
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

package com.cyk666.vibemusic

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFocusConfigTest {

    @Test
    fun config_usesMediaUsageAndMusicContent() {
        val cfg = audioFocusConfig()
        assertEquals(C.USAGE_MEDIA, cfg.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, cfg.contentType)
    }

    @Test
    fun config_handlesFocusAndNoisy() {
        val cfg = audioFocusConfig()
        assertTrue(cfg.handleAudioFocus)
        assertTrue(cfg.handleAudioBecomingNoisy)
    }

    @Test
    fun config_buildsValidAudioAttributes() {
        val cfg = audioFocusConfig()
        val attrs = AudioAttributes.Builder()
            .setUsage(cfg.usage)
            .setContentType(cfg.contentType)
            .build()
        assertEquals(C.USAGE_MEDIA, attrs.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, attrs.contentType)
    }

    // 焦点丢失/恢复策略已交还 Media3 原生处理（瞬时丢失=压制静音+恢复自动续播；
    // 永久丢失=暂停且无回调），原先自绘的 FocusLossAction 策略与"抑制自动恢复"分支
    // 均为死代码，2026-09-18 依据 Media3 1.5.1 源码删除（详见 PlaybackService 注释）。
}

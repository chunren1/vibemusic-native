package com.cyk666.vibemusic

import android.media.AudioManager
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

    @Test
    fun focusLoss_permanentLoss_pauses() {
        assertEquals(FocusLossAction.PAUSE, focusLossAction(AudioManager.AUDIOFOCUS_LOSS))
    }

    @Test
    fun focusLoss_transient_pauses() {
        assertEquals(FocusLossAction.PAUSE, focusLossAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    @Test
    fun focusLoss_canDuck_ducks() {
        assertEquals(
            FocusLossAction.DUCK,
            focusLossAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        )
    }

    @Test
    fun focusLoss_gain_neverResumes() {
        assertEquals(FocusLossAction.PAUSE, focusLossAction(AudioManager.AUDIOFOCUS_GAIN))
    }
}

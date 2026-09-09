package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MineAvatarBgTest {

    @Test
    fun avatarInitial_latin() {
        assertEquals("B", avatarInitial("bob"))
        assertEquals("B", avatarInitial("Bob"))
    }

    @Test
    fun avatarInitial_cjk() {
        assertEquals("张", avatarInitial("张三"))
        assertEquals("あ", avatarInitial("あいう"))
    }

    @Test
    fun avatarInitial_blank() {
        assertEquals("♪", avatarInitial(""))
        assertEquals("♪", avatarInitial("   "))
    }

    @Test
    fun avatarInitial_digit() {
        assertEquals("9", avatarInitial("9lives"))
    }

    @Test
    fun avatarInitial_symbolOnly() {
        assertEquals("♪", avatarInitial("!!!"))
        assertEquals("♪", avatarInitial("@@@###"))
    }

    @Test
    fun avatarInitial_skipsLeadingSymbols() {
        assertEquals("B", avatarInitial("@bob"))
        assertEquals("1", avatarInitial("#1fan"))
    }

    @Test
    fun absImgUrl_relativeBecomesAbsolute() {
        assertEquals(
            "https://vibe.cyk666.top/uploads/avatars/a.jpg",
            absImgUrl("/uploads/avatars/a.jpg")
        )
    }

    @Test
    fun absImgUrl_relativeWithoutSlash() {
        assertEquals(
            "https://vibe.cyk666.top/uploads/avatars/a.jpg",
            absImgUrl("uploads/avatars/a.jpg")
        )
    }

    @Test
    fun absImgUrl_absolutePassthrough() {
        assertEquals("https://c/x.jpg", absImgUrl("https://c/x.jpg"))
        assertEquals("http://c/x.jpg", absImgUrl("http://c/x.jpg"))
    }

    @Test
    fun absImgUrl_blankStaysBlank() {
        assertEquals("", absImgUrl(""))
        assertEquals("", absImgUrl("  "))
    }

    @Test
    fun shouldShowMineBg_nonBlank() {
        assertTrue(shouldShowMineBg("/uploads/avatars/bg_1.png"))
        assertTrue(shouldShowMineBg("https://c/bg.jpg"))
    }

    @Test
    fun shouldShowMineBg_blankHidden() {
        assertFalse(shouldShowMineBg(""))
        assertFalse(shouldShowMineBg("   "))
    }
}

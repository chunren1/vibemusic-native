package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOverlayTest {

    @Test
    fun selectDismissesOverlay() {
        assertFalse(
            reduceSuggestOverlayVisible(true, SuggestOverlayEvent.SELECT)
        )
    }

    @Test
    fun selectWhenAlreadyHiddenStaysHidden() {
        assertFalse(
            reduceSuggestOverlayVisible(false, SuggestOverlayEvent.SELECT)
        )
    }

    @Test
    fun searchPressReshowsOverlay() {
        assertTrue(
            reduceSuggestOverlayVisible(false, SuggestOverlayEvent.SEARCH_PRESS)
        )
    }

    @Test
    fun searchPressWhenVisibleStaysVisible() {
        assertTrue(
            reduceSuggestOverlayVisible(true, SuggestOverlayEvent.SEARCH_PRESS)
        )
    }

    @Test
    fun queryChangeKeepsHiddenWhileResultsVisible() {
        assertFalse(
            reduceSuggestOverlayVisible(false, SuggestOverlayEvent.QUERY_CHANGE)
        )
    }

    @Test
    fun queryChangeKeepsVisibleBeforeFirstSearch() {
        assertTrue(
            reduceSuggestOverlayVisible(true, SuggestOverlayEvent.QUERY_CHANGE)
        )
    }

    @Test
    fun tapSelectThenKeystrokeStaysHiddenUntilSearchPress() {
        var visible = true
        visible = reduceSuggestOverlayVisible(visible, SuggestOverlayEvent.SELECT)
        assertFalse(visible)
        visible = reduceSuggestOverlayVisible(visible, SuggestOverlayEvent.QUERY_CHANGE)
        assertFalse(visible)
        visible = reduceSuggestOverlayVisible(visible, SuggestOverlayEvent.QUERY_CHANGE)
        assertFalse(visible)
        visible = reduceSuggestOverlayVisible(visible, SuggestOverlayEvent.SEARCH_PRESS)
        assertTrue(visible)
    }

    @Test
    fun gateNeedsFlagAndSuggestions() {
        assertTrue(shouldShowSuggestOverlay(true, true))
        assertFalse(shouldShowSuggestOverlay(false, true))
        assertFalse(shouldShowSuggestOverlay(true, false))
        assertFalse(shouldShowSuggestOverlay(false, false))
    }

    @Test
    fun reducerCoversAllEvents() {
        assertEquals(
            SuggestOverlayEvent.entries.size,
            3
        )
    }
}

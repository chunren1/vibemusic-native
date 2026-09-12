package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchBodyTest {

    @Test
    fun loading_winsOverEverything() {
        assertEquals(
            SearchBody.LOADING,
            selectSearchBody(
                loading = true,
                queryBlank = false,
                searched = true,
                error = "x",
                hasResults = true,
                hasVisible = true
            )
        )
    }

    @Test
    fun blankQuery_showsHistory() {
        assertEquals(
            SearchBody.HISTORY,
            selectSearchBody(
                loading = false,
                queryBlank = true,
                searched = false,
                error = null,
                hasResults = false,
                hasVisible = false
            )
        )
    }

    @Test
    fun typedButNeverAttempted_isIdle() {
        assertEquals(
            SearchBody.IDLE,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = false,
                error = null,
                hasResults = false,
                hasVisible = false
            )
        )
    }

    @Test
    fun firstSearchFailureWithNoResults_retries() {
        assertEquals(
            SearchBody.ERROR_RETRY,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = "无网络连接",
                hasResults = false,
                hasVisible = false
            )
        )
    }

    @Test
    fun failureWithStaleRows_showsErrorAboveResults() {
        assertEquals(
            SearchBody.STALE_WITH_ERROR,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = "无网络连接",
                hasResults = true,
                hasVisible = true
            )
        )
    }

    @Test
    fun failureWithStaleRowsHiddenByFilter_stillStale() {
        assertEquals(
            SearchBody.STALE_WITH_ERROR,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = "无网络连接",
                hasResults = true,
                hasVisible = false
            )
        )
    }

    @Test
    fun successWithRows_showsResults() {
        assertEquals(
            SearchBody.RESULTS,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = null,
                hasResults = true,
                hasVisible = true
            )
        )
    }

    @Test
    fun successWithNothing_showsNoResult() {
        assertEquals(
            SearchBody.NO_RESULT,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = null,
                hasResults = false,
                hasVisible = false
            )
        )
    }

    @Test
    fun successFilteredToEmpty_showsFilterEmpty() {
        assertEquals(
            SearchBody.FILTER_EMPTY,
            selectSearchBody(
                loading = false,
                queryBlank = false,
                searched = true,
                error = null,
                hasResults = true,
                hasVisible = false
            )
        )
    }
}

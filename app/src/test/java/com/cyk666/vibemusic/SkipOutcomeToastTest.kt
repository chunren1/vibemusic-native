package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Skip-failure toast truthfulness: the toast must report the service's actual
 * outcome (retried / jumped / stopped) instead of a blanket "已跳过".
 */
class SkipOutcomeToastTest {

    @Test
    fun skipped_keepsHistoricSkippedWording() {
        assertEquals(
            "《夜曲》播不了，已跳过 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast("夜曲", "ERROR_CODE_IO_BAD_HTTP_STATUS", SkipOutcome.SKIPPED_TO_NEXT)
        )
    }

    @Test
    fun stopped_saysQueueEnd() {
        assertEquals(
            "《夜曲》播不了，队列已到末尾，播放停止 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast("夜曲", "ERROR_CODE_IO_BAD_HTTP_STATUS", SkipOutcome.STOPPED_AT_END)
        )
    }

    @Test
    fun retried_saysRetrying() {
        assertEquals(
            "《夜曲》播不了，正在重试 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast("夜曲", "ERROR_CODE_IO_BAD_HTTP_STATUS", SkipOutcome.RETRIED_SAME_ITEM)
        )
    }

    @Test
    fun nullTitle_fallsBackToUnknown() {
        assertEquals(
            "《unknown》播不了，已跳过 (TIMEOUT)",
            skipErrorToast(null, "TIMEOUT", SkipOutcome.SKIPPED_TO_NEXT)
        )
    }

    @Test
    fun infer_reportedOutcomeWins() {
        assertEquals(
            SkipOutcome.RETRIED_SAME_ITEM,
            inferSkipOutcome(SkipOutcome.RETRIED_SAME_ITEM, false)
        )
        assertEquals(
            SkipOutcome.STOPPED_AT_END,
            inferSkipOutcome(SkipOutcome.STOPPED_AT_END, true)
        )
    }

    @Test
    fun infer_nullFallsBackToHasNext() {
        assertEquals(SkipOutcome.SKIPPED_TO_NEXT, inferSkipOutcome(null, true))
        assertEquals(SkipOutcome.STOPPED_AT_END, inferSkipOutcome(null, false))
    }
}

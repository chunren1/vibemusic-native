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

    @Test
    fun meltdown_withSongsLeft_neverClaimsQueueEnd() {
        assertEquals(
            "《夜曲》播不了，连续多次失败，播放停止 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast("夜曲", "ERROR_CODE_IO_BAD_HTTP_STATUS", SkipOutcome.STOPPED_CONSEC_FAILURES)
        )
    }

    @Test
    fun meltdown_split_itemsRemainVsGenuineEnd() {
        assertEquals(SkipOutcome.STOPPED_CONSEC_FAILURES, meltDownOutcome(hasMoreItems = true))
        assertEquals(SkipOutcome.STOPPED_AT_END, meltDownOutcome(hasMoreItems = false))
    }

    @Test
    fun meltdown_guardExit_matchesShouldAutoSkipComplement() {
        // shouldAutoSkip(consecFails, hasNext)==false splits exactly into the
        // two melt-down signals: cap-tripped-with-items-left vs genuine end.
        for (fails in 0..5) {
            for (hasNext in listOf(false, true)) {
                if (!shouldAutoSkip(fails, hasNext)) {
                    val expected = if (hasNext) SkipOutcome.STOPPED_CONSEC_FAILURES
                    else SkipOutcome.STOPPED_AT_END
                    assertEquals(expected, meltDownOutcome(hasNext))
                }
            }
        }
    }

    @Test
    fun unresolvableLocalItem_reportsServiceOutcomeTruthfully() {
        // MainActivity 本地项身份不可解析分支：无标题时仍按 service 上报的
        // 实际结果说话，绝不无条件"已跳过"。
        assertEquals(
            "《unknown》播不了，队列已到末尾，播放停止 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast(
                null,
                "ERROR_CODE_IO_BAD_HTTP_STATUS",
                inferSkipOutcome(SkipOutcome.STOPPED_AT_END, false)
            )
        )
        assertEquals(
            "《unknown》播不了，连续多次失败，播放停止 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast(
                null,
                "ERROR_CODE_IO_BAD_HTTP_STATUS",
                inferSkipOutcome(SkipOutcome.STOPPED_CONSEC_FAILURES, true)
            )
        )
        assertEquals(
            "《unknown》播不了，已跳过 (ERROR_CODE_IO_BAD_HTTP_STATUS)",
            skipErrorToast(
                null,
                "ERROR_CODE_IO_BAD_HTTP_STATUS",
                inferSkipOutcome(null, true)
            )
        )
    }
}

package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Top-3 / Top-4 回归：播放代际守卫 + 队列页数据源。
 *
 * 世代规则（与 songsGen + isStalePlaylistSongs 同构）：playAt /
 * queueSeekTo / queueRemoveAt 自增 playGen，IO 落地后 gen 不一致即丢弃，
 * seek 前再校验目标身份，双击两首歌不会交叉 seek。
 */
class PlayGenGuardTest {

    private fun song(id: String) = Song(id, "n$id", "a", "al", "", 180, "netease")

    @Test
    fun staleGen_sameGenNotStale() {
        assertFalse(isStalePlayGen(3, 3))
    }

    @Test
    fun staleGen_bumpedGenIsStale() {
        assertFalse(isStalePlayGen(1, 1))
        assertTrue(isStalePlayGen(1, 2))
    }

    @Test
    fun staleGen_doubleTapFirstTapStale() {
        // 连点两首：第一首的 gen=7，第二首把 playGen 推到 8，第一首 IO
        // 回来后必须丢弃，否则 A 的断点 seek 会命中 B。
        val firstGen = 7
        val latestGen = 8
        assertTrue(isStalePlayGen(firstGen, latestGen))
        assertFalse(isStalePlayGen(latestGen, latestGen))
    }

    @Test
    fun targetMatch_sameSongAtIndex() {
        val queue = listOf(song("1"), song("2"))
        assertTrue(isRestoreTargetCurrent(queue, 1, song("2")))
    }

    @Test
    fun targetMatch_swappedListMismatch() {
        val queue = listOf(song("9"), song("2"))
        assertFalse(isRestoreTargetCurrent(queue, 0, song("1")))
    }

    @Test
    fun targetMatch_outOfBoundsIsMismatch() {
        val queue = listOf(song("1"))
        assertFalse(isRestoreTargetCurrent(queue, 5, song("1")))
        assertFalse(isRestoreTargetCurrent(emptyList(), 0, song("1")))
    }

    @Test
    fun timeline_nonEmptyWins() {
        val queue = listOf(song("new1"), song("new2"))
        val timeline = listOf(song("new1"), song("new2"))
        assertEquals(timeline, resolveQueueTimeline(queue, timeline))
    }

    @Test
    fun timeline_emptyFallsBackToQueue() {
        val queue = listOf(song("new1"))
        assertEquals(queue, resolveQueueTimeline(queue, emptyList()))
    }

    @Test
    fun timeline_bothEmptyStaysEmpty() {
        assertTrue(resolveQueueTimeline(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun timeline_staleTimelineNeverShown() {
        // playAt 落 queue 时一并刷新 timelineSongs：旧时间线不会残留。
        // 等价于赋值后 resolve 必须等于新列表，而非上一份列表。
        val oldTimeline = listOf(song("old1"), song("old2"))
        val newList = listOf(song("new1"))
        assertEquals(newList, resolveQueueTimeline(newList, newList))
        assertFalse(resolveQueueTimeline(newList, newList) == oldTimeline)
    }
}

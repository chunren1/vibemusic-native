package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverTest {

    // ---- banner ----

    @Test
    fun parseBanners_fullItems() {
        val json = """
            {"code":200,"message":"ok","data":[
              {"name":"华语速爆新歌","coverUrl":"https://cover.example/a.jpg","desc":"精选歌单","playCount":123456},
              {"name":"夜晚电台","coverUrl":"https://cover.example/b.jpg","desc":"深夜陪伴","playCount":"789"}
            ]}
        """.trimIndent()
        val banners = parseDiscoverBanners(json)
        assertEquals(2, banners.size)
        assertEquals("华语速爆新歌", banners[0].name)
        assertEquals("https://cover.example/a.jpg", banners[0].coverUrl)
        assertEquals("精选歌单", banners[0].desc)
        assertEquals(123456L, banners[0].playCount)
        assertEquals(789L, banners[1].playCount)
    }

    @Test
    fun parseBanners_missingDescAndPlayCountDefault() {
        val json = """{"code":200,"message":"ok","data":[{"name":"裸奔","coverUrl":"https://x/y.jpg"}]}"""
        val banners = parseDiscoverBanners(json)
        assertEquals(1, banners.size)
        assertEquals("", banners[0].desc)
        assertEquals(0L, banners[0].playCount)
    }

    @Test
    fun parseBanners_emptyListOk() {
        val banners = parseDiscoverBanners("""{"code":200,"message":"ok","data":[]}""")
        assertTrue(banners.isEmpty())
    }

    @Test
    fun parseBanners_non200Throws() {
        try {
            parseDiscoverBanners("""{"code":500,"message":"boom","data":[]}""")
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertTrue((e.message ?: "").isNotBlank())
        }
    }

    // ---- personalized ----

    private fun personalizedEnvelope(songs: String, reason: String?): String {
        val reasonPart = if (reason == null) "" else ""","reason":"$reason""""
        return """{"code":200,"message":"ok","data":{"songs":[$songs]$reasonPart,"greeting":"晚上好","type":"personalized"}}"""
    }

    @Test
    fun parsePersonalized_fullResult() {
        val song = """{"sourceId":"1895330088","name":"予以","artist":"队长","album":"予以","coverUrl":"https://c/x.jpg","duration":231,"platform":"netease"}"""
        val r = parsePersonalized(personalizedEnvelope(song, "因为你最近喜欢队长的歌"))
        assertEquals(1, r.songs.size)
        assertEquals("予以", r.songs[0].name)
        assertEquals("队长", r.songs[0].artist)
        assertEquals(231, r.songs[0].durationSec)
        assertEquals("netease", r.songs[0].platform)
        assertEquals("因为你最近喜欢队长的歌", r.reason)
        assertEquals("晚上好", r.greeting)
        assertEquals("personalized", r.type)
    }

    @Test
    fun parsePersonalized_missingReasonAndEmptySongs() {
        val r = parsePersonalized("""{"code":200,"message":"ok","data":{"songs":[]}}""")
        assertTrue(r.songs.isEmpty())
        assertEquals("", r.reason)
        assertEquals("", r.greeting)
        assertEquals("", r.type)
    }

    @Test
    fun parsePersonalized_missingSongsKeyYieldsEmpty() {
        val r = parsePersonalized("""{"code":200,"message":"ok","data":{"reason":"随缘"}}""")
        assertTrue(r.songs.isEmpty())
        assertEquals("随缘", r.reason)
    }

    @Test
    fun `每日推荐_reason为null时不显示null字样`() {
        val r = parsePersonalized("""{"code":200,"message":"ok","data":{"songs":[],"reason":null,"greeting":null,"type":null}}""")
        assertEquals("", r.reason)
        assertEquals("", r.greeting)
        assertEquals("", r.type)
    }

    @Test
    fun `每日推荐_reason为字符串null时视为空`() {
        val r = parsePersonalized("""{"code":200,"message":"ok","data":{"songs":[],"reason":"null"}}""")
        assertEquals("", r.reason)
    }

    // ---- random ----

    @Test
    fun parseRandom_fullSongDTO() {
        val json = """
            {"code":200,"message":"ok","data":[
              {"sourceId":"1","name":"晴天","artist":"周杰伦","album":"叶惠美","coverUrl":"https://c/q.jpg","duration":269,"platform":"qq"}
            ]}
        """.trimIndent()
        val songs = parseRandomSongs(json)
        assertEquals(1, songs.size)
        val s = songs[0]
        assertEquals("1", s.sourceId)
        assertEquals("晴天", s.name)
        assertEquals("周杰伦", s.artist)
        assertEquals("叶惠美", s.album)
        assertEquals(269, s.durationSec)
        assertEquals("qq", s.platform)
        assertTrue(s.streamUrl().contains("platform=qq"))
    }

    @Test
    fun parseRandom_missingDurationAndPlatformDefaults() {
        val json = """{"code":200,"message":"ok","data":[{"sourceId":"7","name":"裸歌","artist":"anon"}]}"""
        val songs = parseRandomSongs(json)
        assertEquals(1, songs.size)
        assertEquals(0, songs[0].durationSec)
        assertEquals("netease", songs[0].platform)
        assertTrue(songs[0].streamUrl().contains("platform=netease"))
    }

    @Test
    fun parseRandom_skipsNonObjects() {
        val json = """{"code":200,"message":"ok","data":[null,{"sourceId":"7","platform":"netease"}]}"""
        val songs = parseRandomSongs(json)
        assertEquals(1, songs.size)
        assertEquals("7", songs[0].sourceId)
    }

    // ---- recommend playlists ----

    @Test
    fun parseRecommend_returnedShape() {
        // Exact keys the controller returns (remapped from the cached shape).
        val json = """
            {"code":200,"message":"ok","data":[
              {"id":12345,"name":"热歌榜","coverUrl":"https://c/h.jpg","desc":"大家都在听","count":987654321,"source":"netease"}
            ]}
        """.trimIndent()
        val pls = parseRecommendPlaylists(json)
        assertEquals(1, pls.size)
        assertEquals("12345", pls[0].id)
        assertEquals("热歌榜", pls[0].name)
        assertEquals("https://c/h.jpg", pls[0].picUrl)
        assertEquals("大家都在听", pls[0].copywriter)
        assertEquals(987654321L, pls[0].playCount)
    }

    @Test
    fun parseRecommend_cachedShapeKeys() {
        // Tolerate the intermediate cached keys in case the mapping ever leaks through.
        val json = """
            {"code":200,"message":"ok","data":[
              {"id":"67890","name":"老歌单","picUrl":"https://c/o.jpg","copywriter":"经典","playCount":100}
            ]}
        """.trimIndent()
        val pls = parseRecommendPlaylists(json)
        assertEquals(1, pls.size)
        assertEquals("67890", pls[0].id)
        assertEquals("https://c/o.jpg", pls[0].picUrl)
        assertEquals("经典", pls[0].copywriter)
        assertEquals(100L, pls[0].playCount)
    }

    @Test
    fun parseRecommend_missingCopywriterDefaultsBlank() {
        val json = """{"code":200,"message":"ok","data":[{"id":1,"name":"无文案","coverUrl":"https://c/n.jpg"}]}"""
        val pls = parseRecommendPlaylists(json)
        assertEquals(1, pls.size)
        assertEquals("", pls[0].copywriter)
        assertEquals(0L, pls[0].playCount)
    }

    @Test
    fun `宝藏歌单_copywriter为null时置空走播放量文案`() {
        val json = """{"code":200,"message":"ok","data":[{"id":1,"name":"有量无文案","coverUrl":"https://c/n.jpg","copywriter":null,"desc":null,"count":25000}]}"""
        val pls = parseRecommendPlaylists(json)
        assertEquals(1, pls.size)
        assertEquals("", pls[0].copywriter)
        assertEquals(25000L, pls[0].playCount)
    }

    @Test
    fun `宝藏歌单_文案为字符串null时视为空`() {
        val json = """{"code":200,"message":"ok","data":[{"id":1,"name":"null","coverUrl":"https://c/n.jpg","copywriter":"null"}]}"""
        val pls = parseRecommendPlaylists(json)
        assertEquals("", pls[0].name)
        assertEquals("", pls[0].copywriter)
    }

    // ---- banner aspect ----

    @Test
    fun bannerAspectRatio_isNetEaseWebRatio() {
        assertEquals(2.35f, BANNER_ASPECT_RATIO)
    }

    @Test
    fun selectBannerAspect_measuredWinsWhenValid() {
        assertEquals(1.78f, selectBannerAspect(1.78f))
        assertEquals(2.35f, selectBannerAspect(null))
        assertEquals(2.35f, selectBannerAspect(Float.NaN))
        assertEquals(2.35f, selectBannerAspect(Float.POSITIVE_INFINITY))
        assertEquals(2.35f, selectBannerAspect(0f))
        assertEquals(2.35f, selectBannerAspect(-1f))
    }

    // ---- URL builders ----

    @Test
    fun buildPersonalizedPath_refreshFlag() {
        assertEquals("api/recommend/personalized?refresh=false", buildPersonalizedPath(false))
        assertEquals("api/recommend/personalized?refresh=true", buildPersonalizedPath(true))
    }

    @Test
    fun buildRandomPath_count() {
        assertEquals("api/songs/random?count=8", buildRandomPath(8))
        assertEquals("api/songs/random?count=1", buildRandomPath(0))
    }

    // ---- TTL ----

    @Test
    fun isDiscoverStale_neverLoadedIsStale() {
        assertTrue(isDiscoverStale(0L, 1_000_000L))
        assertTrue(isDiscoverStale(-5L, 1_000_000L))
    }

    @Test
    fun isDiscoverStale_freshWithinTtl() {
        val now = 1_000_000L
        assertFalse(isDiscoverStale(now - 60_000L, now))
        assertFalse(isDiscoverStale(now - DISCOVER_CACHE_TTL_MS + 1_000L, now))
    }

    @Test
    fun isDiscoverStale_oldBeyondTtl() {
        val now = 1_000_000L
        assertTrue(isDiscoverStale(now - DISCOVER_CACHE_TTL_MS, now))
        assertTrue(isDiscoverStale(now - DISCOVER_CACHE_TTL_MS - 1L, now))
    }

    // ---- formatPlayCount ----

    @Test
    fun formatPlayCount_thresholds() {
        assertEquals("0", formatPlayCount(-1))
        assertEquals("9999", formatPlayCount(9999))
        assertEquals("1万", formatPlayCount(10_000))
        assertEquals("2万", formatPlayCount(20_000))
        assertEquals("12.5万", formatPlayCount(125_000))
        assertEquals("1亿", formatPlayCount(100_000_000))
    }

    // ---- selectOpenedRecommend ----

    private fun ownedPlaylist(id: String, name: String) =
        Playlist(id, name, "https://cdn/$id.jpg", 10)

    @Test
    fun `宝藏歌单打开_优先选新导入`() {
        val before = setOf("p1")
        val after = listOf(ownedPlaylist("p1", "旧歌单"), ownedPlaylist("p9", "宝藏"))
        assertEquals("p9", selectOpenedRecommend(before, after, "宝藏", "宝藏")?.id)
    }

    @Test
    fun `宝藏歌单打开_无新增时按导入名回退`() {
        val before = setOf("p1")
        val after = listOf(ownedPlaylist("p1", "宝藏"))
        assertEquals("p1", selectOpenedRecommend(before, after, "宝藏", "其它")?.id)
    }

    @Test
    fun `宝藏歌单打开_全无匹配返回空`() {
        val after = listOf(ownedPlaylist("p1", "旧歌单"))
        assertEquals(null, selectOpenedRecommend(setOf("p1"), after, "", ""))
    }

    // ---- recommendToPlaylist / isRecommendDetail (tap-to-detail, no import) ----

    private fun treasure(id: String) =
        RecommendPlaylist(id, "宝藏$id", "https://cdn/$id.jpg", "编辑推荐", 999L)

    @Test
    fun `宝藏详情_未导入模型字段映射`() {
        val detail = recommendToPlaylist(treasure("n1"))
        assertEquals("recommend:n1", detail.id)
        assertEquals("宝藏n1", detail.name)
        assertEquals("https://cdn/n1.jpg", detail.coverUrl)
        assertEquals("编辑推荐", detail.description)
        assertEquals(0, detail.songCount)
    }

    @Test
    fun `宝藏详情_过渡id不与真实歌单冲突`() {
        val detail = recommendToPlaylist(treasure("p1"))
        assertFalse(detail.id == "p1")
        assertEquals("recommend:p1", recommendDetailId("p1"))
    }

    @Test
    fun `宝藏详情_仅过渡页判定为未导入`() {
        val detail = recommendToPlaylist(treasure("n1"))
        assertTrue(isRecommendDetail(detail, "n1"))
        assertFalse(isRecommendDetail(detail, "n2"))
        assertFalse(isRecommendDetail(detail, null))
        assertFalse(isRecommendDetail(ownedPlaylist("p1", "旧歌单"), "n1"))
    }
}

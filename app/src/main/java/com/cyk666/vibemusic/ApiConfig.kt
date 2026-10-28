package com.cyk666.vibemusic

/**
 * 后端域名的**唯一**来源（CODE-REVIEW-2026-10-28 CODE-6）。
 *
 * 原来域名硬编码两处且格式不同：`VibeApi.BASE_URL = "https://vibe.cyk666.top/"`
 * 与 `Song.streamUrl()` 的 `.authority("vibe.cyk666.top")`。漏改任意一处就是
 * "登录/列表都正常，但所有播放流 404" —— 因为流地址不走 Retrofit 的 baseUrl，
 * 而是自己拼的。两处都从这里取值，`ApiHostTest` 断言两边 host 一致。
 */
const val API_HOST = "vibe.cyk666.top"

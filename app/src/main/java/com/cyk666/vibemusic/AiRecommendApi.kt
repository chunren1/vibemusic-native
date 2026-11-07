package com.cyk666.vibemusic

/**
 * AI 推荐（服务端契约，2026-11-07）：
 * `GET /api/recommend/ai?refresh=false|true`
 *
 * - 成功：data {songs, greeting, reason, type}，songs 结构与
 *   `/api/recommend/personalized` 完全一致 → 直接复用 [parsePersonalized]。
 * - 未登录 / 生成失败 / 命中不足：`data.songs = []`（**不是错误**，UI 隐藏板块）。
 * - `refresh=true` 且当天配额（3 次/天）用完：HTTP 429 + 信封 `code=429`，
 *   message 形如「今天的 AI 推荐次数已用完（3 次/天），明天再来」。
 *
 * 刻意不复用 `VibeApi.personalized`：两条路径的配额语义不同（AI 是花钱的、
 * 每天 3 次；personalized 免费），共用入口迟早会在某次改动里串味。
 */
fun buildAiRecommendPath(refresh: Boolean): String = "api/recommend/ai?refresh=$refresh"

/**
 * GET /api/recommend/ai → [PersonalizedResult]。
 *
 * 服务端 429 会被 [VibeApi.rawGet] 转成异常（HTTP 429 → "Request HTTP 429 …"；
 * 若 200 信封里带 code=429 → parsePersonalized 的 "…code=429…"），两种情况
 * 异常信息里都含 "429"，由 [AiRecommendState] 判配额耗尽。
 */
suspend fun VibeApi.aiRecommend(refresh: Boolean = false): PersonalizedResult =
    parsePersonalized(rawGet(buildAiRecommendPath(refresh)))

package com.cyk666.vibemusic

// Track A: hotspot stale-while-revalidate (memory-only; process death refetch
// is acceptable — no DataStore JSON bloat).
//
// Sections and TTLs:
// - Discover banner / personalized / random / recommend → 30 min
// - Mine playlists → 5 min
// - Search history → local already, untouched.
//
// Rule: fresh cache (< TTL) shows instantly, no reload. Stale cache shows
// immediately + refreshes silently in background (no full-screen reload
// flash). No cache → skeleton as today (blocking load with spinner).

/** Discover hotspot sections share one 30-min TTL. */
const val HOTSPOT_DISCOVER_TTL_MS = 30 * 60 * 1000L

/** Mine playlists refresh faster: 5-min TTL. */
const val HOTSPOT_PLAYLISTS_TTL_MS = 5 * 60 * 1000L

enum class HotspotSection {
    BANNERS,
    DAILY,
    GUESS,
    HOT,
    PLAYLISTS
}

/** Pure: TTL for a hotspot section (playlists 5 min, everything else 30 min). */
fun hotspotTtlMs(section: HotspotSection): Long = when (section) {
    HotspotSection.PLAYLISTS -> HOTSPOT_PLAYLISTS_TTL_MS
    HotspotSection.BANNERS,
    HotspotSection.DAILY,
    HotspotSection.GUESS,
    HotspotSection.HOT -> HOTSPOT_DISCOVER_TTL_MS
}

/**
 * Pure staleness rule: never-loaded (timestamp <= 0), non-positive TTL, or
 * age >= TTL → stale. Future timestamps (clock skew) → fresh.
 */
fun isStale(timestampMs: Long, ttlMs: Long, nowMs: Long): Boolean {
    if (timestampMs <= 0L) return true
    if (ttlMs <= 0L) return true
    return nowMs - timestampMs >= ttlMs
}

/**
 * Pure SWR gate: show cached content immediately and refresh silently in
 * background only when there IS cached content and it is stale. Background
 * refresh failures stay silent (stale content keeps showing).
 */
fun shouldBackgroundRefresh(
    hasCached: Boolean,
    timestampMs: Long,
    ttlMs: Long,
    nowMs: Long
): Boolean = hasCached && isStale(timestampMs, ttlMs, nowMs)

/**
 * Pure load gate for a hotspot section: blocking (skeleton) load only when
 * there is nothing cached; otherwise rely on [shouldBackgroundRefresh] for
 * the silent path.
 */
fun needsBlockingLoad(hasCached: Boolean): Boolean = !hasCached

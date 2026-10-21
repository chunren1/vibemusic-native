package com.cyk666.vibemusic

/**
 * Top-level navigation destinations of the single-Activity app.
 *
 * Extracted from `MainActivity.kt` (refactor step C1) — declaration only, no
 * behavior change. Keeping it in its own file makes the navigation surface
 * greppable without opening the 3.9k-line Activity.
 */
sealed interface Screen {
    data object Discover : Screen
    data object Search : Screen
    data object Player : Screen
    data object Queue : Screen
    data object Login : Screen
    data object Mine : Screen
    data object Offline : Screen
    data object History : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object Playlists : Screen
    data class PlaylistDetail(val playlist: Playlist) : Screen
}

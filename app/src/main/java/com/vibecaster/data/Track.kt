package com.vibecaster.data

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val artworkUri: String?,
    val durationMs: Long,
    val fromYouTube: Boolean = false,
    /** Original page URL (YouTube). Used to re-resolve expired stream URLs. */
    val sourceUrl: String? = null
)

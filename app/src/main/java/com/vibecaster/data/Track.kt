package com.vibecaster.data

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val artworkUri: String?,
    val durationMs: Long,
    val fromYouTube: Boolean = false
)

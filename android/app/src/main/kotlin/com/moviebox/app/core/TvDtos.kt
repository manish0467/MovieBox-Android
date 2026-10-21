package com.moviebox.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A playlist the user configured: a URL or a local file path. */
@Serializable
data class TvPlaylist(
    val source: String,
    val isFile: Boolean = false,
)

/** The request for a channel list. */
@Serializable
data class TvChannelsRequest(
    val query: String = "",
    val limit: Int = 300,
)

/** The channels of the configured playlists. */
@Serializable
data class TvChannelsPayload(
    val channels: List<TvChannel> = emptyList(),
)

/** One channel of a playlist. */
@Serializable
data class TvChannel(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val group: String = "",
    /** The engine names it `stream_url`; the wire format is shared with the
     *  terminal client, so it is not renamed here. */
    @SerialName("stream_url") val streamUrl: String = "",
)

/** The playlist list the bridge returns after a change. */
@Serializable
data class TvPlaylistsPayload(
    val playlists: List<TvPlaylist> = emptyList(),
)

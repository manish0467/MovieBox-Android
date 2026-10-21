//! Live TV: M3U playlists and the channels they carry.
//!
//! The engine already parses M3U playlists (`providers::tv`), and the
//! playlist list itself lives in the shared config, so the bridge only has to
//! wire the two together and do the filtering in Rust — a large playlist holds
//! tens of thousands of channels, which would be far too much to push across
//! JNI on every keystroke.

use serde::{Deserialize, Serialize};

/// A playlist the user configured: a URL or a local file path.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TvPlaylist {
    pub source: String,
    pub is_file: bool,
}

/// The playlist list, as the bridge hands it over.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TvPlaylistsPayload {
    pub playlists: Vec<TvPlaylist>,
}

/// The request for a channel list.
#[derive(Debug, Clone, Deserialize)]
pub struct TvChannelsRequest {
    /// Free text to match against the channel name and group.
    #[serde(default)]
    pub query: String,
    /// The most channels to return.
    #[serde(default = "default_channel_limit")]
    pub limit: usize,
}

fn default_channel_limit() -> usize {
    300
}

/// The channels of the configured playlists.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TvChannelsPayload {
    pub channels: Vec<moviebox_tui::providers::tv::Channel>,
}

/// The channels of the configured playlists.
pub async fn channels(request: &TvChannelsRequest) -> Result<TvChannelsPayload, String> {
    let playlists = moviebox_tui::config::load_tv_playlists();
    if playlists.is_empty() {
        return Ok(TvChannelsPayload {
            channels: Vec::new(),
        });
    }

    let parser = moviebox_tui::providers::tv::M3UParser::new();
    let mut results = Vec::new();
    for source in &playlists {
        match parser.fetch_playlist(source).await {
            Ok(list) => results.extend(list),
            Err(error) => {
                log::warn!("tv playlist {source} failed: {error}");
            }
        }
    }

    // The terminal client drops the empty and duplicate channels, so both
    // frontends show the same list.
    let mut seen = std::collections::HashSet::new();
    let mut channels: Vec<_> = results
        .into_iter()
        .filter(|channel| !channel.stream_url.is_empty() && seen.insert(channel.stream_url.clone()))
        .collect();

    let query = request.query.trim().to_lowercase();
    if !query.is_empty() {
        channels.retain(|channel| {
            channel.name.to_lowercase().contains(&query)
                || channel.group.to_lowercase().contains(&query)
                || channel.id.to_lowercase().contains(&query)
        });
    }

    channels.truncate(request.limit.max(1));
    Ok(TvChannelsPayload { channels })
}

/// The playable source of a channel.
pub fn resolve(
    stream_url: &str,
) -> Result<moviebox_tui::providers::models::PlaybackSource, String> {
    let url = stream_url.trim();
    if url.is_empty() {
        return Err("the channel has no stream url".to_string());
    }
    Ok(moviebox_tui::providers::models::PlaybackSource {
        provider: moviebox_tui::providers::models::ProviderKind::MovieBox,
        url: url.to_string(),
        headers: vec![],
        subtitle: None,
        source_label: "Live TV".to_string(),
        max_height: None,
    })
}

/// Adds a playlist to the shared list.
pub fn add_playlist(source: &str) -> Result<TvPlaylistsPayload, String> {
    let trimmed = source.trim();
    if trimmed.is_empty() {
        return Err("a playlist needs a url or a file path".to_string());
    }
    let mut playlists = moviebox_tui::config::load_tv_playlists();
    if !playlists.iter().any(|item| item == trimmed) {
        playlists.push(trimmed.to_string());
        moviebox_tui::config::save_tv_playlists(&playlists);
    }
    Ok(TvPlaylistsPayload {
        playlists: to_payload(playlists),
    })
}

/// Removes a playlist from the shared list.
pub fn remove_playlist(source: &str) -> Result<TvPlaylistsPayload, String> {
    let trimmed = source.trim();
    let mut playlists = moviebox_tui::config::load_tv_playlists();
    playlists.retain(|item| item != trimmed);
    moviebox_tui::config::save_tv_playlists(&playlists);
    Ok(TvPlaylistsPayload {
        playlists: to_payload(playlists),
    })
}

/// Turns the stored sources into the payload entries.
fn to_payload(sources: Vec<String>) -> Vec<TvPlaylist> {
    sources
        .into_iter()
        .map(|source| TvPlaylist {
            is_file: !moviebox_tui::net::is_http_url(&source),
            source,
        })
        .collect()
}

/// The configured playlists.
pub fn playlists() -> TvPlaylistsPayload {
    TvPlaylistsPayload {
        playlists: to_payload(moviebox_tui::config::load_tv_playlists()),
    }
}

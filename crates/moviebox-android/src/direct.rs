//! Sequential downloader that writes straight into an open file descriptor.
//!
//! The core downloader works with paths because that is what the terminal
//! client needs. Android has to write into a `MediaStore` pending file, and
//! reopening that file by path is denied even when the app owns the descriptor
//! (the path goes back through the FUSE permission check). Duplicating the
//! descriptor and writing to it keeps the download at one copy of the media on
//! disk instead of the old "download, then copy" behaviour.
//!
//! The implementation is deliberately sequential: a filename in `MediaStore`
//! can be resumed with a single `Range` request, and the file length is the
//! amount already written. Pausing, killing the process, and resuming later
//! therefore all use the same offset.

use std::fs::File as StdFile;
use std::io::SeekFrom;
use std::os::fd::OwnedFd;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

use moviebox_tui::download::{DownloadOutcome, DownloadProgress};
use reqwest::header::{CONTENT_LENGTH, CONTENT_RANGE, RANGE};
use reqwest::{Client, StatusCode};
use tokio::fs::File;
use tokio::io::{AsyncSeekExt, AsyncWriteExt};

const MAX_ATTEMPTS: usize = 4;
const RETRY_DELAY: Duration = Duration::from_secs(2);

/// Downloads `url` into `descriptor`, resuming from the current file length.
///
/// The descriptor is duplicated by the caller and stays owned by this future,
/// so closing the Kotlin `ParcelFileDescriptor` after `nativeDownloadStart`
/// returns does not affect the transfer.
pub async fn download<F>(
    client: &Client,
    url: &str,
    descriptor: OwnedFd,
    cancel: Arc<AtomicBool>,
    mut report: F,
) -> Result<DownloadOutcome, String>
where
    F: FnMut(DownloadProgress),
{
    let std_file = StdFile::from(descriptor);
    let mut file = File::from_std(std_file);
    let mut downloaded = file.metadata().await.map_err(file_error)?.len();
    if let Err(error) = file.set_len(downloaded).await {
        return Err(file_error(error));
    }
    if let Err(error) = file.seek(SeekFrom::Start(downloaded)).await {
        return Err(file_error(error));
    }

    let mut total: Option<u64> = None;
    let mut attempt = 0usize;
    let started = Instant::now();

    while attempt < MAX_ATTEMPTS {
        if cancel.load(Ordering::Relaxed) {
            return Ok(paused(&mut file, downloaded).await);
        }
        attempt += 1;
        let mut request = client.get(url);
        if downloaded > 0 {
            request = request.header(RANGE, format!("bytes={downloaded}-"));
        }
        let response = match request.send().await {
            Ok(response) => response,
            Err(_) if attempt < MAX_ATTEMPTS => {
                sleep_or_cancel(&cancel, RETRY_DELAY * attempt as u32).await;
                continue;
            }
            Err(error) => return Err(network_error(&error)),
        };

        let status = response.status();
        if status == StatusCode::RANGE_NOT_SATISFIABLE {
            if let Some(remote_total) = content_range_total(response.headers()) {
                total = Some(remote_total);
                if remote_total == downloaded {
                    return finish(&mut file, downloaded).await;
                }
            }
            if attempt < MAX_ATTEMPTS {
                sleep_or_cancel(&cancel, RETRY_DELAY * attempt as u32).await;
                continue;
            }
            return Err("The server rejected the resume range.".to_string());
        }
        if !status.is_success() {
            if (status == StatusCode::TOO_MANY_REQUESTS || status.is_server_error())
                && attempt < MAX_ATTEMPTS
            {
                sleep_or_cancel(&cancel, RETRY_DELAY * attempt as u32).await;
                continue;
            }
            return Err(http_error(status));
        }

        let resumed = status == StatusCode::PARTIAL_CONTENT;
        if downloaded > 0 && !resumed {
            // The server ignored the Range header. Start over rather than
            // gluing a full response onto the existing bytes.
            file.set_len(0).await.map_err(file_error)?;
            file.seek(SeekFrom::Start(0)).await.map_err(file_error)?;
            downloaded = 0;
        }
        if resumed
            && let Some(start) = content_range_start(response.headers())
            && start != downloaded
        {
            return Err(format!(
                "The server resumed at byte {start}, expected {downloaded}."
            ));
        }

        let remote_total = content_range_total(response.headers()).or_else(|| {
            response
                .headers()
                .get(CONTENT_LENGTH)
                .and_then(|value| value.to_str().ok())
                .and_then(|value| value.parse::<u64>().ok())
                .and_then(|length| if resumed { None } else { Some(length) })
        });
        if let Some(remote_total) = remote_total {
            total = Some(remote_total);
        }
        report(progress(downloaded, total, attempt, started));

        let mut response = response;
        let mut retry = false;
        loop {
            if cancel.load(Ordering::Relaxed) {
                return Ok(paused(&mut file, downloaded).await);
            }
            let chunk = match response.chunk().await {
                Ok(Some(chunk)) => chunk,
                Ok(None) => break,
                Err(_) => {
                    retry = true;
                    break;
                }
            };
            if chunk.is_empty() {
                continue;
            }
            if let Err(error) = file.write_all(&chunk).await {
                return Err(file_error(error));
            }
            downloaded += chunk.len() as u64;
            report(progress(downloaded, total, attempt, started));
        }

        if retry {
            if attempt < MAX_ATTEMPTS {
                sleep_or_cancel(&cancel, RETRY_DELAY * attempt as u32).await;
                continue;
            }
            return Err("The connection to the download server was lost.".to_string());
        }

        if let Some(expected) = total
            && downloaded < expected
        {
            if attempt < MAX_ATTEMPTS {
                sleep_or_cancel(&cancel, RETRY_DELAY * attempt as u32).await;
                continue;
            }
            return Err(format!(
                "Download stopped at {downloaded} of {expected} bytes."
            ));
        }
        return finish(&mut file, downloaded).await;
    }

    Err("The download server did not respond after several attempts.".to_string())
}

async fn finish(file: &mut File, bytes: u64) -> Result<DownloadOutcome, String> {
    file.flush().await.map_err(file_error)?;
    let _ = file.sync_all().await;
    Ok(DownloadOutcome::Completed { bytes })
}

async fn paused(file: &mut File, bytes: u64) -> DownloadOutcome {
    let _ = file.flush().await;
    DownloadOutcome::Paused { bytes }
}

fn progress(
    downloaded: u64,
    total: Option<u64>,
    attempt: usize,
    started: Instant,
) -> DownloadProgress {
    let seconds = started.elapsed().as_secs_f64().max(0.001);
    DownloadProgress {
        downloaded,
        total,
        bytes_per_second: downloaded as f64 / seconds,
        attempt,
        workers: 1,
    }
}

async fn sleep_or_cancel(cancel: &AtomicBool, delay: Duration) {
    let deadline = Instant::now() + delay;
    while Instant::now() < deadline {
        if cancel.load(Ordering::Relaxed) {
            return;
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
}

fn content_range_start(headers: &reqwest::header::HeaderMap) -> Option<u64> {
    let value = headers.get(CONTENT_RANGE)?.to_str().ok()?;
    let range = value.strip_prefix("bytes ")?;
    let (start, _) = range.split_once('-')?;
    start.parse().ok()
}

fn content_range_total(headers: &reqwest::header::HeaderMap) -> Option<u64> {
    let value = headers.get(CONTENT_RANGE)?.to_str().ok()?;
    let (_, total) = value.rsplit_once('/')?;
    total.parse().ok()
}

fn file_error(error: std::io::Error) -> String {
    format!("File write error: {}.", error.kind())
}

fn network_error(error: &reqwest::Error) -> String {
    if error.is_timeout() {
        "Connection timed out.".to_string()
    } else if error.is_connect() {
        "Cannot reach download server.".to_string()
    } else {
        "Connection to server was lost.".to_string()
    }
}

fn http_error(status: StatusCode) -> String {
    match status {
        StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN => {
            format!("Server refused download (HTTP {}).", status.as_u16())
        }
        StatusCode::NOT_FOUND | StatusCode::GONE => {
            "File is no longer available on server.".to_string()
        }
        StatusCode::TOO_MANY_REQUESTS => "Server rate limit exceeded. Try again later.".to_string(),
        other if other.is_server_error() => {
            format!("Server error (HTTP {}). Try again later.", other.as_u16())
        }
        other => format!("Server returned HTTP {}.", other.as_u16()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use reqwest::header::{HeaderMap, HeaderValue};

    #[test]
    fn parses_content_range() {
        let mut headers = HeaderMap::new();
        headers.insert(
            CONTENT_RANGE,
            HeaderValue::from_static("bytes 1024-2047/2048"),
        );
        assert_eq!(content_range_start(&headers), Some(1024));
        assert_eq!(content_range_total(&headers), Some(2048));
    }

    #[test]
    fn parses_unsatisfied_content_range() {
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_RANGE, HeaderValue::from_static("bytes */4096"));
        assert_eq!(content_range_start(&headers), None);
        assert_eq!(content_range_total(&headers), Some(4096));
    }

    #[test]
    fn ignores_malformed_ranges() {
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_RANGE, HeaderValue::from_static("bytes nope"));
        assert_eq!(content_range_start(&headers), None);
        assert_eq!(content_range_total(&headers), None);
    }
}

//! Download jobs for the Android app.
//!
//! The core download engine ([`moviebox_tui::download::download`]) is one path
//! for plain files. Android 10+ additionally uses [`crate::direct`], which
//! writes the response straight into an open MediaStore descriptor so the app
//! does not need a second full-size copy. The app needs a *manager* on top:
//! several jobs at once, progress that can be polled, and the ability to pause
//! a job.
//!
//! The bridge owns the jobs, so the Kotlin side only has to start, list, pause
//! and remove them.

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

/// The request the app sends to start a download.
#[derive(Debug, Clone, Deserialize)]
pub struct DownloadRequest {
    /// The signed CDN URL of the media.
    pub url: String,
    /// Headers the CDN needs, for example the MovieBox user agent.
    #[serde(default)]
    pub headers: Vec<(String, String)>,
    /// The file name to store the media under.
    pub title: String,
    #[serde(default)]
    pub provider: String,
    /// The subject the download belongs to, so the app can group the jobs.
    #[serde(default, alias = "subjectId")]
    pub subject_id: String,
    #[serde(default)]
    pub season: usize,
    #[serde(default)]
    pub episode: usize,
    /// Where to write the file. Defaults to the core download directory.
    ///
    /// When [`Self::file_descriptor`] is set this is the exact app-private
    /// placeholder path that identifies the job; the bytes go to the
    /// descriptor instead, so it is not a base directory.
    #[serde(default)]
    pub destination: Option<String>,
    /// A MediaStore pending file descriptor to stream the body into.
    ///
    /// The app keeps the public file pending until the download completes, so
    /// it never has to keep a second full-size copy in app storage.
    #[serde(default, alias = "fileDescriptor")]
    pub file_descriptor: Option<i32>,
    /// The public MediaStore URI that owns [`Self::file_descriptor`].
    #[serde(default, alias = "contentUri")]
    pub content_uri: Option<String>,
}

/// The state of a job, as the app sees it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum JobState {
    Queued,
    Running,
    Completed,
    Paused,
    Failed,
}

/// The progress of a job, as the app sees it.
#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobProgress {
    pub downloaded: u64,
    pub total: Option<u64>,
    pub bytes_per_second: f64,
    pub attempt: usize,
    pub workers: usize,
}

/// A running download.
pub struct DownloadJob {
    pub id: String,
    pub title: String,
    pub provider: String,
    pub subject_id: String,
    pub season: usize,
    pub episode: usize,
    pub destination: PathBuf,
    /// The public MediaStore URI for a direct-to-MediaStore download.
    pub content_uri: Option<String>,
    pub cancel: Arc<AtomicBool>,
    pub state: Mutex<JobState>,
    pub progress: Mutex<JobProgress>,
    pub error: Mutex<Option<String>>,
}

impl DownloadJob {
    /// The JSON view of the job, used by `nativeDownloadList`.
    pub fn to_json(&self) -> Value {
        let state = *self.state.lock().unwrap_or_else(|e| e.into_inner());
        let progress = self
            .progress
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        let error = self.error.lock().unwrap_or_else(|e| e.into_inner()).clone();
        json!({
            "id": self.id,
            "title": self.title,
            "provider": self.provider,
            "subjectId": self.subject_id,
            "season": self.season,
            "episode": self.episode,
            "destination": self.destination.to_string_lossy(),
            "contentUri": self.content_uri,
            "state": state,
            "error": error,
            "progress": progress,
        })
    }
}

use serde_json::{Value, json};

/// A process-wide counter, so two managers in one process cannot hand out the
/// same id either.
static JOB_ID_COUNTER: AtomicU64 = AtomicU64::new(1);

/// Keeps track of the download jobs.
#[derive(Default)]
pub struct DownloadManager {
    jobs: Mutex<std::collections::HashMap<String, Arc<DownloadJob>>>,
}

impl DownloadManager {
    pub fn new() -> Self {
        Self::default()
    }

    /// Registers a job and returns it.
    pub fn register(
        &self,
        request: &DownloadRequest,
        destination: PathBuf,
        cancel: Arc<AtomicBool>,
    ) -> Arc<DownloadJob> {
        // The Kotlin layer stores these ids across process deaths. Include the
        // process and a timestamp so a new process cannot reuse a saved `dl-1`
        // and overwrite the record of a finished download.
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let sequence = JOB_ID_COUNTER.fetch_add(1, Ordering::Relaxed);
        let id = format!("dl-{}-{timestamp}-{sequence}", std::process::id());
        let job = Arc::new(DownloadJob {
            id: id.clone(),
            title: request.title.clone(),
            provider: request.provider.clone(),
            subject_id: request.subject_id.clone(),
            season: request.season,
            episode: request.episode,
            destination,
            content_uri: request.content_uri.clone(),
            cancel,
            state: Mutex::new(JobState::Queued),
            progress: Mutex::new(JobProgress::default()),
            error: Mutex::new(None),
        });
        self.jobs
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .insert(id, job.clone());
        job
    }

    pub fn get(&self, id: &str) -> Option<Arc<DownloadJob>> {
        self.jobs
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .get(id)
            .cloned()
    }

    /// Removes a job from the list. The downloaded file is left in place.
    pub fn remove(&self, id: &str) -> bool {
        self.jobs
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(id)
            .is_some()
    }

    /// All jobs, newest first.
    pub fn list(&self) -> Vec<Arc<DownloadJob>> {
        let guard = self.jobs.lock().unwrap_or_else(|e| e.into_inner());
        let mut jobs: Vec<_> = guard.values().cloned().collect();
        jobs.sort_by(|a, b| b.id.cmp(&a.id));
        jobs
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn request_accepts_the_payload_the_app_sends() {
        // The app re-encodes the headers as an array of pairs.
        let payload = r#"{
            "url": "https://bcdn.hakunaymatata.com/resource/h265/x.mp4?sign=abc&t=1",
            "headers": [["User-Agent", "com.community.oneroom/50020117"]],
            "title": "Dune [Hindi]-1080P",
            "provider": "moviebox",
            "subjectId": "1511967966181605720",
            "season": 0,
            "episode": 0
        }"#;

        let request: DownloadRequest = serde_json::from_str(payload).expect("request");
        assert_eq!(request.title, "Dune [Hindi]-1080P");
        assert_eq!(request.provider, "moviebox");
        assert_eq!(request.subject_id, "1511967966181605720");
        assert_eq!(request.headers.len(), 1);
        assert_eq!(request.headers[0].0, "User-Agent");
        assert!(request.destination.is_none());
    }

    #[test]
    fn request_defaults_the_optional_fields() {
        let payload = r#"{ "url": "https://example.test/a.mp4", "title": "a" }"#;
        let request: DownloadRequest = serde_json::from_str(payload).expect("request");
        assert!(request.headers.is_empty());
        assert_eq!(request.season, 0);
        assert_eq!(request.episode, 0);
        assert!(request.provider.is_empty());
        assert!(request.file_descriptor.is_none());
        assert!(request.content_uri.is_none());
    }

    #[test]
    fn request_accepts_a_direct_media_descriptor() {
        let payload = r#"{
            "url": "https://example.test/a.mp4",
            "title": "a",
            "destination": "/tmp/a",
            "fileDescriptor": 42,
            "contentUri": "content://media/external/video/media/7"
        }"#;
        let request: DownloadRequest = serde_json::from_str(payload).expect("request");
        assert_eq!(request.file_descriptor, Some(42));
        assert_eq!(
            request.content_uri.as_deref(),
            Some("content://media/external/video/media/7")
        );
    }

    #[test]
    fn manager_registers_lists_and_removes_jobs() {
        let manager = DownloadManager::new();
        let request = DownloadRequest {
            url: "https://example.test/a.mp4".to_string(),
            headers: vec![],
            title: "a".to_string(),
            provider: "moviebox".to_string(),
            subject_id: "1".to_string(),
            season: 0,
            episode: 0,
            destination: None,
            file_descriptor: None,
            content_uri: None,
        };
        let job = manager.register(
            &request,
            PathBuf::from("/tmp/a.mp4"),
            std::sync::Arc::new(AtomicBool::new(false)),
        );
        assert_eq!(manager.list().len(), 1);
        assert!(manager.get(&job.id).is_some());
        assert!(manager.remove(&job.id));
        assert!(manager.list().is_empty());
        assert!(!manager.remove(&job.id));
    }

    #[test]
    fn job_json_carries_the_progress() {
        let manager = DownloadManager::new();
        let request = DownloadRequest {
            url: "https://example.test/a.mp4".to_string(),
            headers: vec![],
            title: "Dune".to_string(),
            provider: "moviebox".to_string(),
            subject_id: "1".to_string(),
            season: 1,
            episode: 2,
            destination: None,
            file_descriptor: None,
            content_uri: None,
        };
        let job = manager.register(
            &request,
            PathBuf::from("/tmp/Dune.mp4"),
            std::sync::Arc::new(AtomicBool::new(false)),
        );
        job.progress.lock().unwrap().downloaded = 42;
        *job.state.lock().unwrap() = JobState::Running;
        let value = job.to_json();
        let id = value["id"].as_str().expect("id");
        assert!(id.starts_with("dl-"));
        assert!(id.split('-').count() >= 4);
        assert_eq!(value["state"], json!("running"));
        assert_eq!(value["progress"]["downloaded"], json!(42));
        assert_eq!(value["season"], json!(1));
    }

    #[test]
    fn job_ids_are_unique_across_managers() {
        let request = DownloadRequest {
            url: "https://example.test/a.mp4".to_string(),
            headers: vec![],
            title: "a".to_string(),
            provider: "moviebox".to_string(),
            subject_id: "1".to_string(),
            season: 0,
            episode: 0,
            destination: None,
            file_descriptor: None,
            content_uri: None,
        };
        let first = DownloadManager::new();
        let second = DownloadManager::new();
        let first_id = first
            .register(
                &request,
                PathBuf::from("/tmp/a.mp4"),
                std::sync::Arc::new(AtomicBool::new(false)),
            )
            .id
            .clone();
        let second_id = second
            .register(
                &request,
                PathBuf::from("/tmp/a.mp4"),
                std::sync::Arc::new(AtomicBool::new(false)),
            )
            .id
            .clone();
        assert_ne!(first_id, second_id);
    }
}

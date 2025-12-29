-- v8: common query indexes

CREATE INDEX IF NOT EXISTS idx_remote_file_fetched_ignored ON remote_file (fetched, ignored);
CREATE INDEX IF NOT EXISTS idx_remote_file_fetched_ignored_partial
    ON remote_file (remote_file_id) WHERE fetched = 1 AND ignored = 0;

-- WHERE fetched=1 AND ignored=0 ORDER BY (rf.bytes IS NULL), rf.bytes DESC, rf.remote_file_id DESC
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_bytes
    ON remote_file (fetched, ignored, (bytes IS NULL) ASC, bytes DESC, remote_file_id DESC);

-- WHERE fetched=1 AND ignored=0 ORDER BY (rf.uploaded_ts IS NULL), rf.uploaded_ts DESC, rf.remote_file_id DESC
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_uploaded
    ON remote_file (fetched, ignored, (uploaded_ts IS NULL) ASC, uploaded_ts DESC, remote_file_id DESC);

-- WHERE fetched=1 AND ignored=0 ORDER BY rf.inserted_ts DESC, rf.remote_file_id DESC
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_inserted
    ON remote_file (fetched, ignored, inserted_ts DESC, remote_file_id DESC);

-- ORDER BY (a.last_fetch_ts IS NULL), a.last_fetch_ts DESC, a.inserted_ts DESC, a.album_id DESC
CREATE INDEX IF NOT EXISTS idx_album_sort_last_fetch
    ON album (
              (last_fetch_ts IS NULL) ASC,
              last_fetch_ts DESC,
              inserted_ts DESC,
              album_id DESC
        );

-- ORDER BY (a.created_ts IS NULL), (a.modified_ts IS NULL), a.created_ts DESC, a.modified_ts DESC, a.album_id DESC
CREATE INDEX IF NOT EXISTS idx_album_sort_created_modified
    ON album (
              (created_ts IS NULL) ASC,
              (modified_ts IS NULL) ASC,
              created_ts DESC,
              modified_ts DESC,
              album_id DESC
        );

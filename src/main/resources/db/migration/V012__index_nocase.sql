-- v12: index COLLATE NOCASE

-- For exact but case-insensitive queries

CREATE INDEX IF NOT EXISTS idx_album_urlid_nocase ON album (gid COLLATE NOCASE);
CREATE INDEX IF NOT EXISTS idx_album_uploader_nocase ON album (uploader COLLATE NOCASE);

CREATE INDEX IF NOT EXISTS idx_remote_file_urlid_nocase ON remote_file (urlid COLLATE NOCASE);
CREATE INDEX IF NOT EXISTS idx_remote_file_uploader_nocase ON remote_file (uploader COLLATE NOCASE);


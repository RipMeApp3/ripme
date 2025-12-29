-- v11: index local rating

CREATE INDEX IF NOT EXISTS idx_album_local_rating ON album (local_rating);

CREATE INDEX IF NOT EXISTS idx_remote_file_local_rating ON remote_file (local_rating);


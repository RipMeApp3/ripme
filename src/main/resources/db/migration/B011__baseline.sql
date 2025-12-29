-- baseline at v11

-- db_flags is currently used to disable expensive triggers when migrating data.
-- Remember to delete all flags for normal operation!
CREATE TABLE IF NOT EXISTS db_flags (
    flag TEXT PRIMARY KEY
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS ripper (
    ripper_id INTEGER PRIMARY KEY,
    name      TEXT NOT NULL, -- AbstractRipper simple class name
    host      TEXT NOT NULL, -- getHost()
    UNIQUE (name, host)
);

CREATE INDEX IF NOT EXISTS idx_ripper_name ON ripper (name);
CREATE INDEX IF NOT EXISTS idx_ripper_host ON ripper (host);

CREATE TABLE IF NOT EXISTS mime_type (
    mime_type_id INTEGER PRIMARY KEY,
    -- type/subtype only; lowercase only; no parameters
    -- image/jpeg, image/png, image/jxl, video/mp4, video/webm
    -- note: NOT image/jpg
    name         TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS album (
    album_id      INTEGER PRIMARY KEY,
    ripper_id     INTEGER NOT NULL,                                      -- ExampleRipper
    gid           TEXT    NOT NULL,                                      -- album1
    -- Not splitting album url because it won't be as much data as remote file url
    url           TEXT    NOT NULL,                                      -- https://example.com/album1/
    uploader      TEXT,
    title         TEXT,
    description   TEXT,
    created_ts    INTEGER,                                               -- Created date of the album on the server, unix millis
    modified_ts   INTEGER,                                               -- Modified date of the album on the server, unix millis
    fetch_count   INTEGER NOT NULL DEFAULT 0 CHECK (fetch_count >= 0),   -- Local number of times that the ripper started
    hidden        BOOLEAN          DEFAULT 0 CHECK (hidden IN (0, 1)),   -- Unable to view on server
    removed       BOOLEAN          DEFAULT 0 CHECK (removed IN (0, 1)),  -- Removed from server
    local_rating  INTEGER,                                               -- Local arbitrary user rating, not constrained
    sum_rf_bytes  INTEGER NOT NULL DEFAULT 0,                            -- Read-only sum of remote_file bytes in album where fetched=1 and ignored=0
    cnt_rf        INTEGER NOT NULL DEFAULT 0,                            -- Read-only count of remote_files in album where fetched=1 and ignored=0
    last_fetch_ts INTEGER,                                               -- Date last fetch started, unix millis
    inserted_ts   INTEGER NOT NULL DEFAULT (UNIXEPOCH('subsec') * 1000), -- Date first inserted into db, unix millis
    FOREIGN KEY (ripper_id) REFERENCES ripper (ripper_id) ON DELETE RESTRICT,
    UNIQUE (ripper_id, gid),
    UNIQUE (ripper_id, url)
);

CREATE INDEX IF NOT EXISTS idx_album_ripper_id_uploader ON album (ripper_id, uploader);
CREATE INDEX IF NOT EXISTS idx_album_created_ts ON album (created_ts);
CREATE INDEX IF NOT EXISTS idx_album_modified_ts ON album (modified_ts);
CREATE INDEX IF NOT EXISTS idx_album_hidden ON album (hidden);
CREATE INDEX IF NOT EXISTS idx_album_removed ON album (removed);
CREATE INDEX IF NOT EXISTS idx_album_local_rating ON album (local_rating);
CREATE INDEX IF NOT EXISTS idx_album_sum_rf_bytes ON album (sum_rf_bytes);
CREATE INDEX IF NOT EXISTS idx_album_cnt_rf ON album (cnt_rf);
CREATE INDEX IF NOT EXISTS idx_album_last_fetch_ts ON album (last_fetch_ts);
CREATE INDEX IF NOT EXISTS idx_album_inserted_ts ON album (inserted_ts);
CREATE INDEX IF NOT EXISTS idx_album_sort_last_fetch
    ON album (
              (last_fetch_ts IS NULL) ASC,
              last_fetch_ts DESC,
              inserted_ts DESC,
              album_id DESC
        );
CREATE INDEX IF NOT EXISTS idx_album_sort_created_modified
    ON album (
              (created_ts IS NULL) ASC,
              (modified_ts IS NULL) ASC,
              created_ts DESC,
              modified_ts DESC,
              album_id DESC
        );

CREATE TABLE IF NOT EXISTS remote_file (
    remote_file_id INTEGER PRIMARY KEY,
    ripper_id      INTEGER NOT NULL,                                      -- ExampleRipper
    urlid          TEXT,                                                  -- album1_file1
    -- Store url_base and url_path separately to save space;
    -- url_base is going to be the same all the time,
    -- and sqlite optimizes duplicate string storage.
    -- url = url_base + url_path
    url_base       TEXT,                                                  -- https://example.com
    url_path       TEXT,                                                  -- /album1/file1
    filename       TEXT,                                                  -- Filename of the file on the server
    mime_type_id   INTEGER,
    bytes          INTEGER,
    width_px       INTEGER,
    height_px      INTEGER,
    duration_ms    INTEGER,
    title          TEXT,
    description    TEXT,
    uploaded_ts    INTEGER,                                               -- Uploaded date of the file on the server, unix millis
    uploader       TEXT,
    aux            TEXT,                                                  -- Unstructured data that a ripper might want to store
    hidden         BOOLEAN          DEFAULT 0 CHECK (hidden IN (0, 1)),   -- Unable to view on the server
    removed        BOOLEAN          DEFAULT 0 CHECK (removed IN (0, 1)),  -- Removed from server
    fetched        BOOLEAN          DEFAULT 0 CHECK (fetched IN (0, 1)),  -- Locally fetched successfully
    ignored        BOOLEAN          DEFAULT 0 CHECK (ignored IN (0, 1)),  -- Locally ignored
    local_rating   INTEGER,                                               -- Local arbitrary user rating, not constrained
    inserted_ts    INTEGER NOT NULL DEFAULT (UNIXEPOCH('subsec') * 1000), -- Date first inserted to db, unix millis
    FOREIGN KEY (ripper_id) REFERENCES ripper (ripper_id) ON DELETE RESTRICT,
    FOREIGN KEY (mime_type_id) REFERENCES mime_type (mime_type_id) ON DELETE RESTRICT,
    UNIQUE (ripper_id, urlid),
    UNIQUE (ripper_id, url_base, url_path)
);

CREATE INDEX IF NOT EXISTS idx_remote_file_filename ON remote_file (filename);
CREATE INDEX IF NOT EXISTS idx_remote_file_mime_type_id ON remote_file (mime_type_id);
CREATE INDEX IF NOT EXISTS idx_remote_file_uploaded_ts ON remote_file (uploaded_ts);
CREATE INDEX IF NOT EXISTS idx_remote_file_ripper_id_uploader ON remote_file (ripper_id, uploader);
CREATE INDEX IF NOT EXISTS idx_remote_file_fetched ON remote_file (fetched);
CREATE INDEX IF NOT EXISTS idx_remote_file_ignored ON remote_file (ignored);
CREATE INDEX IF NOT EXISTS idx_remote_file_local_rating ON remote_file (local_rating);
CREATE INDEX IF NOT EXISTS idx_remote_file_inserted_ts ON remote_file (inserted_ts);
CREATE INDEX IF NOT EXISTS idx_remote_file_fetched_ignored ON remote_file (fetched, ignored);
CREATE INDEX IF NOT EXISTS idx_remote_file_fetched_ignored_partial
    ON remote_file (remote_file_id) WHERE fetched = 1 AND ignored = 0;
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_bytes
    ON remote_file (fetched, ignored, (bytes IS NULL) ASC, bytes DESC, remote_file_id DESC);
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_uploaded
    ON remote_file (fetched, ignored, (uploaded_ts IS NULL) ASC, uploaded_ts DESC, remote_file_id DESC);
CREATE INDEX IF NOT EXISTS idx_remote_file_sort_inserted
    ON remote_file (fetched, ignored, inserted_ts DESC, remote_file_id DESC);

CREATE TABLE IF NOT EXISTS map_album_remote_file (
    album_id       INTEGER NOT NULL,
    remote_file_id INTEGER NOT NULL,
    PRIMARY KEY (album_id, remote_file_id),
    FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    FOREIGN KEY (remote_file_id) REFERENCES remote_file (remote_file_id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_album_remote_file_reverse ON map_album_remote_file (remote_file_id, album_id);

-- To disable trigger during data migration:
-- BEGIN TRANSACTION;
-- INSERT INTO db_flags VALUES ('migration_mode');
-- INSERT INTO map_album_remote_file [...]
-- DELETE FROM db_flags;
-- COMMIT;
CREATE TRIGGER IF NOT EXISTS insert_map_album_remote_file_sum_rf_bytes
    AFTER INSERT
    ON map_album_remote_file
    WHEN (
        SELECT 1
          FROM db_flags
         WHERE flag = 'migration_mode'
         LIMIT 1
         ) IS NULL
BEGIN
    UPDATE album
       SET sum_rf_bytes = sum_rf_bytes + rf.bytes
      FROM remote_file rf
     WHERE album.album_id = NEW.album_id
       AND rf.remote_file_id = NEW.remote_file_id
       AND rf.bytes IS NOT NULL
       AND rf.fetched = 1
       AND rf.ignored = 0;
END;

CREATE TRIGGER IF NOT EXISTS delete_map_album_remote_file_sum_rf_bytes
    AFTER DELETE
    ON map_album_remote_file
BEGIN
    UPDATE album
       SET sum_rf_bytes = sum_rf_bytes - rf.bytes
      FROM remote_file rf
     WHERE album.album_id = OLD.album_id
       AND rf.remote_file_id = OLD.remote_file_id
       AND rf.bytes IS NOT NULL
       AND rf.fetched = 1
       AND rf.ignored = 0;
END;

CREATE TRIGGER IF NOT EXISTS update_remote_file_album_sum_rf_bytes
    AFTER UPDATE OF bytes, fetched, ignored
    ON remote_file
    WHEN OLD.bytes IS NOT NULL OR NEW.bytes IS NOT NULL
BEGIN
    UPDATE album
       SET sum_rf_bytes = sum_rf_bytes + delta.val
      FROM (
          SELECT COALESCE(NEW.bytes, 0) * NEW.fetched * (1 - NEW.ignored)
                     - COALESCE(OLD.bytes, 0) * OLD.fetched * (1 - OLD.ignored)
                     AS val
           ) delta
      JOIN map_album_remote_file marf ON marf.remote_file_id = NEW.remote_file_id
     WHERE album.album_id = marf.album_id;
END;

CREATE TRIGGER IF NOT EXISTS delete_remote_file_album_sum_rf_bytes
    BEFORE DELETE -- Must be BEFORE because map_album_remote_file has ON DELETE CASCADE
    ON remote_file
    WHEN OLD.bytes IS NOT NULL
        AND OLD.fetched = 1
        AND OLD.ignored = 0
BEGIN
    UPDATE album
       SET sum_rf_bytes = sum_rf_bytes - OLD.bytes
      FROM map_album_remote_file marf
     WHERE album.album_id = marf.album_id
       AND marf.remote_file_id = OLD.remote_file_id;
END;

-- To disable trigger during data migration:
-- BEGIN TRANSACTION;
-- INSERT INTO db_flags VALUES ('migration_mode');
-- INSERT INTO map_album_remote_file [...]
-- DELETE FROM db_flags;
-- COMMIT;
CREATE TRIGGER IF NOT EXISTS insert_map_album_remote_file_cnt_rf
    AFTER INSERT
    ON map_album_remote_file
    WHEN (
        SELECT 1
          FROM db_flags
         WHERE flag = 'migration_mode'
         LIMIT 1
         ) IS NULL
BEGIN
    UPDATE album
       SET cnt_rf = cnt_rf + 1
      FROM remote_file rf
     WHERE album.album_id = NEW.album_id
       AND rf.remote_file_id = NEW.remote_file_id
       AND rf.fetched = 1
       AND rf.ignored = 0;
END;

CREATE TRIGGER IF NOT EXISTS delete_map_album_remote_file_cnt_rf
    AFTER DELETE
    ON map_album_remote_file
BEGIN
    UPDATE album
       SET cnt_rf = cnt_rf - 1
      FROM remote_file rf
     WHERE album.album_id = OLD.album_id
       AND rf.remote_file_id = OLD.remote_file_id
       AND rf.fetched = 1
       AND rf.ignored = 0;
END;

CREATE TRIGGER IF NOT EXISTS update_remote_file_album_cnt_rf
    AFTER UPDATE OF fetched, ignored
    ON remote_file
BEGIN
    UPDATE album
       SET cnt_rf = cnt_rf + delta.val
      FROM (
          SELECT NEW.fetched * (1 - NEW.ignored)
                     - OLD.fetched * (1 - OLD.ignored)
                     AS val
           ) delta
      JOIN map_album_remote_file marf ON marf.remote_file_id = NEW.remote_file_id
     WHERE album.album_id = marf.album_id;
END;

CREATE TRIGGER IF NOT EXISTS delete_remote_file_album_cnt_rf
    BEFORE DELETE -- Must be BEFORE because map_album_remote_file has ON DELETE CASCADE
    ON remote_file
    WHEN OLD.fetched = 1
        AND OLD.ignored = 0
BEGIN
    UPDATE album
       SET cnt_rf = cnt_rf - 1
      FROM map_album_remote_file marf
     WHERE album.album_id = marf.album_id
       AND marf.remote_file_id = OLD.remote_file_id;
END;

CREATE TABLE tag (
    tag_id INTEGER PRIMARY KEY,
    name   TEXT NOT NULL,
    local  BOOLEAN DEFAULT 0 CHECK (local IN (0, 1)),
    UNIQUE (name, local)
);

CREATE TABLE IF NOT EXISTS map_remote_file_tag (
    remote_file_id INTEGER NOT NULL,
    tag_id         INTEGER NOT NULL,
    PRIMARY KEY (remote_file_id, tag_id),
    FOREIGN KEY (remote_file_id) REFERENCES remote_file (remote_file_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_remote_file_tag_reverse ON map_remote_file_tag (tag_id, remote_file_id);

CREATE TABLE IF NOT EXISTS map_album_tag (
    album_id INTEGER NOT NULL,
    tag_id   INTEGER NOT NULL,
    PRIMARY KEY (album_id, tag_id),
    FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_album_tag_reverse ON map_album_tag (tag_id, album_id);

CREATE TRIGGER IF NOT EXISTS delete_unused_remote_file_tag
    AFTER DELETE
    ON map_remote_file_tag
BEGIN
    DELETE
      FROM tag
     WHERE tag.tag_id = OLD.tag_id
       AND NOT (
         EXISTS (
             SELECT 1
               FROM map_album_tag map
              WHERE map.tag_id = OLD.tag_id
                ) OR
         EXISTS (
             SELECT 1
               FROM map_remote_file_tag map
              WHERE map.tag_id = OLD.tag_id
                )
         );
END;

CREATE TRIGGER IF NOT EXISTS delete_unused_album_tag
    AFTER DELETE
    ON map_album_tag
BEGIN
    DELETE
      FROM tag
     WHERE tag.tag_id = OLD.tag_id
       AND NOT (
         EXISTS (
             SELECT 1
               FROM map_album_tag map
              WHERE map.tag_id = OLD.tag_id
                ) OR
         EXISTS (
             SELECT 1
               FROM map_remote_file_tag map
              WHERE map.tag_id = OLD.tag_id
                )
         );
END;

-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS remote_file_fts5 USING fts5 (
    title,
    description,
    content='remote_file',
    content_rowid='remote_file_id',
    tokenize='porter unicode61 remove_diacritics 2',
);

CREATE TRIGGER IF NOT EXISTS insert_remote_file_fts5
    AFTER INSERT
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(rowid, title, description)
    VALUES (new.remote_file_id, new.title, new.description);
END;

CREATE TRIGGER IF NOT EXISTS delete_remote_file_fts5
    AFTER DELETE
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(remote_file_fts5, rowid, title, description)
    VALUES ('delete', old.remote_file_id, old.title, old.description);
END;

CREATE TRIGGER IF NOT EXISTS update_remote_file_fts5
    AFTER UPDATE
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(remote_file_fts5, rowid, title, description)
    VALUES ('delete', old.remote_file_id, old.title, old.description);
    INSERT INTO remote_file_fts5(rowid, title, description)
    VALUES (new.remote_file_id, new.title, new.description);
END;

INSERT INTO remote_file_fts5(remote_file_fts5)
VALUES ('rebuild');

-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS album_fts5 USING fts5 (
    title,
    description,
    content='album',
    content_rowid='album_id',
    tokenize='porter unicode61 remove_diacritics 2',
);

CREATE TRIGGER IF NOT EXISTS insert_album_fts5
    AFTER INSERT
    ON album
BEGIN
    INSERT INTO album_fts5(rowid, title, description)
    VALUES (new.album_id, new.title, new.description);
END;

CREATE TRIGGER IF NOT EXISTS delete_album_fts5
    AFTER DELETE
    ON album
BEGIN
    INSERT INTO album_fts5(album_fts5, rowid, title, description)
    VALUES ('delete', old.album_id, old.title, old.description);
END;

CREATE TRIGGER IF NOT EXISTS update_album_fts5
    AFTER UPDATE
    ON album
BEGIN
    INSERT INTO album_fts5(album_fts5, rowid, title, description)
    VALUES ('delete', old.album_id, old.title, old.description);
    INSERT INTO album_fts5(rowid, title, description)
    VALUES (new.album_id, new.title, new.description);
END;

INSERT INTO album_fts5(album_fts5)
VALUES ('rebuild');

-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS tag_fts5 USING fts5 (
    name,
    content='tag',
    content_rowid='tag_id',
    tokenize='porter unicode61 remove_diacritics 2',
);

CREATE TRIGGER IF NOT EXISTS insert_tag_fts5
    AFTER INSERT
    ON tag
BEGIN
    INSERT INTO tag_fts5(rowid, name)
    VALUES (NEW.tag_id, NEW.name);
END;

CREATE TRIGGER IF NOT EXISTS delete_tag_fts5
    AFTER DELETE
    ON tag
BEGIN
    INSERT INTO tag_fts5(tag_fts5, rowid, name)
    VALUES ('delete', OLD.tag_id, OLD.name);
END;

CREATE TRIGGER IF NOT EXISTS update_tag_fts5
    AFTER UPDATE
    ON tag
BEGIN
    INSERT INTO tag_fts5(tag_fts5, rowid, name)
    VALUES ('delete', OLD.tag_id, OLD.name);
    INSERT INTO tag_fts5(rowid, name)
    VALUES (NEW.tag_id, NEW.name);
END;

INSERT INTO tag_fts5(tag_fts5)
VALUES ('rebuild');


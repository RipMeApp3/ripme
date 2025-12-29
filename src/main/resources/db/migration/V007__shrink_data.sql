-- v7: shrink redundant data

-- The sqlite auto-generated ROWID is never used for map tables,
-- but it still takes up 33% of space

CREATE TABLE IF NOT EXISTS map_album_remote_file_new (
    album_id       INTEGER NOT NULL,
    remote_file_id INTEGER NOT NULL,
    PRIMARY KEY (album_id, remote_file_id),
    FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    FOREIGN KEY (remote_file_id) REFERENCES remote_file (remote_file_id) ON DELETE CASCADE
) WITHOUT ROWID;

INSERT INTO map_album_remote_file_new (album_id, remote_file_id)
SELECT album_id, remote_file_id
  FROM map_album_remote_file;

DROP TABLE IF EXISTS map_album_remote_file;

ALTER TABLE map_album_remote_file_new
    RENAME TO map_album_remote_file;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_album_remote_file_reverse ON map_album_remote_file (remote_file_id, album_id);

--

DROP TRIGGER IF EXISTS delete_unused_remote_file_tag;
DROP TRIGGER IF EXISTS delete_unused_album_tag;

--

CREATE TABLE IF NOT EXISTS map_remote_file_tag_new (
    remote_file_id INTEGER NOT NULL,
    tag_id         INTEGER NOT NULL,
    PRIMARY KEY (remote_file_id, tag_id),
    FOREIGN KEY (remote_file_id) REFERENCES remote_file (remote_file_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
) WITHOUT ROWID;

INSERT INTO map_remote_file_tag_new (remote_file_id, tag_id)
SELECT remote_file_id, tag_id
  FROM map_remote_file_tag;

DROP TABLE IF EXISTS map_remote_file_tag;

ALTER TABLE map_remote_file_tag_new
    RENAME TO map_remote_file_tag;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_remote_file_tag_reverse ON map_remote_file_tag (tag_id, remote_file_id);

--

CREATE TABLE IF NOT EXISTS map_album_tag_new (
    album_id INTEGER NOT NULL,
    tag_id   INTEGER NOT NULL,
    PRIMARY KEY (album_id, tag_id),
    FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
) WITHOUT ROWID;

INSERT INTO map_album_tag_new (album_id, tag_id)
SELECT album_id, tag_id
  FROM map_album_tag;

DROP TABLE IF EXISTS map_album_tag;

ALTER TABLE map_album_tag_new
    RENAME TO map_album_tag;

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_album_tag_reverse ON map_album_tag (tag_id, album_id);

--

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

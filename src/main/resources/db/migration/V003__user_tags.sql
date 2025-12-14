-- v3: local user-defined tags

-- Always create new table then copy old table into new table, never rename old table then copy into new table:
-- https://sqlite.org/lang_altertable.html#caution

-- PRAGMA foreign_keys = OFF; -- required, but must be set in connection string due to flyway quirk

CREATE TABLE IF NOT EXISTS tag_new (
    tag_id INTEGER PRIMARY KEY,
    name   TEXT NOT NULL,
    local  BOOLEAN DEFAULT 0 CHECK (local IN (0, 1)),
    UNIQUE (name, local)
);

INSERT INTO tag_new (tag_id, name, local)
SELECT tag_id, name, 0
  FROM tag;

DROP TRIGGER IF EXISTS delete_unused_remote_file_tag;
DROP TRIGGER IF EXISTS delete_unused_album_tag;
DROP TABLE IF EXISTS tag;

ALTER TABLE tag_new
    RENAME TO tag;

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


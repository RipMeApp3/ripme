-- v2: tags for remote files and albums

CREATE TABLE IF NOT EXISTS tag (
    tag_id INTEGER PRIMARY KEY,
    name   TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS map_remote_file_tag (
    remote_file_id INTEGER NOT NULL,
    tag_id         INTEGER NOT NULL,
    PRIMARY KEY (remote_file_id, tag_id),
    FOREIGN KEY (remote_file_id) REFERENCES remote_file (remote_file_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_map_remote_file_tag_reverse ON map_remote_file_tag (tag_id, remote_file_id);

CREATE TABLE IF NOT EXISTS map_album_tag (
    album_id INTEGER NOT NULL,
    tag_id   INTEGER NOT NULL,
    PRIMARY KEY (album_id, tag_id),
    FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE
);

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

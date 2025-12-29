-- v9: aggregate bytes

ALTER TABLE album
    ADD COLUMN sum_rf_bytes INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_album_sum_rf_bytes ON album (sum_rf_bytes);

-- db_flags is currently used to disable expensive triggers when migrating data.
-- Remember to delete all flags for normal operation!
CREATE TABLE IF NOT EXISTS db_flags (
    flag TEXT PRIMARY KEY
) WITHOUT ROWID;

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

UPDATE album
   SET sum_rf_bytes = agg.sum_rf_bytes
  FROM (
      SELECT marf.album_id
           , SUM(rf.bytes) AS sum_rf_bytes
        FROM map_album_remote_file marf
        JOIN remote_file rf ON rf.remote_file_id = marf.remote_file_id
       WHERE rf.bytes IS NOT NULL
         AND rf.fetched = 1
         AND rf.ignored = 0
       GROUP BY marf.album_id
       ) agg
 WHERE album.album_id = agg.album_id;

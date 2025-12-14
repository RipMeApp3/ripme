-- v5: fts5 search

-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS remote_file_fts5 USING fts5 (
    title,
    description,
    content='remote_file',
    content_rowid='remote_file_id',
    tokenize='unicode61 remove_diacritics 2',
);

CREATE TRIGGER IF NOT EXISTS insert_remote_file_fts5
    AFTER INSERT
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(rowid, title, description)
    VALUES (NEW.remote_file_id, NEW.title, NEW.description);
END;

CREATE TRIGGER IF NOT EXISTS delete_remote_file_fts5
    AFTER DELETE
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(remote_file_fts5, rowid, title, description)
    VALUES ('delete', OLD.remote_file_id, OLD.title, OLD.description);
END;

CREATE TRIGGER IF NOT EXISTS update_remote_file_fts5
    AFTER UPDATE
    ON remote_file
BEGIN
    INSERT INTO remote_file_fts5(remote_file_fts5, rowid, title, description)
    VALUES ('delete', OLD.remote_file_id, OLD.title, OLD.description);
    INSERT INTO remote_file_fts5(rowid, title, description)
    VALUES (NEW.remote_file_id, NEW.title, NEW.description);
END;

INSERT INTO remote_file_fts5(remote_file_fts5)
VALUES ('rebuild');


-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS album_fts5 USING fts5 (
    title,
    description,
    content='album',
    content_rowid='album_id',
    tokenize='unicode61 remove_diacritics 2',
);

CREATE TRIGGER IF NOT EXISTS insert_album_fts5
    AFTER INSERT
    ON album
BEGIN
    INSERT INTO album_fts5(rowid, title, description)
    VALUES (NEW.album_id, NEW.title, NEW.description);
END;

CREATE TRIGGER IF NOT EXISTS delete_album_fts5
    AFTER DELETE
    ON album
BEGIN
    INSERT INTO album_fts5(album_fts5, rowid, title, description)
    VALUES ('delete', OLD.album_id, OLD.title, OLD.description);
END;

CREATE TRIGGER IF NOT EXISTS update_album_fts5
    AFTER UPDATE
    ON album
BEGIN
    INSERT INTO album_fts5(album_fts5, rowid, title, description)
    VALUES ('delete', OLD.album_id, OLD.title, OLD.description);
    INSERT INTO album_fts5(rowid, title, description)
    VALUES (NEW.album_id, NEW.title, NEW.description);
END;

INSERT INTO album_fts5(album_fts5)
VALUES ('rebuild');

-- Note: fields might not be sanitized or normalized. HTML or markdown tags are likely present.
CREATE VIRTUAL TABLE IF NOT EXISTS tag_fts5 USING fts5 (
    name,
    content='tag',
    content_rowid='tag_id',
    tokenize='unicode61 remove_diacritics 2',
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


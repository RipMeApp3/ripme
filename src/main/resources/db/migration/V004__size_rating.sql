-- v4: file size and local rating

-- Enable sorting by size in albums
ALTER TABLE remote_file
    ADD COLUMN bytes INTEGER;

-- Intention: NULL: unrated, 1: worst, 5: best
-- For local arbitrary use by the user, so not constrained
ALTER TABLE remote_file
    ADD COLUMN local_rating INTEGER;

ALTER TABLE album
    ADD COLUMN local_rating INTEGER;

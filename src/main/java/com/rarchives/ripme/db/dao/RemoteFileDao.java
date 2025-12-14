package com.rarchives.ripme.db.dao;

import com.rarchives.ripme.db.DatabaseManager;
import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.db.model.SplitUrl;
import com.rarchives.ripme.ripper.RipUrlId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RemoteFileDao extends BaseDao<RemoteFile> {
    private static final Logger logger = LogManager.getLogger(RemoteFileDao.class);

    public RemoteFileDao(DatabaseManager db) {
        super(db);
    }

    public Optional<RemoteFile> findById(long id) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT remote_file_id
                         , ripper.ripper_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , urlid
                         , (url_base || url_path) AS url
                         , filename
                         , mime_type.name AS mime_type
                         , width_px
                         , height_px
                         , duration_ms
                         , title
                         , description
                         , uploaded_ts
                         , uploader
                         , aux
                         , hidden
                         , removed
                         , fetched
                         , ignored
                         , bytes
                         , local_rating
                         , inserted_ts
                      FROM remote_file
                      JOIN ripper ON remote_file.ripper_id = ripper.ripper_id
                      LEFT JOIN mime_type ON remote_file.mime_type_id = mime_type.mime_type_id
                     WHERE remote_file_id = ?
                    """)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        RemoteFile remoteFile = map(rs);
                        remoteFile.setTags(getRemoteTagsForRemoteFile(conn, id));
                        return Optional.of(remoteFile);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public Optional<RemoteFile> findByRipUrlId(RipUrlId ripUrlId) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT remote_file_id
                         , ripper.ripper_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , urlid
                         , (url_base || url_path) AS url -- || is concatenation in sql
                         , filename
                         , mime_type.name AS mime_type
                         , width_px
                         , height_px
                         , duration_ms
                         , title
                         , description
                         , uploaded_ts
                         , uploader
                         , aux
                         , hidden
                         , removed
                         , fetched
                         , ignored
                         , bytes
                         , local_rating
                         , inserted_ts
                      FROM remote_file
                      JOIN ripper ON remote_file.ripper_id = ripper.ripper_id
                      LEFT JOIN mime_type ON remote_file.mime_type_id = mime_type.mime_type_id
                     WHERE (ripper.name = ?1 AND ripper.host = ?2)
                       AND (urlid = ?3 OR (url_base = ?4 AND url_path = ?5))
                     LIMIT 1
                    """)) {
                SplitUrl splitUrl = SplitUrl.of(ripUrlId.getUrl());
                stmt.setString(1, ripUrlId.getRipper().getSimpleName());
                stmt.setString(2, ripUrlId.getRipperHost());
                stmt.setString(3, ripUrlId.getRipUrlId());
                stmt.setString(4, splitUrl.getBase());
                stmt.setString(5, splitUrl.getPath());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        RemoteFile remoteFile = map(rs);
                        remoteFile.setTags(getRemoteTagsForRemoteFile(conn, remoteFile.getId()));
                        return Optional.of(remoteFile);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private static RemoteFile map(ResultSet rs) throws SQLException {
        Instant uploadedTs = rs.getObject("uploaded_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("uploaded_ts"));
        Instant insertedTs = rs.getObject("inserted_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("inserted_ts"));

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setId(rs.getLong("remote_file_id"));
        remoteFile.setRipperName(rs.getString("ripper_name"));
        remoteFile.setRipperHost(rs.getString("ripper_host"));
        remoteFile.setUrlId(rs.getString("urlid"));
        try {
            String urlString = rs.getString("url");
            if (urlString != null && !urlString.isEmpty()) {
                remoteFile.setUrl(URI.create(urlString).toURL());
            }
        } catch (MalformedURLException e) {
            logger.warn("Unable to parse url, discarding data: {}", rs.getString("url"));
        }
        remoteFile.setFilename(rs.getString("filename"));
        remoteFile.setMimeType(rs.getString("mime_type"));
        remoteFile.setWidthPx(rs.getInt("width_px"));
        remoteFile.setHeightPx(rs.getInt("height_px"));
        remoteFile.setDurationMs(rs.getInt("duration_ms"));
        remoteFile.setTitle(rs.getString("title"));
        remoteFile.setDescription(rs.getString("description"));
        remoteFile.setUploadedTs(uploadedTs);
        remoteFile.setUploader(rs.getString("uploader"));
        remoteFile.setAux(rs.getString("aux"));
        remoteFile.setHidden(rs.getBoolean("hidden"));
        remoteFile.setRemoved(rs.getBoolean("removed"));
        remoteFile.setFetched(rs.getBoolean("fetched"));
        remoteFile.setIgnored(rs.getBoolean("ignored"));
        remoteFile.setBytes(rs.getObject("bytes") == null ? null : rs.getLong("bytes")); // nullable
        remoteFile.setLocalRating(rs.getObject("local_rating") == null ? null : rs.getInt("local_rating")); // nullable
        remoteFile.setInsertedTs(insertedTs);
        return remoteFile;
    }

    private Set<String> getRemoteTagsForRemoteFile(long id) throws SQLException {
        return db.withConnection(conn -> {
            Set<String> tags = getRemoteTagsForRemoteFile(conn, id);
            return tags;
        });
    }

    /**
     * Get remote tags within a transaction
     * @param conn The connection of the transaction
     * @param id The remote_file_id
     * @return The tags
     * @throws SQLException On issue with preparing statement, executing the query, or getting the row
     */
    private Set<String> getRemoteTagsForRemoteFile(Connection conn, long id) throws SQLException {
        Set<String> tags = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT name
                  FROM map_remote_file_tag
                  INNER JOIN tag
                          ON tag.tag_id = map_remote_file_tag.tag_id
                 WHERE remote_file_id = ?
                   AND tag.local = 0
                """)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tags.add(rs.getString("name"));
                }
            }
        }
        return tags;
    }

    /**
     * Save the remote file.
     * Every value in the database is replaced with
     * the new RemoteFile values, even if they are null.
     */
    public void save(RemoteFile remoteFile) throws SQLException {
        String ensureRipper = """
                   INSERT INTO ripper (name, host)
                   VALUES (?, ?)
                -- no-op UPDATE instead of NOTHING so that ripper_id is always returned
                       ON CONFLICT (name, host) DO UPDATE
                      SET name = name
                        , host = host
                RETURNING ripper_id
                """;
        String ensureMimeType = """
                   INSERT INTO mime_type (name)
                   VALUES (?)
                -- no-op UPDATE instead of NOTHING so that mime_type_id is always returned
                       ON CONFLICT (name) DO UPDATE
                           SET name = name
                RETURNING mime_type_id
                """;
        String upsertRemoteFile = """
                   INSERT INTO remote_file ( ripper_id
                                           , urlid
                                           , url_base
                                           , url_path
                                           , filename
                                           , mime_type_id
                                           , width_px
                                           , height_px
                                           , duration_ms
                                           , title
                                           , description
                                           , uploaded_ts
                                           , uploader
                                           , aux
                                           , hidden
                                           , removed
                                           , fetched
                                           , ignored
                                           , bytes
                                           , local_rating)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                -- sqlite does not support multiple ON CONFLICT, so do not use
                -- ON CONFLICT (ripper_id, urlid) ... ON CONFLICT (ripper_id, url_base, url_path)
                       ON CONFLICT DO UPDATE
                      SET ripper_id    = COALESCE(EXCLUDED.ripper_id, remote_file.ripper_id)
                        , urlid        = COALESCE(EXCLUDED.urlid, remote_file.urlid)
                        , url_base     = COALESCE(EXCLUDED.url_base, remote_file.url_base)
                        , url_path     = COALESCE(EXCLUDED.url_path, remote_file.url_path)
                        , filename     = COALESCE(EXCLUDED.filename, remote_file.filename)
                        , mime_type_id = COALESCE(EXCLUDED.mime_type_id, remote_file.mime_type_id)
                        , width_px     = COALESCE(EXCLUDED.width_px, remote_file.width_px)
                        , height_px    = COALESCE(EXCLUDED.height_px, remote_file.height_px)
                        , duration_ms  = COALESCE(EXCLUDED.duration_ms, remote_file.duration_ms)
                        , title        = COALESCE(EXCLUDED.title, remote_file.title)
                        , description  = COALESCE(EXCLUDED.description, remote_file.description)
                        , uploaded_ts  = COALESCE(EXCLUDED.uploaded_ts, remote_file.uploaded_ts)
                        , uploader     = COALESCE(EXCLUDED.uploader, remote_file.uploader)
                        , aux          = COALESCE(EXCLUDED.aux, remote_file.aux)
                        , hidden       = COALESCE(EXCLUDED.hidden, remote_file.hidden)
                        , removed      = COALESCE(EXCLUDED.removed, remote_file.removed)
                        , fetched      = COALESCE(EXCLUDED.fetched, remote_file.fetched)
                        , ignored      = COALESCE(EXCLUDED.ignored, remote_file.ignored)
                        , bytes        = COALESCE(EXCLUDED.bytes, remote_file.bytes)
                        , local_rating = COALESCE(EXCLUDED.local_rating, remote_file.local_rating)
                RETURNING remote_file_id, inserted_ts
                """;
        db.withConnection(conn -> {
            conn.setAutoCommit(false); // Begin transaction
            try (PreparedStatement stmt1 = conn.prepareStatement(ensureRipper);
                 PreparedStatement stmt2 = conn.prepareStatement(ensureMimeType);
                 PreparedStatement stmt3 = conn.prepareStatement(upsertRemoteFile)) {
                SplitUrl splitUrl = SplitUrl.of(remoteFile.getUrl());
                String urlBase = null;
                String urlPath = null;
                if (splitUrl.url() != null) {
                    urlBase = splitUrl.getBase();
                    urlPath = splitUrl.getPath();
                }
                try {
                    stmt1.setString(1, remoteFile.getRipperName());
                    stmt1.setString(2, remoteFile.getRipperHost());
                    long ripperId;
                    try (ResultSet resultSet1 = stmt1.executeQuery()) {
                        ripperId = resultSet1.getLong("ripper_id");
                    }

                    long mimeTypeId = 0;
                    String mimeType = remoteFile.getMimeType();
                    if (mimeType != null) {
                        stmt2.setString(1, mimeType);
                        try (ResultSet resultSet1 = stmt2.executeQuery()) {
                            mimeTypeId = resultSet1.getLong("mime_type_id");
                        }
                    }

                    int i = 0;
                    stmt3.setLong(++i, ripperId);
                    stmt3.setString(++i, remoteFile.getUrlId());
                    stmt3.setString(++i, urlBase);
                    stmt3.setString(++i, urlPath);
                    stmt3.setString(++i, remoteFile.getFilename());
                    if (mimeType == null) {
                        stmt3.setNull(++i, Types.BIGINT);
                    } else {
                        stmt3.setLong(++i, mimeTypeId);
                    }
                    if (remoteFile.getWidthPx() == null) {
                        stmt3.setNull(++i, Types.INTEGER);
                    } else {
                        stmt3.setInt(++i, remoteFile.getWidthPx());
                    }
                    if (remoteFile.getHeightPx() == null) {
                        stmt3.setNull(++i, Types.INTEGER);
                    } else {
                        stmt3.setInt(++i, remoteFile.getHeightPx());
                    }
                    if (remoteFile.getDurationMs() == null) {
                        stmt3.setNull(++i, Types.INTEGER);
                    } else {
                        stmt3.setInt(++i, remoteFile.getDurationMs());
                    }
                    stmt3.setString(++i, remoteFile.getTitle());
                    stmt3.setString(++i, remoteFile.getDescription());
                    Instant uploadedTs = remoteFile.getUploadedTs();
                    if (uploadedTs == null) {
                        stmt3.setNull(++i, Types.BIGINT);
                    } else {
                        stmt3.setLong(++i, uploadedTs.toEpochMilli());
                    }
                    stmt3.setString(++i, remoteFile.getUploader());
                    stmt3.setString(++i, remoteFile.getAux());
                    stmt3.setBoolean(++i, remoteFile.isHidden());
                    stmt3.setBoolean(++i, remoteFile.isRemoved());
                    stmt3.setBoolean(++i, remoteFile.isFetched());
                    stmt3.setBoolean(++i, remoteFile.isIgnored());
                    if (remoteFile.getBytes() == null) {
                        stmt3.setNull(++i, Types.BIGINT);
                    } else {
                        stmt3.setLong(++i, remoteFile.getBytes());
                    }
                    if (remoteFile.getLocalRating() == null) {
                        stmt3.setNull(++i, Types.INTEGER);
                    } else {
                        stmt3.setInt(++i, remoteFile.getLocalRating());
                    }

                    long remoteFileId;
                    Instant insertedTs;
                    try (ResultSet resultSet2 = stmt3.executeQuery()) {
                        remoteFileId = resultSet2.getLong("remote_file_id");
                        insertedTs = Instant.ofEpochMilli(resultSet2.getLong("inserted_ts"));
                    }

                    saveRemoteFileRemoteTags(conn, remoteFileId, remoteFile);

                    conn.commit();
                    remoteFile.setId(remoteFileId);
                    remoteFile.setInsertedTs(insertedTs);
                } catch (SQLException e) {
                    logger.error("Unable to save remote file {}; url {}", remoteFile, splitUrl.url(), e);
                    conn.rollback();
                    throw e;
                }
            }
        });
    }

    private void saveRemoteFileRemoteTags(Connection conn, long remoteFileId, RemoteFile remoteFile) throws SQLException {
        Set<String> newTags = remoteFile.getTags();
        if (newTags == null || newTags.isEmpty()) {
            deleteAllRemoteTagsForRemoteFile(conn, remoteFileId);
            return;
        }
        Set<String> oldTags = getRemoteTagsForRemoteFile(conn, remoteFileId);

        HashSet<String> tagsToAdd = new HashSet<>(newTags);
        tagsToAdd.removeAll(oldTags);
        HashSet<String> tagsToRemove = new HashSet<>(oldTags);
        tagsToRemove.removeAll(newTags);

        if (!tagsToAdd.isEmpty()) {
            insertRemoteTagsForRemoteFile(conn, remoteFileId, tagsToAdd);
        }
        if (!tagsToRemove.isEmpty()) {
            deleteRemoteTagsForRemoteFile(conn, remoteFileId, tagsToRemove);
        }

    }

    private void deleteAllRemoteTagsForRemoteFile(Connection conn, long remoteFileId) throws SQLException {
        String sql = """
            DELETE
              FROM map_remote_file_tag
             WHERE remote_file_id = ?
               AND tag_id IN (
                 SELECT tag_id
                   FROM tag
                  WHERE local = 0
                             )
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, remoteFileId);
            stmt.executeUpdate();
        }
    }

    private void insertRemoteTagsForRemoteFile(Connection conn, long remoteFileId, HashSet<String> tags) throws SQLException {
        String insertTags = "INSERT INTO tag (name, local) VALUES (?, 0) ON CONFLICT DO NOTHING";
        try (PreparedStatement stmt = conn.prepareStatement(insertTags)) {
            for (String tag : tags) {
                stmt.setString(1, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        String insertRemoteFileTags = """
                INSERT INTO map_remote_file_tag (remote_file_id, tag_id)
                SELECT ?, tag_id
                  FROM tag
                 WHERE tag.name = ?
                   AND tag.local = 0
                    ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement stmt = conn.prepareStatement(insertRemoteFileTags)) {
            for (String tag : tags) {
                stmt.setLong(1, remoteFileId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void deleteRemoteTagsForRemoteFile(Connection conn, long remoteFileId, HashSet<String> tags) throws SQLException {
        String sql = """
                DELETE
                  FROM map_remote_file_tag
                 WHERE remote_file_id = ?
                   AND tag_id IN (
                     SELECT tag_id
                       FROM tag
                      WHERE tag.name = ?
                        AND tag.local = 0
                                 )
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String tag : tags) {
                stmt.setLong(1, remoteFileId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}

package com.rarchives.ripme.db.dao;

import com.rarchives.ripme.db.DatabaseManager;
import com.rarchives.ripme.db.model.Album;
import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.db.model.SplitUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class AlbumDao extends BaseDao<Album> {
    private static final Logger logger = LogManager.getLogger(AlbumDao.class);

    public AlbumDao(DatabaseManager db) {
        super(db);
    }

    public Optional<Album> findById(long id) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT album_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , gid
                         , url
                         , uploader
                         , title
                         , description
                         , created_ts
                         , modified_ts
                         , fetch_count
                         , hidden
                         , removed
                         , local_rating
                         , sum_rf_bytes
                         , last_fetch_ts
                         , inserted_ts
                      FROM album
                      JOIN ripper
                              ON album.ripper_id = ripper.ripper_id
                     WHERE album_id = ?
                    """)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Album album = map(rs);
                        album.setTags(getRemoteTagsForAlbum(conn, id));
                        return Optional.of(album);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public Optional<Album> find(Class<?> ripperClass, String host, String albumId, URL url) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT album_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , gid
                         , url
                         , uploader
                         , title
                         , description
                         , created_ts
                         , modified_ts
                         , fetch_count
                         , hidden
                         , removed
                         , local_rating
                         , sum_rf_bytes
                         , last_fetch_ts
                         , inserted_ts
                      FROM album
                      JOIN ripper
                              ON album.ripper_id = ripper.ripper_id
                     WHERE (ripper.name = ? AND ripper.host = ?)
                       AND (gid = ? OR url = ?)
                     LIMIT 1
                    """)) {
                String urlString = null;
                if (url != null) {
                    urlString = url.toString();
                }
                stmt.setString(1, ripperClass.getSimpleName());
                stmt.setString(2, host);
                stmt.setString(3, albumId);
                stmt.setString(4, urlString);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Album album = map(rs);
                        album.setTags(getRemoteTagsForAlbum(conn, album.getId()));
                        return Optional.of(album);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public Optional<Album> findByGid(Class<?> ripperClass, String host, String albumId) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT album_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , gid
                         , url
                         , uploader
                         , title
                         , description
                         , created_ts
                         , modified_ts
                         , fetch_count
                         , hidden
                         , removed
                         , local_rating
                         , sum_rf_bytes
                         , last_fetch_ts
                         , inserted_ts
                      FROM album
                      JOIN ripper
                              ON album.ripper_id = ripper.ripper_id
                     WHERE (ripper.name = ? AND ripper.host = ?)
                       AND gid = ?
                    -- (gid = ? OR url = ?)
                     LIMIT 1
                    """)) {
                stmt.setString(1, ripperClass.getSimpleName());
                stmt.setString(2, host);
                stmt.setString(3, albumId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Album album = map(rs);
                        album.setTags(getRemoteTagsForAlbum(conn, album.getId()));
                        return Optional.of(album);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public Optional<Album> findByRipperUrl(Class<?> ripperClass, String host, URL url) throws SQLException {
        return db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT album_id
                         , ripper.name AS ripper_name
                         , ripper.host AS ripper_host
                         , gid
                         , url
                         , uploader
                         , title
                         , description
                         , created_ts
                         , modified_ts
                         , fetch_count
                         , hidden
                         , removed
                         , local_rating
                         , sum_rf_bytes
                         , last_fetch_ts
                         , inserted_ts
                      FROM album
                      JOIN ripper
                              ON album.ripper_id = ripper.ripper_id
                     WHERE (ripper.name = ? AND ripper.host = ?)
                       AND url = ?
                     LIMIT 1
                    """)) {
                stmt.setString(1, ripperClass.getSimpleName());
                stmt.setString(2, host);
                stmt.setString(3, url.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Album album = map(rs);
                        album.setTags(getRemoteTagsForAlbum(conn, album.getId()));
                        return Optional.of(album);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private static Album map(ResultSet rs) throws SQLException {
        Instant createdTs = rs.getObject("created_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("created_ts"));
        Instant modifiedTs = rs.getObject("modified_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("modified_ts"));
        Instant lastFetchTs = rs.getObject("last_fetch_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("last_fetch_ts"));
        Instant insertedTs = rs.getObject("inserted_ts") == null ? null : Instant.ofEpochMilli(rs.getLong("inserted_ts"));

        Album album = new Album();
        album.setId(rs.getLong("album_id"));
        album.setRipperName(rs.getString("ripper_name"));
        album.setRipperHost(rs.getString("ripper_host"));
        album.setGid(rs.getString("gid"));
        try {
            String urlString = rs.getString("url");
            if (urlString != null && !urlString.isEmpty()) {
                album.setUrl(URI.create(urlString).toURL());
            }
        } catch (MalformedURLException e) {
            logger.error("Database has unparseable URL: {}", rs.getString("url"));
            // Ignore...
        }
        album.setUploader(rs.getString("uploader"));
        album.setTitle(rs.getString("title"));
        album.setDescription(rs.getString("description"));
        album.setCreatedTs(createdTs);
        album.setModifiedTs(modifiedTs);
        album.setFetchCount(rs.getInt("fetch_count"));
        album.setHidden(rs.getBoolean("hidden"));
        album.setRemoved(rs.getBoolean("removed"));
        album.setLocalRating(rs.getObject("local_rating") == null ? null : rs.getInt("local_rating")); // nullable
        album.setSumRfBytes(rs.getLong("sum_rf_bytes"));
        album.setLastFetchTs(lastFetchTs);
        album.setInsertedTs(insertedTs);
        return album;
    }

    private Set<String> getRemoteTagsForAlbum(long id) throws SQLException {
        return db.withConnection(conn -> {
            Set<String> tags = getRemoteTagsForAlbum(conn, id);
            return tags;
        });
    }

    /**
     * Get remote tags within a transaction
     * @param conn The connection of the transaction
     * @param id The album_id
     * @return The tags
     * @throws SQLException On issue with preparing statement, executing the query, or getting the row
     */
    private Set<String> getRemoteTagsForAlbum(Connection conn, long id) throws SQLException {
        Set<String> tags = new HashSet<>();
        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT name
                  FROM map_album_tag
                  INNER JOIN tag
                          ON tag.tag_id = map_album_tag.tag_id
                 WHERE album_id = ?
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
     * Save the album.
     * Every value in the database is replaced with
     * the new Album values, even if they are null.
     */
    public void save(Album album) throws SQLException {
        String ensureRipper = """
                   INSERT INTO ripper (name, host)
                   VALUES (?, ?)
                -- no-op UPDATE instead of NOTHING so that ripper_id is always returned
                       ON CONFLICT (name, host) DO UPDATE
                      SET name = name
                        , host = host
                RETURNING ripper_id
                """;
        String upsertAlbum = """
                   INSERT INTO album ( ripper_id
                                     , gid
                                     , url
                                     , uploader
                                     , title
                                     , description
                                     , created_ts
                                     , modified_ts
                                     , fetch_count
                                     , hidden
                                     , removed
                                     , local_rating
                                     , last_fetch_ts)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                -- sqlite does not support multiple ON CONFLICT, so do not use
                -- ON CONFLICT (ripper_id, gid) ... ON CONFLICT (ripper_id, url)
                       ON CONFLICT DO UPDATE
                      SET ripper_id     = COALESCE(EXCLUDED.ripper_id, album.ripper_id)
                        , gid           = COALESCE(EXCLUDED.gid, album.gid)
                        , url           = COALESCE(EXCLUDED.url, album.url)
                        , uploader      = COALESCE(EXCLUDED.uploader, album.uploader)
                        , title         = COALESCE(EXCLUDED.title, album.title)
                        , description   = COALESCE(EXCLUDED.description, album.description)
                        , created_ts    = COALESCE(EXCLUDED.created_ts, album.created_ts)
                        , modified_ts   = COALESCE(EXCLUDED.modified_ts, album.modified_ts)
                        , fetch_count   = COALESCE(EXCLUDED.fetch_count, album.fetch_count)
                        , hidden        = COALESCE(EXCLUDED.hidden, album.hidden)
                        , removed       = COALESCE(EXCLUDED.removed, album.removed)
                        , local_rating  = COALESCE(EXCLUDED.local_rating, album.local_rating)
                        , last_fetch_ts = CASE
                                              WHEN EXCLUDED.fetch_count > album.fetch_count THEN UNIXEPOCH('subsec') * 1000
                                              ELSE album.last_fetch_ts END
                RETURNING album_id, sum_rf_bytes, inserted_ts, last_fetch_ts
                """;
        db.withConnection(conn -> {
            try (PreparedStatement stmt1 = conn.prepareStatement(ensureRipper);
                 PreparedStatement stmt2 = conn.prepareStatement(upsertAlbum)) {
                conn.setAutoCommit(false); // Begin transaction
                try {
                    stmt1.setString(1, album.getRipperName());
                    stmt1.setString(2, album.getRipperHost());
                    long ripperId;
                    try (ResultSet resultSet1 = stmt1.executeQuery()) {
                        ripperId = resultSet1.getLong("ripper_id");
                    }
                    int i = 0;
                    stmt2.setLong(++i, ripperId);
                    stmt2.setString(++i, album.getGid());
                    stmt2.setString(++i, album.getUrl().toString());
                    stmt2.setString(++i, album.getUploader());
                    stmt2.setString(++i, album.getTitle());
                    stmt2.setString(++i, album.getDescription());
                    Instant createdTs = album.getCreatedTs();
                    if (createdTs == null) {
                        stmt2.setNull(++i, Types.BIGINT);
                    } else {
                        stmt2.setLong(++i, createdTs.toEpochMilli());
                    }
                    Instant modifiedTs = album.getModifiedTs();
                    if (modifiedTs == null) {
                        stmt2.setNull(++i, Types.BIGINT);
                    } else {
                        stmt2.setLong(++i, modifiedTs.toEpochMilli());
                    }
                    stmt2.setInt(++i, album.getFetchCount());
                    stmt2.setBoolean(++i, album.isHidden());
                    stmt2.setBoolean(++i, album.isRemoved());
                    if (album.getLocalRating() == null) {
                        stmt2.setNull(++i, Types.INTEGER);
                    } else {
                        stmt2.setInt(++i, album.getLocalRating());
                    }
                    if (album.getFetchCount() > 0) {
                        stmt2.setLong(++i, Instant.now().toEpochMilli());
                    } else {
                        stmt2.setNull(++i, Types.BIGINT);
                    }

                    long albumId;
                    long sumRfBytes;
                    Instant lastFetchTs;
                    Instant insertedTs;
                    try (ResultSet resultSet2 = stmt2.executeQuery()) {
                        albumId = resultSet2.getLong("album_id");
                        sumRfBytes = resultSet2.getLong("sum_rf_bytes");
                        lastFetchTs = resultSet2.getObject("last_fetch_ts") == null ? null : Instant.ofEpochMilli(resultSet2.getLong("last_fetch_ts"));
                        insertedTs = Instant.ofEpochMilli(resultSet2.getLong("inserted_ts"));
                        insertedTs = Instant.ofEpochMilli(resultSet2.getLong("inserted_ts"));
                    }

                    saveAlbumRemoteTags(conn, albumId, album);

                    conn.commit();
                    album.setId(albumId);
                    album.setSumRfBytes(sumRfBytes);
                    album.setLastFetchTs(lastFetchTs);
                    album.setInsertedTs(insertedTs);
                } catch (SQLException e) {
                    logger.error("Unable to save album {}", album, e);
                    conn.rollback();
                    throw e;
                }
            }
        });
    }

    public void incrementFetchCount(Album album) throws SQLException {
        String sql = """
                   UPDATE album
                      SET fetch_count   = fetch_count + 1
                        , last_fetch_ts = UNIXEPOCH('subsec') * 1000
                    WHERE album_id = ?
                       OR (ripper_id = (
                        SELECT ripper_id
                          FROM ripper
                         WHERE (name = ? AND host = ?)
                                       )
                        AND (gid = ? OR url = ?))
                RETURNING fetch_count, last_fetch_ts
                """;
        db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, album.getId());
                stmt.setString(2, album.getRipperName());
                stmt.setString(3, album.getRipperHost());
                stmt.setString(4, album.getGid());
                stmt.setString(5, album.getUrl().toString());
                ResultSet resultSet = stmt.executeQuery();
                if (resultSet.next()) {
                    int fetchCount = resultSet.getInt("fetch_count");
                    Instant lastFetchTs = Instant.ofEpochMilli(resultSet.getLong("last_fetch_ts"));
                    album.setFetchCount(fetchCount);
                    album.setLastFetchTs(lastFetchTs);
                }
            }
        });
    }

    public boolean hasAlbumRemoteFileMap(Album album, RemoteFile remoteFile) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT *
                      FROM map_album_remote_file
                     WHERE album_id = COALESCE(?1, (
                         SELECT a.album_id
                           FROM album a
                           JOIN ripper r ON r.ripper_id = a.ripper_id
                          WHERE (r.name = ?2 AND r.host = ?3)
                            AND (a.gid = ?4 OR a.url = ?5)
                                                   ))
                       AND remote_file_id = COALESCE(?6, (
                         SELECT rf.remote_file_id
                           FROM remote_file rf
                           JOIN ripper r ON r.ripper_id = rf.ripper_id
                          WHERE (r.name = ?7 AND r.host = ?8)
                            AND (rf.urlid = ?9 OR (rf.url_base = ?10 AND rf.url_path = ?11))
                                                         ))
                              )
                """;
        boolean exists = db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                URL albumUrl = album.getUrl();
                SplitUrl remoteFileSplitUrl = SplitUrl.of(remoteFile.getUrl());
                if (album.getId() == null) {
                    stmt.setNull(1, Types.INTEGER);
                } else {
                    stmt.setLong(1, album.getId());
                }
                stmt.setString(2, album.getRipperName());
                stmt.setString(3, album.getRipperHost());
                stmt.setString(4, album.getGid());
                stmt.setString(5, albumUrl == null ? null : albumUrl.toString());
                if (remoteFile.getId() == null) {
                    stmt.setNull(6, Types.INTEGER);
                } else {
                    stmt.setLong(6, remoteFile.getId());
                }
                stmt.setString(7, remoteFile.getRipperName());
                stmt.setString(8, remoteFile.getRipperHost());
                stmt.setString(9, remoteFile.getUrlId());
                stmt.setString(10, remoteFileSplitUrl.getBase());
                stmt.setString(11, remoteFileSplitUrl.getPath());
                ResultSet resultSet = stmt.executeQuery();
                return resultSet.next() && resultSet.getBoolean(1);
            }
        });
        return exists;
    }

    public void ensureAlbumRemoteFileMap(Album album, RemoteFile remoteFile) throws SQLException {
        URL albumUrl = album.getUrl();
        SplitUrl remoteFileSplitUrl = SplitUrl.of(remoteFile.getUrl());
        String sql = """
                INSERT
                  INTO map_album_remote_file (album_id, remote_file_id)
                SELECT COALESCE(?1, (
                    SELECT a.album_id
                      FROM album a
                      JOIN ripper r ON r.ripper_id = a.ripper_id
                     WHERE (r.name = ?2 AND r.host = ?3)
                       AND (a.gid = ?4 OR a.url = ?5)
                                    ))
                     , COALESCE(?6, (
                    SELECT rf.remote_file_id
                      FROM remote_file rf
                      JOIN ripper r ON r.ripper_id = rf.ripper_id
                     WHERE (r.name = ?7 AND r.host = ?8)
                       AND (rf.urlid = ?9 OR (rf.url_base = ?10 AND rf.url_path = ?11))
                                    ))
                    ON CONFLICT (album_id, remote_file_id) DO NOTHING
                """;
        db.withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (album.getId() == null) {
                    stmt.setNull(1, Types.INTEGER);
                } else {
                    stmt.setLong(1, album.getId());
                }
                stmt.setString(2, album.getRipperName());
                stmt.setString(3, album.getRipperHost());
                stmt.setString(4, album.getGid());
                stmt.setString(5, albumUrl == null ? null : albumUrl.toString());
                if (remoteFile.getId() == null) {
                    stmt.setNull(6, Types.INTEGER);
                } else {
                    stmt.setLong(6, remoteFile.getId());
                }
                stmt.setString(7, remoteFile.getRipperName());
                stmt.setString(8, remoteFile.getRipperHost());
                stmt.setString(9, remoteFile.getUrlId());
                stmt.setString(10, remoteFileSplitUrl.getBase());
                stmt.setString(11, remoteFileSplitUrl.getPath());
                stmt.executeUpdate();
            }
        });
    }

    private void saveAlbumRemoteTags(Connection conn, long albumId, Album album) throws SQLException {
        Set<String> newTags = album.getTags();
        if (newTags == null || newTags.isEmpty()) {
            deleteAllRemoteTagsForAlbum(conn, albumId);
            return;
        }
        Set<String> oldTags = getRemoteTagsForAlbum(conn, albumId);

        HashSet<String> tagsToAdd = new HashSet<>(newTags);
        tagsToAdd.removeAll(oldTags);
        HashSet<String> tagsToRemove = new HashSet<>(oldTags);
        tagsToRemove.removeAll(newTags);

        if (!tagsToAdd.isEmpty()) {
            insertRemoteTagsForAlbum(conn, albumId, tagsToAdd);
        }
        if (!tagsToRemove.isEmpty()) {
            deleteRemoteTagsForAlbum(conn, albumId, tagsToRemove);
        }
    }

    private void deleteAllRemoteTagsForAlbum(Connection conn, long albumId) throws SQLException {
        String sql = """
            DELETE
              FROM map_album_tag
             WHERE album_id = ?
               AND tag_id IN (
                 SELECT tag_id
                   FROM tag
                  WHERE local = 0
                             )
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, albumId);
            stmt.executeUpdate();
        }
    }

    private void insertRemoteTagsForAlbum(Connection conn, long albumId, Set<String> tags) throws SQLException {
        String insertTags = "INSERT INTO tag (name, local) VALUES (?, 0) ON CONFLICT DO NOTHING";
        try (PreparedStatement stmt = conn.prepareStatement(insertTags)) {
            for (String tag : tags) {
                stmt.setString(1, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        String insertAlbumTags = """
                INSERT INTO map_album_tag (album_id, tag_id)
                SELECT ?, tag_id
                  FROM tag
                 WHERE tag.name = ?
                   AND tag.local = 0
                    ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement stmt = conn.prepareStatement(insertAlbumTags)) {
            for (String tag : tags) {
                stmt.setLong(1, albumId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void deleteRemoteTagsForAlbum(Connection conn, long albumId, Set<String> tags) throws SQLException {
        String sql = """
                DELETE
                  FROM map_album_tag
                 WHERE album_id = ?
                   AND tag_id IN (
                     SELECT tag_id
                       FROM tag
                      WHERE tag.name = ?
                        AND tag.local = 0
                                 )
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String tag : tags) {
                stmt.setLong(1, albumId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}

package com.rarchives.ripme.db.dao;

import com.rarchives.ripme.TestHelpers;
import com.rarchives.ripme.db.model.Album;
import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.ripper.rippers.FlickrRipper;
import com.rarchives.ripme.test.SQLiteTestBase;
import com.rarchives.ripme.test.WithInMemoryDb;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlbumDaoTest extends SQLiteTestBase {
    @Test
    @WithInMemoryDb
    void save_findById() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTitle("Album Title");
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
    }

    @Test
    @WithInMemoryDb
    void save_insert_localRating_null() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        album.setLocalRating(null);
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertNull(album.getLocalRating());
    }

    @Test
    @WithInMemoryDb
    void save_insert_localRating_notNull() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        int localRating = 2;
        album.setLocalRating(localRating);
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertEquals(localRating, album.getLocalRating());
    }

    @Test
    @WithInMemoryDb
    void save_insertedNotNull_lastFetchNull() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertNotNull(album.getInsertedTs());
        assertNull(album.getLastFetchTs());
    }

    @Test
    @WithInMemoryDb
    void save_insert_autoLastFetchWhenFetchCountNonZero() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertNotNull(album.getLastFetchTs());
    }

    @Test
    @WithInMemoryDb
    void save_insert_autoLastFetchIgnoresUserValue() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        Instant userValue = Instant.now().minusSeconds(1);
        album.setLastFetchTs(userValue);
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertTrue(album.getLastFetchTs().isAfter(userValue));
    }

    @Test
    @WithInMemoryDb
    void save_update_sameFetchCount_autoLastFetchIgnoresUserValue() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        albumDao.save(album);
        assertEquals(1, album.getId());

        long simulatedLastFetch = Instant.now().minusSeconds(10).toEpochMilli();
        db.withConnection(conn -> {
            int albumId = 1;
            List<Map<String, Object>> simulatedUpdate = TestHelpers.debugSqlQuery(conn, "UPDATE album SET last_fetch_ts = ? WHERE album_id = ? RETURNING last_fetch_ts", simulatedLastFetch, albumId);
            assertEquals(String.format("[{last_fetch_ts=%d}]", simulatedLastFetch), simulatedUpdate.toString());
        });

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getFetchCount());
        album.setTitle("Album Title");
        Instant newUserValue = Instant.now();
        album.setLastFetchTs(newUserValue);
        albumDao.save(album);

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertEquals("Album Title", album.getTitle());
        assertEquals(simulatedLastFetch, album.getLastFetchTs().toEpochMilli());
    }

    @Test
    @WithInMemoryDb
    void save_update_incFetchCount_autoLastFetchIgnoresUserValue() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setFetchCount(1);
        albumDao.save(album);
        assertEquals(1, album.getId());
        Instant insertedTs = Instant.ofEpochMilli(album.getInsertedTs().toEpochMilli());

        long simulatedLastFetch = Instant.now().minusSeconds(10).toEpochMilli();
        db.withConnection(conn -> {
            int albumId = 1;
            List<Map<String, Object>> simulatedUpdate = TestHelpers.debugSqlQuery(conn, "UPDATE album SET last_fetch_ts = ? WHERE album_id = ? RETURNING last_fetch_ts", simulatedLastFetch, albumId);
            assertEquals(String.format("[{last_fetch_ts=%d}]", simulatedLastFetch), simulatedUpdate.toString());
        });

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getFetchCount());
        album.setTitle("Album Title");
        Instant newUserValue = Instant.now().minusSeconds(2);
        album.setLastFetchTs(newUserValue);
        album.setFetchCount(2);
        albumDao.save(album);

        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getId());
        assertEquals("Album Title", album.getTitle());
        assertTrue(album.getLastFetchTs().toEpochMilli() > simulatedLastFetch);
        assertTrue(album.getLastFetchTs().toEpochMilli() > newUserValue.toEpochMilli());
        assertEquals(insertedTs, album.getInsertedTs());
    }

    @Test
    @WithInMemoryDb
    void findByGid() throws SQLException, IOException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("FlickrRipper");
        album.setRipperHost("flickr");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTitle("Album Title");
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findByGid(FlickrRipper.class, "flickr", "AlbumId1234").get();
        assertEquals(1, album.getId());
    }

    @Test
    @WithInMemoryDb
    void findByRipperUrl() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("FlickrRipper");
        album.setRipperHost("flickr");
        album.setGid("AlbumId1234");
        URL url = URI.create("https://example.com/album/1234").toURL();
        album.setUrl(url);
        album.setTitle("Album Title");
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = albumDao.findByRipperUrl(FlickrRipper.class, "flickr", url).get();
        assertEquals(1, album.getId());
    }

    @Test
    @WithInMemoryDb
    void saveUpsert() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTitle("Album Title");
        // Round off nanoseconds
        Instant createdTs = Instant.ofEpochMilli(Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());
        album.setCreatedTs(createdTs);
        album.setLocalRating(2);
        albumDao.save(album);
        assertEquals(1, album.getId());

        album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTitle("Album Title Updated");
        Instant modifiedTs = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        album.setModifiedTs(modifiedTs);
        album.setLocalRating(3);
        albumDao.save(album);
        assertEquals(1, album.getId());
        album = albumDao.findById(album.getId()).get();
        assertEquals("Album Title Updated", album.getTitle());
        assertEquals(modifiedTs, album.getModifiedTs());
        assertEquals(3, album.getLocalRating());

        // save overwrites all original values, even with new null values
        //assertNull(album.getCreatedTs()); // actually, decided against overwriting original values with new null values
    }

    @Test
    @WithInMemoryDb
    void saveTags() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTags(Set.of("tag1", "tag2", "tag3"));
        albumDao.save(album);
        assertEquals(1, album.getId());
        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), album.getTags());
    }

    @Test
    @WithInMemoryDb
    void updateTags() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTags(Set.of("tag1", "tag3", "tag4", "tag5"));
        albumDao.save(album);
        assertEquals(1, album.getId());
        // Delete tag3, delete tag4, add tag2, add tag6
        album.setTags(Set.of("tag1", "tag2", "tag5", "tag6"));
        albumDao.save(album);
        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag5", "tag6"), album.getTags());
    }

    @Test
    @WithInMemoryDb
    void deleteTags() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        album.setTags(Set.of("tag1", "tag3", "tag4", "tag5"));
        albumDao.save(album);
        assertEquals(1, album.getId());
        album.setTags(Set.of());
        albumDao.save(album);
        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of(), album.getTags());
    }

    @Test
    @WithInMemoryDb
    void saveTags_ignoreLocal() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        albumDao.save(album);
        assertEquals(1, album.getId());
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                           INSERT INTO tag(name, local)
                           VALUES ('usertag1', 1)
                        RETURNING tag_id
                        """);
                int localTagId = rs1.getInt("tag_id");
                assertEquals(1, localTagId);
                stmt.execute("INSERT INTO map_album_tag(album_id, tag_id) VALUES (1, 1)");
            }
        });
        // All Album.tags in RipMe are remote tags, not local tags
        album.setTags(Set.of("tag1", "tag2", "tag3"));
        albumDao.save(album);

        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), album.getTags());
        // local tags are preserved
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                        SELECT t.name
                          FROM tag t
                          JOIN map_album_tag mat ON mat.tag_id = t.tag_id
                         WHERE t.local = 1
                           AND mat.album_id = 1
                        """);
                String localTagName = rs1.getString("name");
                assertEquals("usertag1", localTagName);
            }
        });
    }

    @Test
    @WithInMemoryDb
    void deleteTags_ignoreLocal() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        albumDao.save(album);
        assertEquals(1, album.getId());
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                           INSERT INTO tag(name, local)
                           VALUES ('usertag1', 1)
                        RETURNING tag_id
                        """);
                int localTagId = rs1.getInt("tag_id");
                assertEquals(1, localTagId);
                stmt.execute("INSERT INTO map_album_tag(album_id, tag_id) VALUES (1, 1)");
            }
        });
        // All Album.tags in RipMe are remote tags, not local tags
        album.setTags(Set.of("tag1", "tag2", "tag3"));
        albumDao.save(album);

        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), album.getTags());
        album.setTags(Set.of()); // Delete remote tags
        albumDao.save(album);

        album = albumDao.findById(album.getId()).get();
        assertEquals(Set.of(), album.getTags());

        // local tags are preserved
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                        SELECT t.name
                          FROM tag t
                          JOIN map_album_tag mat ON mat.tag_id = t.tag_id
                         WHERE t.local = 1
                           AND mat.album_id = 1
                        """);
                String localTagName = rs1.getString("name");
                assertEquals("usertag1", localTagName);
            }
        });
    }



    @Test
    @WithInMemoryDb
    void incrementFetchCount() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1234");
        album.setUrl(URI.create("https://example.com/album/1234").toURL());
        albumDao.save(album);
        assertEquals(1, album.getId());
        assertEquals(0, album.getFetchCount());
        albumDao.incrementFetchCount(album);
        assertEquals(1, album.getFetchCount());
        assertNotNull(album.getLastFetchTs());
        Instant lastFetchTs = album.getLastFetchTs();
        album = albumDao.findById(album.getId()).get();
        assertEquals(1, album.getFetchCount());
        assertEquals(lastFetchTs, album.getLastFetchTs());
    }

    @Test
    @WithInMemoryDb
    void ensure_has_AlbumRemoteFileMap_albumRowid_remoteFileRowid() throws SQLException, MalformedURLException {
        // Tests ensureAlbumRemoteFileMap AND hasAlbumRemoteFileMap
        AlbumDao albumDao = new AlbumDao(db);
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1000");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());
        albumDao.save(album);
        long albumId = album.getId();

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("Album1000Img2000");
        remoteFile.setUrl(URI.create("https://example.com/album/1000/2000.jpg").toURL());
        remoteFileDao.save(remoteFile);
        long remoteFileId = remoteFile.getId();

        albumDao.ensureAlbumRemoteFileMap(album, remoteFile);

        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT album_id, remote_file_id FROM map_album_remote_file");
                assertEquals(albumId, rs.getLong(1));
                assertEquals(remoteFileId, rs.getLong(2));
            }
        });

        assertTrue(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
        remoteFile.setId(-1L);
        remoteFile.setUrl(null);
        remoteFile.setUrlId(null);
        assertFalse(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
    }

    @Test
    @WithInMemoryDb
    void ensure_has_AlbumRemoteFileMap_albumId_remoteFileUrlId() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1000");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());
        albumDao.save(album);
        long albumId = album.getId();
        album = new Album(); // Recreate object without id for existing album
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1000");

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("Album1000Img2000");
        remoteFile.setUrl(URI.create("https://example.com/album/1000/2000.jpg").toURL());
        remoteFileDao.save(remoteFile);
        long remoteFileId = remoteFile.getId();
        remoteFile = new RemoteFile(); // Recreate object without id for existing remote file
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("Album1000Img2000");

        albumDao.ensureAlbumRemoteFileMap(album, remoteFile);

        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT album_id, remote_file_id FROM map_album_remote_file");
                assertEquals(albumId, rs.getLong(1));
                assertEquals(remoteFileId, rs.getLong(2));
            }
        });

        assertTrue(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
        remoteFile.setId(null);
        remoteFile.setUrl(URI.create("https://example.com/different").toURL());
        remoteFile.setUrlId("different");
        assertFalse(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
    }

    @Test
    @WithInMemoryDb
    void ensure_has_AlbumRemoteFileMap_albumUrl_remoteFileRowid() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1000");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());
        albumDao.save(album);
        long albumId = album.getId();
        album = new Album(); // Recreate object without id for existing album
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("Album1000Img2000");
        remoteFile.setUrl(URI.create("https://example.com/album/1000/2000.jpg").toURL());
        remoteFileDao.save(remoteFile);
        long remoteFileId = remoteFile.getId();
        //remoteFile = new RemoteFile(); // Recreate object without id for existing remote file
        //remoteFile.setRipper("MyRipper");
        //remoteFile.setUrlId("Album1000Img2000");

        albumDao.ensureAlbumRemoteFileMap(album, remoteFile);

        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT album_id, remote_file_id FROM map_album_remote_file");
                assertEquals(albumId, rs.getLong(1));
                assertEquals(remoteFileId, rs.getLong(2));
            }
        });

        assertTrue(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
        album.setUrl(URI.create("https://example.com/different").toURL());
        assertFalse(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
    }

    @Test
    @WithInMemoryDb
    void ensure_has_AlbumRemoteFileMap_albumUrl_remoteFileUrl() throws SQLException, MalformedURLException {
        AlbumDao albumDao = new AlbumDao(db);
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        Album album = new Album();
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setGid("AlbumId1000");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());
        albumDao.save(album);
        long albumId = album.getId();
        album = new Album(); // Recreate object without id for existing album
        album.setRipperName("MyRipper");
        album.setRipperHost("myripper");
        album.setUrl(URI.create("https://example.com/album/1000").toURL());

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrl(URI.create("https://example.com/album/1000/2000.jpg").toURL());
        remoteFileDao.save(remoteFile);
        long remoteFileId = remoteFile.getId();
        remoteFile = new RemoteFile(); // Recreate object without id for existing remote file
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrl(URI.create("https://example.com/album/1000/2000.jpg").toURL());

        albumDao.ensureAlbumRemoteFileMap(album, remoteFile);

        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT album_id, remote_file_id FROM map_album_remote_file");
                assertEquals(albumId, rs.getLong(1));
                assertEquals(remoteFileId, rs.getLong(2));
            }
        });

        assertTrue(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
        album.setUrl(URI.create("https://example.com/different").toURL());
        assertFalse(albumDao.hasAlbumRemoteFileMap(album, remoteFile));
    }
}

package com.rarchives.ripme.db.dao;

import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.ripper.RipUrlId;
import com.rarchives.ripme.ripper.rippers.FlickrRipper;
import com.rarchives.ripme.test.SQLiteTestBase;
import com.rarchives.ripme.test.WithInMemoryDb;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RemoteFileDaoTest extends SQLiteTestBase {

    @Test
    @WithInMemoryDb
    void save_findById() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertEquals("file.txt", remoteFile.getFilename());
    }

    @Test
    @WithInMemoryDb
    void save_mimeType() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setMimeType("text/plain");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertEquals("text/plain", remoteFile.getMimeType());
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM mime_type");
                assertEquals("text/plain", rs.getString("name"));
            }
        });
    }

    @Test
    @WithInMemoryDb
    void save_insert_bytes_localRating_null() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setBytes(null);
        remoteFile.setLocalRating(null);
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertNull(remoteFile.getBytes());
        assertNull(remoteFile.getLocalRating());
    }

    @Test
    @WithInMemoryDb
    void save_insert_bytes_localRating_notNull() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setBytes(1024L);
        remoteFile.setLocalRating(2);
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertEquals(1024L, remoteFile.getBytes());
        assertEquals(2, remoteFile.getLocalRating());
    }

    @Test
    @WithInMemoryDb
    void save_insertSetsInsertedTs() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("MyUrlId");
        Instant oldTime = Instant.now().minusSeconds(1);
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertTrue(remoteFile.getInsertedTs().isAfter(oldTime));
    }

    @Test
    @WithInMemoryDb
    void save_insertIgnoresUserSpecifiedInsertedTs() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("MyUrlId");
        Instant oldTime = Instant.now().minusSeconds(1);
        remoteFile.setInsertedTs(oldTime);
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertTrue(remoteFile.getInsertedTs().isAfter(oldTime));
    }

    @Test
    @WithInMemoryDb
    void save_updateIgnoresUserSpecifiedInsertedTs() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        Instant originalTime = remoteFile.getInsertedTs();
        Instant newTime = remoteFile.getInsertedTs().plusSeconds(1);
        remoteFile.setInsertedTs(newTime);
        remoteFileDao.save(remoteFile);

        assertEquals(1, remoteFile.getId());
        assertEquals(originalTime, remoteFile.getInsertedTs());
    }

    @Test
    @WithInMemoryDb
    void findByRipUrlId_Id() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file.txt");
        remoteFile.setTitle("My File");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findByRipUrlId(new RipUrlId(FlickrRipper.class, "flickr", "MyUrlId")).get();
        assertEquals(1, remoteFile.getId());
        assertEquals("My File", remoteFile.getTitle());
    }

    @Test
    @WithInMemoryDb
    void findByRipUrlId_Url() throws SQLException, MalformedURLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrl(URI.create("https://live.staticflickr.com/4281/35181899252_c16950d7d5_h.jpg").toURL());
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findByRipUrlId(new RipUrlId(FlickrRipper.class, "flickr", URI.create("https://live.staticflickr.com/4281/35181899252_c16950d7d5_h.jpg").toURL())).get();
        assertEquals(1, remoteFile.getId());
    }

    @Test
    @WithInMemoryDb
    void saveUpsertUniqueUrlId() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setUploader("MyUploader");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file_newname.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals("file_newname.txt", remoteFile.getFilename());

        // save overwrites all original values, even with new null values
        //assertNull(remoteFile.getUploader()); // actually, decided against overwriting original values with new null values

        remoteFile = remoteFileDao.findByRipUrlId(new RipUrlId(FlickrRipper.class, "flickr", "MyUrlId")).get();
        assertEquals("file_newname.txt", remoteFile.getFilename());
    }

    @Test
    @WithInMemoryDb
    void saveUpsertUrl() throws SQLException, MalformedURLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);

        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrl(URI.create("https://live.staticflickr.com/4281/35181899252_c16950d7d5_h.jpg").toURL());
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertEquals("file.txt", remoteFile.getFilename());

        remoteFile = new RemoteFile();
        remoteFile.setRipperName("FlickrRipper");
        remoteFile.setRipperHost("flickr");
        remoteFile.setFilename("file_new.txt");
        remoteFile.setUrl(URI.create("https://live.staticflickr.com/4281/35181899252_c16950d7d5_h.jpg").toURL());
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(1, remoteFile.getId());
        assertEquals("file_new.txt", remoteFile.getFilename());

        remoteFile = remoteFileDao.findByRipUrlId(new RipUrlId(FlickrRipper.class, "flickr", remoteFile.getUrl())).get();
        assertEquals("file_new.txt", remoteFile.getFilename());
    }

    @Test
    @WithInMemoryDb
    void saveTags() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setTags(Set.of("tag1", "tag2", "tag3"));
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), remoteFile.getTags());
    }

    @Test
    @WithInMemoryDb
    void updateTags() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setTags(Set.of("tag1", "tag2", "tag3"));
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        // Delete tag3, delete tag4, add tag2, add tag6
        remoteFile.setTags(Set.of("tag1", "tag2", "tag5", "tag6"));
        remoteFileDao.save(remoteFile);
        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag5", "tag6"), remoteFile.getTags());
    }

    @Test
    @WithInMemoryDb
    void deleteTags() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFile.setTags(Set.of("tag1", "tag2", "tag3"));
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        remoteFile.setTags(Set.of());
        remoteFileDao.save(remoteFile);
        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of(), remoteFile.getTags());
    }

    @Test
    @WithInMemoryDb
    void saveTags_ignoreLocal() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                           INSERT INTO tag(name, local)
                           VALUES ('usertag1', 1)
                        RETURNING tag_id
                        """);
                int localTagId = rs1.getInt("tag_id");
                assertEquals(1, localTagId);
                stmt.execute("INSERT INTO map_remote_file_tag(remote_file_id, tag_id) VALUES (1, 1)");
            }
        });
        // All RemoteFile.tags in RipMe are remote tags, not local tags
        remoteFile.setTags(Set.of("tag1", "tag2", "tag3"));
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), remoteFile.getTags());
        // local tags are preserved
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                        SELECT t.name
                          FROM tag t
                          JOIN map_remote_file_tag mrft ON mrft.tag_id = t.tag_id
                         WHERE t.local = 1
                           AND mrft.remote_file_id = 1
                        """);
                String localTagName = rs1.getString("name");
                assertEquals("usertag1", localTagName);
            }
        });
    }

    @Test
    @WithInMemoryDb
    void deleteTags_ignoreLocal() throws SQLException {
        RemoteFileDao remoteFileDao = new RemoteFileDao(db);
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperName("MyRipper");
        remoteFile.setRipperHost("myripper");
        remoteFile.setFilename("file.txt");
        remoteFile.setUrlId("MyUrlId");
        remoteFileDao.save(remoteFile);
        assertEquals(1, remoteFile.getId());
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                           INSERT INTO tag(name, local)
                           VALUES ('usertag1', 1)
                        RETURNING tag_id
                        """);
                int localTagId = rs1.getInt("tag_id");
                assertEquals(1, localTagId);
                stmt.execute("INSERT INTO map_remote_file_tag(remote_file_id, tag_id) VALUES (1, 1)");
            }
        });
        // All RemoteFile.tags in RipMe are remote tags, not local tags
        remoteFile.setTags(Set.of("tag1", "tag2", "tag3"));
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of("tag1", "tag2", "tag3"), remoteFile.getTags());
        remoteFile.setTags(Set.of()); // Delete remote tags
        remoteFileDao.save(remoteFile);

        remoteFile = remoteFileDao.findById(remoteFile.getId()).get();
        assertEquals(Set.of(), remoteFile.getTags());

        // local tags are preserved
        db.withConnection(conn -> {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("""
                        SELECT t.name
                          FROM tag t
                          JOIN map_remote_file_tag mrft ON mrft.tag_id = t.tag_id
                         WHERE t.local = 1
                           AND mrft.remote_file_id = 1
                        """);
                String localTagName = rs1.getString("name");
                assertEquals("usertag1", localTagName);
            }
        });
    }

}

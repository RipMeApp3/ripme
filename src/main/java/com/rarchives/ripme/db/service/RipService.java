package com.rarchives.ripme.db.service;

import com.rarchives.ripme.db.DatabaseManager;
import com.rarchives.ripme.db.dao.AlbumDao;
import com.rarchives.ripme.db.dao.RemoteFileDao;
import com.rarchives.ripme.db.model.Album;
import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.ripper.AbstractRipper;
import com.rarchives.ripme.ripper.RipUrlId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;

public class RipService {
    private static final Logger logger = LogManager.getLogger(RipService.class);

    private final AlbumDao albumDao;
    private final RemoteFileDao remoteFileDao;

    public RipService(DatabaseManager db) {
        this.albumDao = new AlbumDao(db);
        this.remoteFileDao = new RemoteFileDao(db);
    }

    public Album getAlbum(Class<? extends AbstractRipper> ripperClass, String host, String gid, URL url) throws SQLException {
        try {
            Optional<Album> storedAlbum = albumDao.find(ripperClass, host, gid, url);
            if (storedAlbum.isPresent()) {
                return storedAlbum.get();
            }
            Album newAlbum = new Album();
            newAlbum.setRipperClass(ripperClass);
            newAlbum.setRipperHost(host);
            newAlbum.setGid(gid);
            newAlbum.setUrl(url);
            saveAlbum(newAlbum);
            return newAlbum;
        } catch (SQLException e) {
            logger.error("Not able to get Album for {} {} {}", ripperClass, gid, url);
            throw e;
        }
    }

    public void saveAlbum(Album album) {
        try {
            if (album == null) {
                logger.info("Attempted to save null Album");
                return;
            }
            albumDao.save(album);
        } catch (SQLException e) {
            logger.error("Not able to save Album {}", album);
            // Bad, but not rethrowing
        }
    }

    public RemoteFile getRemoteFile(RipUrlId ripUrlId) throws SQLException {
        try {
            Optional<RemoteFile> storedRemoteFile = remoteFileDao.findByRipUrlId(ripUrlId);
            if (storedRemoteFile.isPresent()) {
                return storedRemoteFile.get();
            }
            RemoteFile newRemoteFile = new RemoteFile();
            newRemoteFile.setRipUrlId(ripUrlId);
            saveRemoteFile(newRemoteFile);
            return newRemoteFile;
        } catch (SQLException e) {
            logger.error("Not able to get RemoteFile for {}", ripUrlId);
            throw e;
        }
    }

    public void saveRemoteFile(RemoteFile remoteFile) {
        if (remoteFile == null) {
            logger.info("Attempted to save null RemoteFile");
            return;
        }
        try {
            remoteFileDao.save(remoteFile);
        } catch (SQLException e) {
            logger.error("Not able to save RemoteFile {}", remoteFile, e);
            // Bad, but not rethrowing
        }
    }

    public boolean isRemoteFileInAlbum(Album album, RemoteFile remoteFile) {
        if (album == null || remoteFile == null) {
            logger.info("Attempted to check mapping of null Album or RemoteFile: {} {}", album, remoteFile);
            return false;
        }
        try {
            return albumDao.hasAlbumRemoteFileMap(album, remoteFile);
        } catch (SQLException e) {
            logger.error("Failed to check mapping of Album and RemoteFile", e);
            // Bad, but not rethrowing
            return false;
        }
    }

    public void putRemoteFileInAlbum(Album album, RemoteFile remoteFile) {
        if (album == null || remoteFile == null) {
            logger.info("Attempted to save null Album or RemoteFile: {} {}", album, remoteFile);
            return;
        }
        try {
            albumDao.ensureAlbumRemoteFileMap(album, remoteFile);
        } catch (SQLException e) {
            logger.error("Failed to put RemoteFile {} in Album {}", remoteFile, album, e);
            // Bad, but not rethrowing
        }
    }

    public void incrementFetchCount(Album album) {
        if (album == null) {
            logger.info("Attempted to increment fetch count on null Album");
            return;
        }
        try {
            albumDao.incrementFetchCount(album);
        } catch (SQLException e) {
            logger.warn("Unable to increment album fetch count: {}", album);
            // Not rethrowing because not a huge deal.
        }
    }
}

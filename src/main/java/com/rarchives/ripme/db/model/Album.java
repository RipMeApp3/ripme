package com.rarchives.ripme.db.model;

import com.rarchives.ripme.ripper.AbstractRipper;
import lombok.Data;

import java.net.URL;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Describes a ripped album or gallery. AbstractRipper handles getting and saving the album, but feel free to save eagerly.
 */
@Data
public class Album {
    private Long id; // sqlite id // Automatically set by AlbumDao
    private String ripperName; // Automatically set by AbstractRipper via RipService#getAlbum via setRipperClass
    private Class<? extends AbstractRipper> ripperClass; // local cache // Automatically set by AbstractRipper via RipService#getAlbum via setRipperClass
    private String ripperHost; // AbstractRipper#getHost() // Automatically set by AbstractRipper via RipService#getAlbum
    private String gid; // Automatically set by AbstractRipper via RipService#getAlbum
    private URL url; // Automatically set by AbstractRipper via RipService#getAlbum
    private String uploader; // To be set by ripper
    private String title; // To be set by ripper
    private String description; // To be set by ripper
    private Instant createdTs; // unix millis // To be set by ripper
    private Instant modifiedTs; // To be set by ripper
    private int fetchCount; // Automatically set by AbstractRipper#notifyComplete via RipService#incrementFetchCount
    private boolean hidden; // To be set by ripper
    private boolean removed; // To be set by ripper
    private Instant insertedTs; // Automatically set by AlbumDao
    private Instant lastFetchTs; // Automatically set by AlbumDao
    private Set<String> tags; // To be set by ripper

    public Set<String> getTags() {
        if (tags == null) {
            tags = new HashSet<>();
        }
        return tags;
    }

    public void setRipperClass(Class<? extends AbstractRipper> ripperClass) {
        this.ripperClass = ripperClass;
        this.ripperName = ripperClass.getSimpleName();
    }
}

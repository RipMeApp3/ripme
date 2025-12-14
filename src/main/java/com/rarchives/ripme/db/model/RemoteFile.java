package com.rarchives.ripme.db.model;

import com.rarchives.ripme.ripper.AbstractRipper;
import com.rarchives.ripme.ripper.RipUrlId;
import com.rarchives.ripme.utils.Utils;
import lombok.Data;

import java.net.URL;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Describes a fetched file. AbstractRipper automatically saves a stub RemoteFile, but rippers should get and save updates via RipService.
 */
@Data
public class RemoteFile {
    /** Automatically set by RemoteFileDao */
    private Long id; // sqlite id // Automatically set by RemoteFileDao
    private String ripperName; // Automatically set by RipService#getRemoteFile via setRipUrlId
    private Class<? extends AbstractRipper> ripperClass; // local cache  // Automatically set by RipService#getRemoteFile via setRipUrlId
    private String ripperHost; // Automatically set by RipService#getRemoteFile via setRipUrlId
    private String urlId; // The "url id" part of RipUrlId // Automatically set by RipService#getRemoteFile via setRipUrlId
    private URL url; // The url of the file // Automatically set by RipService#getRemoteFile via setRipUrlId
    private String filename; // Automatically set by DownloadFileThread
    private String mimeType; // Automatically set by DownloadFileThread
    private Integer widthPx; // To be set by ripper
    private Integer heightPx; // To be set by ripper
    private Integer durationMs; // To be set by ripper
    private String title; // To be set by ripper
    private String description; // To be set by ripper
    private Instant uploadedTs; // unix millis // To be set by ripper
    private String uploader; // To be set by ripper
    private String aux; // To be set by ripper; bag for unstructured data
    private boolean hidden; // To be set by ripper
    private boolean removed; // To be set by ripper
    private boolean fetched; // Automatically set by DownloadFileThread
    private boolean ignored; // To be set manually by user, for now
    private Instant insertedTs; // Automatically set by RemoteFileDao
    private Long bytes; // Automatically set by DownloadFileThread
    private Integer localRating; // Not used by RipMe; for arbitrary local use by other applications
    private Set<String> tags; // To be set by ripper

    private static final Pattern MIME_TYPE_REGEX = Pattern.compile("[a-z]+/[a-z0-9_.+-]*");

    public Set<String> getTags() {
        if (tags == null) {
            tags = new HashSet<>();
        }
        return tags;
    }

    public boolean addTag(String tag) {
        return getTags().add(tag);
    }

    public void setRipperClass(Class<? extends AbstractRipper> ripperClass) {
        this.ripperClass = ripperClass;
        this.ripperName = ripperClass.getSimpleName();
    }

    public void setRipUrlId(RipUrlId ripUrlId) {
        setRipperClass(ripUrlId.getRipper());
        this.ripperHost = ripUrlId.getRipperHost();
        this.urlId = ripUrlId.getRipUrlId();
        this.url = ripUrlId.getUrl();
    }

    public void setMimeType(String mimeType) {
        if (mimeType != null && !MIME_TYPE_REGEX.matcher(mimeType).matches()) {
            throw new IllegalArgumentException("Invalid mime type; must be all lowercase, in type/subtype form, with no parameters: " + mimeType);
        }
        this.mimeType = mimeType;
    }

    @SuppressWarnings("unchecked")
    private Optional<RipUrlId> getRipUrlId() {
        if (ripperHost == null) {
            return Optional.empty();
        }
        if (ripperClass == null) {
            for (Class<?> clazz : Utils.getClassesForPackage("com.rarchives.ripme.ripper.rippers")) {
                if (AbstractRipper.class.isAssignableFrom(clazz) && clazz.getSimpleName().equals(ripperName)) {
                    ripperClass = (Class<? extends AbstractRipper>) clazz;
                    break;
                }
            }
        }
        if (ripperClass == null) {
            for (Class<?> clazz : Utils.getClassesForPackage("com.rarchives.ripme.ripper.rippers.video")) {
                if (AbstractRipper.class.isAssignableFrom(clazz) && clazz.getSimpleName().equals(ripperName)) {
                    ripperClass = (Class<? extends AbstractRipper>) clazz;
                    break;
                }
            }
        }
        if (ripperClass != null) {
            if (urlId != null && !urlId.isEmpty()) {
                return Optional.of(new RipUrlId(ripperClass, ripperHost, urlId));
            }
            return Optional.of(new RipUrlId(ripperClass, ripperHost, url));
        }
        return Optional.empty();
    }
}

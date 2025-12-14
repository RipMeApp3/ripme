package com.rarchives.ripme.ripper;

import java.io.*;
import java.net.*;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jsoup.HttpStatusException;

import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.db.model.SplitUrl;
import com.rarchives.ripme.ui.RipStatusMessage.STATUS;
import com.rarchives.ripme.utils.RetryUtil;
import com.rarchives.ripme.utils.Utils;

/**
 * Thread for downloading files. Includes retry logic, observer notifications,
 * and other goodies.
 */
class DownloadFileThread implements Runnable {
    private static final Logger logger = LogManager.getLogger(DownloadFileThread.class);

    private static final MimeTypes MIME_REPO = TikaConfig.getDefaultConfig().getMimeRepository();

    private final TokenedUrlGetter tokenedUrlGetter; // Some URLs may be valid for a limited time. This should get a fresh url
    private final RipUrlId ripUrlId;

    // Important: get a fresh RemoteFile after each tokenedUrlGetter.getTokenedUrl()
    // to prevent overwriting data that might be populated from TokenedUrlGetter
    private RemoteFile remoteFile;

    private String referrer = "";
    private Map<String, String> cookies = new HashMap<>();

    private final Path directory;
    private String filename;
    private final AbstractRipper observer;
    private final int retries;
    private final Boolean getFileExtFromMIME;

    private final int TIMEOUT;

    private final int retrySleep;
    private final double retryDelayMultiplier = 1.8;

    private static final Semaphore staggeredStart = new Semaphore(1, true);
    private final int staggeredStartMilliDelay = 200;

    public DownloadFileThread(TokenedUrlGetter tug, RipUrlId ripUrlId, Path directory, String filename, AbstractRipper observer, Boolean getFileExtFromMIME) {
        super();
        this.tokenedUrlGetter = tug;
        this.ripUrlId = ripUrlId;
        this.directory = directory;
        this.filename = filename;
        this.observer = observer;
        this.retries = Utils.getConfigInteger("download.retries", 1);
        this.TIMEOUT = Utils.getConfigInteger("download.timeout", 60000);
        this.retrySleep = Utils.getConfigInteger("download.retry.sleep", 0);
        this.getFileExtFromMIME = getFileExtFromMIME;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public void setCookies(Map<String, String> cookies) {
        this.cookies = cookies;
    }

    /**
     * Attempts to download the file. Retries as needed. Notifies observers upon
     * completion/error/warn.
     */
    @Override
    public void run() {
        try {
            runReal();
            if (remoteFile != null) {
                observer.getRipService().saveRemoteFile(remoteFile);
            }
        } catch (Exception e) {
            if (remoteFile != null) {
                observer.getRipService().saveRemoteFile(remoteFile);
            }
            throw e;
        }
    }

    public void runReal() {
        if (observer.isStopped()) {
            // TODO add handler for graceful stop
            observer.downloadErrored(ripUrlId, Utils.getLocalizedString("download.interrupted"));
            return;
        }

        try {
            staggeredStart.acquire();
            Thread.sleep(staggeredStartMilliDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            staggeredStart.release();
        }

        URL url = null;
        try {
            int maxAttempts = 5;
            Predicate<Exception> shouldAttemptRetry = e -> {
                return observer.isStopped() || e instanceof ConnectException && "Connection refused".equals(e.getMessage());
            };
            url = RetryUtil.executeWithRetry(tokenedUrlGetter::getTokenedUrl, maxAttempts, Duration.ofSeconds(10), 1.2, shouldAttemptRetry);
            URL urlNoQuery = SplitUrl.of(url).noQueryFragment();
            try {
                remoteFile = observer.getRipService().getRemoteFile(ripUrlId);
                remoteFile.setUrl(urlNoQuery);
            } catch (SQLException ignored) {
                remoteFile = null;
                // Bad, but continue to download
            }
        } catch (HttpStatusException e) {
            observer.downloadErrored(ripUrlId, Utils.getLocalizedString("failed.to.get.url.for.0", ripUrlId));
            logger.error("[!] Failed to get URL for " + ripUrlId);
            return; // do not retry
        } catch (Exception e) {
            logger.error("[!] Failed to get URL for " + ripUrlId, e);
            observer.downloadErrored(ripUrlId, Utils.getLocalizedString("failed.to.get.url.for.0", ripUrlId));
            return; // do not retry
        }
        if (filename == null) {
            // Strip token query parameters
            filename = Path.of(url.getPath()).getFileName().toString();
        }
        // First thing we make sure the file name doesn't have any illegal chars in it
        filename = Utils.sanitizeSaveAs(filename);
        if (remoteFile != null) {
            remoteFile.setFilename(filename);
        }

        if (AbstractRipper.shouldIgnoreExtension(url)) {
            observer.sendUpdate(STATUS.DOWNLOAD_SKIP, Utils.getLocalizedString("skipping.ignored.extension") + ": " + url.toExternalForm());
            return;
        }

        if (!Files.exists(directory)) {
            logger.info("[+] Creating directory: " + directory);
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                logger.error("Error creating directory", e);
                observer.downloadErrored(ripUrlId, Utils.getLocalizedString("error.creating.directory") + ": " + directory + " ; " +  e.getMessage());
                return;
            }
        }

        File saveAs = directory.resolve(filename).toFile();
        File saveAsPart = directory.resolve(filename + ".part").toFile();
        String prettySaveAs = Utils.removeCWD(saveAs.toPath());

        long fileSize = 0;
        long bytesTotal = 0;
        long bytesDownloaded = 0;

        boolean overwriteFile = Utils.getConfigBoolean("file.overwrite", false);
        if (saveAs.exists()) {
            if (overwriteFile) {
                logger.info("[!] " + Utils.getLocalizedString("deleting.existing.file") + " " + prettySaveAs);
                if (!saveAs.delete()) logger.error("could not delete existing file: " + saveAs.getAbsolutePath());
            } else {
                logger.info("[!] " + Utils.getLocalizedString("skipping") + " " + url + " -- "
                        + Utils.getLocalizedString("file.already.exists") + ": " + prettySaveAs);
                observer.downloadExists(ripUrlId, saveAs.toPath());
                return;
            }
        }

        boolean redirected = false;
        int tries = 0; // Number of attempts to download
        do {
            tries += 1;
            try {
                logger.info("    Downloading file: " + url + (tries > 0 ? " Try #" + tries : ""));

                String urlNoQuery = SplitUrl.of(url).noQueryFragment().toExternalForm();
                observer.sendUpdate(STATUS.DOWNLOAD_STARTED, urlNoQuery);

                if (saveAsPart.exists()) {
                    if (observer.tryResumeDownload()) {
                        fileSize = saveAsPart.length();
                    } else {
                        if (!saveAsPart.delete()) logger.error("could not delete existing file: " + saveAsPart.getAbsolutePath());
                    }
                }

                // Setup HTTP request
                HttpURLConnection huc;
                if (url.getProtocol().equals("https")) {
                    huc = (HttpsURLConnection) url.openConnection();
                } else {
                    huc = (HttpURLConnection) url.openConnection();
                }
                huc.setInstanceFollowRedirects(true);
                // It is important to set both ConnectTimeout and ReadTimeout. If you don't then
                // ripme will wait forever
                // for the server to send data after connecting.
                huc.setConnectTimeout(TIMEOUT);
                huc.setReadTimeout(TIMEOUT);
                huc.setRequestProperty("accept", "*/*");
                if (!referrer.equals("")) {
                    huc.setRequestProperty("Referer", referrer); // Sic
                }
                huc.setRequestProperty("User-agent", AbstractRipper.USER_AGENT);
                StringBuilder cookie = new StringBuilder();
                for (String key : cookies.keySet()) {
                    if (!cookie.toString().equals("")) {
                        cookie.append("; ");
                    }
                    cookie.append(key).append("=").append(cookies.get(key));
                }
                huc.setRequestProperty("Cookie", cookie.toString());
                if (observer.tryResumeDownload()) {
                    if (fileSize != 0) {
                        huc.setRequestProperty("Range", "bytes=" + fileSize + "-");
                    }
                }
                logger.debug(Utils.getLocalizedString("request.properties") + ": " + huc.getRequestProperties());
                huc.connect();

                int statusCode = huc.getResponseCode();
                logger.debug("Status code: " + statusCode);
                // If the server doesn't allow resuming downloads error out
                if (statusCode != 206 && observer.tryResumeDownload() && saveAsPart.exists()) {
                    // TODO find a better way to handle servers that don't support resuming
                    // downloads then just erroring out
                    observer.downloadErrored(ripUrlId,
                            Utils.getLocalizedString("server.doesnt.support.resuming.downloads") + ": "
                                    + Utils.getLocalizedString("0.while.downloading.1", statusCode, url.toExternalForm()));
                    //throw new IOException(Utils.getLocalizedString("server.doesnt.support.resuming.downloads"));
                    return;
                }
                if (statusCode / 100 == 3) { // 3xx Redirect
                    // FIXME Should not happen because of above line: huc.setInstanceFollowRedirects(true); ???
                    if (!redirected) {
                        // Don't increment retries on the first redirect
                        tries--;
                        redirected = true;
                    }
                    String location = huc.getHeaderField("Location");
                    url = new URI(location).toURL(); // TODO fix redirect with TokenedUrlGetter
                    logger.debug("Redirect status code {} - redirect to {}", statusCode, location);
                    continue; // retry
                }
                if (statusCode / 100 == 4) { // 4xx errors
                    logger.error("[!] " + Utils.getLocalizedString("nonretriable.status.code") + " " + statusCode
                            + " while downloading from " + url);
                    observer.downloadErrored(ripUrlId, Utils.getLocalizedString("nonretriable.status.code") + " "
                            + Utils.getLocalizedString("0.while.downloading.1", statusCode, url.toExternalForm()));
                    return; // Not retriable, drop out.
                }
                if (statusCode / 100 == 5) { // 5xx errors
                    observer.downloadErrored(ripUrlId, Utils.getLocalizedString("retriable.status.code") + " "
                            + Utils.getLocalizedString("0.while.downloading.1", statusCode, url.toExternalForm()));
                    // Throw exception so download can be retried
                    throw new IOException(Utils.getLocalizedString("retriable.status.code") + " " + statusCode);
                }
                if (huc.getContentLength() == 503 && url.getHost().endsWith("imgur.com")) {
                    // Imgur image with 503 bytes is "404"
                    logger.error("[!] Imgur image is 404 (503 bytes long): " + url);
                    observer.downloadErrored(ripUrlId, "Imgur image is 404: " + url.toExternalForm());
                    return;
                }

                // If the ripper is using the bytes progress bar set bytesTotal to
                // huc.getContentLength()
                if (observer.useByteProgessBar()) {
                    bytesTotal = huc.getContentLengthLong();
                    observer.setBytesTotal(bytesTotal);
                    observer.sendUpdate(STATUS.TOTAL_BYTES, bytesTotal);
                    logger.debug("Size of file at " + url + " = " + bytesTotal + "b");
                }

                // Save file
                InputStream bis = TikaInputStream.get(huc.getInputStream());

                // Detect mime type (new code supporting more types, including video)
                Tika tika = new Tika();
                Metadata metadata = new Metadata();
                // Doesn't seem to work:
                //metadata.set(TikaCoreProperties.CONTENT_TYPE_HINT, huc.getHeaderField("Content-Type"));
                // Works, null-safe:
                metadata.set(HttpHeaders.CONTENT_TYPE, huc.getHeaderField(HttpHeaders.CONTENT_TYPE));
                String detectedMimeType = tika.detect(bis, metadata);
                bis.reset();

                MediaType parsedMimeType = MediaType.parse(detectedMimeType); // May include parameters; null if not parsed
                MediaType baseMimeType = null;
                if (parsedMimeType != null) {
                    baseMimeType = parsedMimeType.getBaseType(); // No parameters
                    if (remoteFile != null) {
                        String lowerCaseMimeType = baseMimeType.toString();
                        remoteFile.setMimeType(lowerCaseMimeType.toLowerCase());
                    } else {
                        logger.warn("remoteFile is null, not able to set mime type for {}", ripUrlId);
                    }
                } else {
                    logger.warn("Unable to detect mime type for {}", ripUrlId);
                }

                // Check if we should get the file ext from the MIME type
                if (getFileExtFromMIME) {
                    if (baseMimeType != null) {
                        try {
                            String extension = MIME_REPO.forName(baseMimeType.toString()).getExtension();
                            saveAs = new File(saveAs + extension);
                        } catch (MimeTypeException e) {
                            logger.error(Utils.getLocalizedString("was.unable.to.detect.content.type"));
                        }
                    } else {
                        logger.error(Utils.getLocalizedString("was.unable.to.detect.content.type"));
                    }
                }

                if (remoteFile != null) {
                    remoteFile.setFilename(saveAs.getName());
                }
                // If we're resuming a download we append data to the existing file
                OutputStream fos = null;
                if (statusCode == 206) {
                    fos = new FileOutputStream(saveAsPart, true);
                } else {
                    try {
                        fos = new FileOutputStream(saveAsPart);
                    } catch (FileNotFoundException e) {
                        // We do this because some filesystems have a max name length
                        if (e.getMessage().contains("File name too long")) {
                            logger.error("The filename " + saveAsPart.getName()
                                    + " is to long to be saved on this file system.");
                            logger.info("Shortening filename");
                            String[] saveAsSplit = saveAsPart.getName().split("\\.");
                            // Get the file extension so when we shorten the file name we don't cut off the
                            // file extension
                            String fileExt = saveAsSplit[saveAsSplit.length - 1];
                            // The max limit for filenames on Linux with Ext3/4 is 255 bytes
                            logger.info(saveAsPart.getName().substring(0, 254 - fileExt.length()) + fileExt);
                            String filename = saveAsPart.getName().substring(0, 254 - fileExt.length()) + "." + fileExt;
                            // We can't just use the new file name as the saveAs because the file name
                            // doesn't include the
                            // users save path, so we get the user save path from the old saveAs
                            saveAsPart = new File(saveAsPart.getParentFile().getAbsolutePath() + File.separator + filename);
                            fos = new FileOutputStream(saveAsPart);
                        } else if (saveAsPart.getAbsolutePath().length() > 259 && Utils.isWindows()) {
                            // This if is for when the file path has gone above 260 chars which windows does
                            // not allow
                            fos = Files.newOutputStream(
                                    Utils.shortenSaveAsWindows(saveAsPart.getParentFile().getPath(), saveAsPart.getName()));
                            assert fos != null: "After shortenSaveAsWindows: " + saveAsPart.getAbsolutePath();
                        }
                        assert fos != null: e.getStackTrace();
                    }
                }
                byte[] data = new byte[1024 * 256];
                int bytesRead;
                boolean shouldSkipFileDownload = huc.getContentLength() / 1000000 >= 10 && AbstractRipper.isThisATest();
                // If this is a test rip we skip large downloads
                if (shouldSkipFileDownload) {
                    logger.debug("Not downloading whole file because it is over 10mb and this is a test");
                } else {
                    long lastProgressUpdate = 0;
                    long bytesSinceLastProgressUpdate = 0;
                    while ((bytesRead = bis.read(data)) != -1) {
                        if (observer.isPanicked()) {
                            observer.downloadErrored(ripUrlId, Utils.getLocalizedString("download.interrupted"));
                            return;
                        }
                        fos.write(data, 0, bytesRead);
                        bytesSinceLastProgressUpdate += bytesRead;
                        long now = System.currentTimeMillis();
                        if (now > lastProgressUpdate + 200) {
                            lastProgressUpdate = now;
                            observer.sendUpdate(STATUS.CHUNK_BYTES, bytesSinceLastProgressUpdate);
                            bytesSinceLastProgressUpdate = 0;
                            if (observer.useByteProgessBar()) {
                                bytesDownloaded += bytesRead;
                                observer.setBytesCompleted(bytesDownloaded);
                                observer.sendUpdate(STATUS.COMPLETED_BYTES, bytesDownloaded);
                            }
                        }
                    }
                }
                bis.close();
                fos.close();
                break; // Download successful: break out of infinite loop
            } catch (SocketTimeoutException timeoutEx) {
                // Handle the timeout
                logger.error("[!] " + url.toExternalForm() + " timedout!");
                // Download failed, break out of loop
                break;
            } catch (HttpStatusException hse) {
                logger.debug(Utils.getLocalizedString("http.status.exception"), hse);
                logger.error("[!] HTTP status " + hse.getStatusCode() + " while downloading from " + hse.getUrl());
                if (hse.getStatusCode() == 404 && Utils.getConfigBoolean("errors.skip404", false)) {
                    observer.downloadErrored(ripUrlId,
                            Utils.getLocalizedString("0.while.downloading.1", "HTTP " + hse.getStatusCode(), url.toExternalForm()));
                    return;
                }
            } catch (SocketException e) {
                boolean resumable = "Connection reset".equals(e.getMessage());
                if (!resumable) {
                    logger.debug("SocketException", e);
                    logger.error("[!] " + Utils.getLocalizedString("exception.while.downloading.file") + ": " + url + " - "
                            + e.getMessage());
                    observer.downloadErrored(ripUrlId, e.getMessage());
                    return;
                }
                // fall through to retry
            } catch (IOException e) {
                if (guessIsENOSPC(e, saveAsPart)) {
                    logger.debug("IOException", e);
                    observer.downloadErrored(ripUrlId, Utils.getLocalizedString("no.space.left.on.device")); // TODO cancel all rips
                    return;
                }
                boolean resumable = "Premature EOF".equals(e.getMessage());
                if (!resumable) {
                    logger.debug("IOException", e);
                    logger.error("[!] " + Utils.getLocalizedString("exception.while.downloading.file") + ": " + url + " - "
                            + e.getMessage());
                    observer.downloadErrored(ripUrlId, e.getMessage());
                    return;
                }
                // fall through to retry
            } catch (URISyntaxException e) {
                logger.debug("IOException", e);
                logger.error("[!] " + Utils.getLocalizedString("exception.while.downloading.file") + ": " + url + " - "
                        + e.getMessage());
                observer.downloadErrored(ripUrlId, Utils.getLocalizedString("exception.while.downloading.file"));
                return;
            } catch (NullPointerException npe){

                logger.error("[!] " + Utils.getLocalizedString("failed.to.download") + " for URL " + url);
                observer.downloadErrored(ripUrlId,
                        Utils.getLocalizedString("failed.to.download") + " " + url.toExternalForm());
                return;

            }
            if (tries > this.retries) {
                logger.error("[!] " + Utils.getLocalizedString("exceeded.maximum.retries") + " (" + this.retries
                        + ") for URL " + url);
                observer.downloadErrored(ripUrlId,
                        Utils.getLocalizedString("failed.to.download") + " " + url.toExternalForm());
                return;
            } else {
                long delay = (long) (retrySleep * Math.pow(retryDelayMultiplier, tries));
                if (delay > 0) {
                    logger.info("Retry: backing off for {}", Duration.ofMillis(delay));
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            }

            // get fresh URL for the next attempt
            try {
                url = tokenedUrlGetter.getTokenedUrl();
                try {
                    if (remoteFile == null) {
                        remoteFile = observer.ripService.getRemoteFile(ripUrlId);
                    }
                    if (remoteFile != null) {
                        remoteFile.setUrl(url);
                        remoteFile.setFilename(saveAs.getName());
                    }
                } catch (SQLException e) {
                    remoteFile = null;
                }
            } catch (HttpStatusException e) {
                observer.downloadErrored(ripUrlId, Utils.getLocalizedString("failed.to.get.url.for.0", ripUrlId));
                logger.error("[!] Failed to get URL for " + ripUrlId);
                return; // do not retry
            } catch (IOException | URISyntaxException e) {
                logger.error("[!] Failed to get URL for " + ripUrlId, e);
                observer.downloadErrored(ripUrlId, Utils.getLocalizedString("failed.to.get.url.for.0", ripUrlId));
                return; // do not retry
            }

        } while (true);
        boolean renamed = saveAsPart.renameTo(saveAs);
        if (!renamed) {
            observer.downloadErrored(ripUrlId, Utils.getLocalizedString("failed.to.rename.0.to.1", saveAsPart, saveAs));
            return;
        }
        observer.downloadCompleted(ripUrlId, saveAs.toPath());
        if (remoteFile != null) {
            remoteFile.setFetched(true);
        }
        logger.info("[+] Saved " + url + " as " + prettySaveAs);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private boolean guessIsENOSPC(IOException e, File saveAs) {
        // The ENOSPC IOException message is localized in Java, so this only works on English locale systems.
        if (e.getMessage().matches("No space left on device")) {
            return true;
        }
        // Fallback: check usable space on the filesystem
        try {
            FileStore fs = Files.getFileStore(saveAs.toPath());
            // could check for 0 bytes, but 256 kilobytes is small enough
            int downloadBufferSizeBytes = 1024 * 256;
            boolean notEnoughUsableBytes = fs.getUsableSpace() < downloadBufferSizeBytes;
            return notEnoughUsableBytes;
        } catch (IOException ex) {
            // unable to determine if no space left on device; fall through
        }
        return false;
    }

}

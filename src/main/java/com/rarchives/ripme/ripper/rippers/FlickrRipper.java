package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rarchives.ripme.db.model.RemoteFile;
import com.rarchives.ripme.ripper.RipUrlId;
import com.rarchives.ripme.ui.DownloadedFilesLog;
import com.rarchives.ripme.utils.Utils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.rarchives.ripme.ripper.AbstractHTMLRipper;
import com.rarchives.ripme.ui.RipStatusMessage;
import com.rarchives.ripme.utils.Http;

public class FlickrRipper extends AbstractHTMLRipper {

    private static final Logger logger = LogManager.getLogger(FlickrRipper.class);
    public static final String HOST = "flickr";
    private static final Pattern fileNameId = Pattern.compile("^(\\d+)_.*");

    private enum UrlType {
        USER,
        PHOTOSET,
        GALLERY,
    }

    private class Album {
        final UrlType type;
        final String id;

        Album(UrlType type, String id) {
            this.type = type;
            this.id = id;
        }
    }

    static {
        DownloadedFilesLog.registerPathParser(FlickrRipper.class, FlickrRipper::decodePathRipUrlId);
    }

    private static RipUrlId decodePathRipUrlId(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        String parentDirName = path.getParent().getFileName().toString();
        if (!parentDirName.startsWith(HOST + "_")) {
            return null;
        }
        String fileName = path.getFileName().toString();
        Matcher matcher = fileNameId.matcher(fileName);
        if (matcher.matches()) {
            String photoId = matcher.group(1);
            return new RipUrlId(FlickrRipper.class, HOST, photoId);
        }
        return null;
    }


    @Override
    public boolean hasASAPRipping() {
        return true;
    }

    public FlickrRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    public String getHost() {
        return HOST;
    }
    @Override
    public String getDomain() {
        return "flickr.com";
    }

    @Override
    public URL sanitizeURL(URL url) throws MalformedURLException, URISyntaxException {
        String sUrl = url.toExternalForm();
        // Strip out https
        sUrl = sUrl.replace("https://secure.flickr.com", "http://www.flickr.com");
        // For /groups/ links, add a /pool to the end of the URL
        if (sUrl.contains("flickr.com/groups/") && !sUrl.contains("/pool")) {
            if (!sUrl.endsWith("/")) {
                sUrl += "/";
            }
            sUrl += "pool";
        }
        return new URI(sUrl).toURL();
    }
    // FLickr is one of those sites what includes a api key in sites javascript
    // TODO let the user provide their own api key
    private String getAPIKey(Document doc) {
        Pattern p;
        Matcher m;
        p = Pattern.compile("root.YUI_config.flickr.api.site_key = \"([a-zA-Z0-9]*)\";");
        for (Element e : doc.select("script")) {
            // You have to use .html here as .text will strip most of the javascript
            m = p.matcher(e.html());
            if (m.find()) {
                logger.info("Found api key:" + m.group(1));
                return m.group(1);
            }
        }
        logger.error("Unable to get api key");
        // A nice error message to tell our users what went wrong
        sendUpdate(RipStatusMessage.STATUS.DOWNLOAD_WARN, "Unable to extract api key from flickr");
        sendUpdate(RipStatusMessage.STATUS.DOWNLOAD_WARN, "Using hardcoded api key");
        return "935649baf09b2cc50628e2b306e4da5d";
    }

    // The flickr api is a monster of weird settings so we just request everything that the webview does
    private String apiURLBuilder(Album album, String pageNumber, String apiKey) {
        String method = null;
        String idField = null;
        switch (album.type) {
            case PHOTOSET:
                method = "flickr.photosets.getPhotos";
                idField = "photoset_id=" + album.id;
                break;
            case GALLERY:
                method = "flickr.galleries.getPhotos";
                idField = "gallery_id=" + album.id;
                break;
            case USER:
                method = "flickr.people.getPhotos";
                idField = "user_id=" + album.id;
                break;
        }

        return "https://api.flickr.com/services/rest?extras=can_addmeta," +
        "can_comment,can_download,can_share,contact,count_comments,count_faves,count_views,date_taken," +
        "date_upload,icon_urls_deep,isfavorite,ispro,license,media,needs_interstitial,owner_name," +
        "owner_datecreate,path_alias,realname,rotation,safety_level,secret_k,secret_h,url_c,url_f,url_h,url_k," +
        "url_l,url_m,url_n,url_o,url_q,url_s,url_sq,url_t,url_z,visibility,visibility_source,o_dims," +
        "is_marketplace_printable,is_marketplace_licensable,publiceditability,tags,autotags,&per_page=100&page="+ pageNumber + "&" +
        "get_user_info=1&primary_photo_extras=url_c,%20url_h,%20url_k,%20url_l,%20url_m,%20url_n,%20url_o" +
        ",%20url_q,%20url_s,%20url_sq,%20url_t,%20url_z,%20needs_interstitial,%20can_share&jump_to=&" +
        idField + "&viewerNSID=&method=" + method + "&csrf=&" +
        "api_key=" + apiKey + "&format=json&hermes=1&hermesClient=1&reqId=358ed6a0&nojsoncallback=1";
    }

    private URL apiPhotoInfoUrl(String photoId, String apiKey) throws URISyntaxException, MalformedURLException {
        String extras = String.join(",",
                "sizes"
                //, "icon_urls"
                //, "ignored"
                //, "rev_ignored"
                //, "rev_contacts"
                //, "venue"
                , "datecreate"
                //, "ad_eligibility"
                //, "can_addmeta"
                //, "can_comment"
                //, "can_download"
                //, "can_print"
                //, "can_share"
                , "contact"
                , "content_type"
                //, "count_comments"
                //, "count_faves"
                //, "count_views"
                , "date_taken"
                , "date_upload"
                , "description"
                //, "icon_urls_deep"
                //, "isfavorite"
                //, "ispro"
                , "license"
                , "media"
                //, "needs_interstitial"
                , "owner_name"
                , "owner_datecreate"
                , "path_alias"
                //, "perm_print"
                , "realname"
                //, "rotation"
                , "safety_level"
                //, "secret_k"
                //, "secret_h"
                //, "url_sq"
                //, "url_q"
                //, "url_t"
                //, "url_s"
                //, "url_n"
                //, "url_w"
                //, "url_m"
                //, "url_z"
                //, "url_c"
                //, "url_l"
                //, "url_h"
                //, "url_k"
                //, "url_3k"
                //, "url_4k"
                //, "url_f"
                //, "url_5k"
                //, "url_6k"
                , "url_o"
                , "visibility"
                , "visibility_source"
                //, "o_dims"
                //, "publiceditability"
                , "system_moderation"
                //, "static_maps"
                , "tags"
                , "autotags"
        );
        String extrasEncoded = URLEncoder.encode(extras, StandardCharsets.UTF_8);

        return new URI("https://api.flickr.com/services/rest?datecreate=1&extras=" + extrasEncoded + "&photo_id=" + photoId + "&method=flickr.photos.getInfo&api_key=" + apiKey + "&format=json&hermes=1&hermesClient=1&nojsoncallback=1").toURL();
    }

    private URL apiPhotoTagsUrl(String photoId, String apiKey) throws URISyntaxException, MalformedURLException {
        //https://api.flickr.com/services/rest?photo_id=28869282056&extras=autotags&lang=en-US&viewerNSID=&method=flickr.tags.getListPhoto&csrf=&api_key=...&format=json&hermes=1&hermesClient=1&reqId=...&nojsoncallback=1
        String extras = "autotags";
        String extrasEncoded = URLEncoder.encode(extras, StandardCharsets.UTF_8);
        return new URI("https://api.flickr.com/services/rest?" + "photo_id=" + photoId + "&extras=" + extrasEncoded + "&method=flickr.tags.getListPhoto&api_key=" + apiKey + "&format=json&hermes=1&hermesClient=1&nojsoncallback=1").toURL();
    }

    private JSONObject getJSON(String page, String apiKey) {
        URL pageURL = null;
        String apiURL = null;
        try {
            apiURL = apiURLBuilder(getAlbum(url.toExternalForm()), page, apiKey);
            pageURL = new URI(apiURL).toURL();
        }  catch (MalformedURLException | URISyntaxException e) {
            logger.error("Unable to get api link " + apiURL + " is malformed");
        }
        try {
            logger.info("Fetching: " + apiURL);
            String body = Http.url(pageURL).ignoreContentType().response().body();
            logger.info("Response: " + body);
            return new JSONObject(body);
        } catch (IOException e) {
            logger.error("Unable to get api link " + apiURL + " is malformed");
            return null;
        }
    }

    private Album getAlbum(String url) throws MalformedURLException {
        Pattern p; Matcher m;

        // User photostream:  https://www.flickr.com/photos/115858035@N04/
        // Album: https://www.flickr.com/photos/115858035@N04/sets/72157644042355643/
        // Gallery: https://www.flickr.com/photos/shantanu_dutta/galleries/72157719881618264/

        final String domainRegex = "https?://[wm.]*flickr.com";
        final String userRegex = "[a-zA-Z0-9@_-]+";
        // Album
        p = Pattern.compile("^" + domainRegex + "/photos/" + userRegex + "/(sets|albums)/([0-9]+)/?.*$");
        m = p.matcher(url);
        if (m.matches()) {
            return new Album(UrlType.PHOTOSET, m.group(2));
        }

        // Gallery
        p = Pattern.compile("^" + domainRegex + "/photos/" + userRegex + "/(galleries)/([0-9]+)/?.*$");
        m = p.matcher(url);
        if (m.matches()) {
            return new Album(UrlType.GALLERY, m.group(2));
        }

        // User photostream
        p = Pattern.compile("^" + domainRegex + "/photos/(" + userRegex + ")/?$");
        m = p.matcher(url);
        if (m.matches()) {
            return new Album(UrlType.USER, m.group(1));
        }

        String errorMessage = "Failed to extract photoset ID from url: " + url;

        logger.error(errorMessage);
        throw new MalformedURLException(errorMessage);
    }

    @Override
    public String getAlbumTitle(URL url) throws MalformedURLException, URISyntaxException {
        if (!url.toExternalForm().contains("/sets/")) {
            return super.getAlbumTitle(url);
        }
        try {
            // Attempt to use album title as GID
            Document doc = getCachedFirstPage();
            String user = url.toExternalForm();
            user = user.substring(user.indexOf("/photos/") + "/photos/".length());
            user = user.substring(0, user.indexOf("/"));
            String title = doc.select("meta[name=description]").get(0).attr("content");
            if (!title.equals("")) {
                return getHost() + "_" + user + "_" + title;
            }
        } catch (Exception e) {
            // Fall back to default album naming convention
        }
        return super.getAlbumTitle(url);
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        Pattern p; Matcher m;

        // Root:  https://www.flickr.com/photos/115858035@N04/
        // Album: https://www.flickr.com/photos/115858035@N04/sets/72157644042355643/

        final String domainRegex = "https?://[wm.]*flickr.com";
        final String userRegex = "[a-zA-Z0-9@_-]+";
        // Album or Gallery
        p = Pattern.compile("^" + domainRegex + "/photos/(" + userRegex + ")/(?:sets|albums|galleries)/([0-9]+)/?.*$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            return m.group(1) + "_" + m.group(2);
        }

        // User page
        p = Pattern.compile("^" + domainRegex + "/photos/(" + userRegex + ").*$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            return m.group(1);
        }

        // Groups page
        p = Pattern.compile("^" + domainRegex + "/groups/(" + userRegex + ").*$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            return "groups-" + m.group(1);
        }
        throw new MalformedURLException(
                "Expected flickr.com URL formats: "
                        + "flickr.com/photos/username or "
                        + "flickr.com/photos/username/sets/albumid"
                        + " Got: " + url);
    }


    @Override
    public List<String> getURLsFromPage(Document doc) {
        List<String> imageURLs = new ArrayList<>();
        String apiKey = getAPIKey(doc);
        int x = 1;
        while (true) {
            if (isStopped()) {
                return imageURLs;
            }
            JSONObject jsonData = getJSON(String.valueOf(x), apiKey);
            if (jsonData.has("stat") && jsonData.getString("stat").equals("fail")) {
                break;
            } else {
                // Determine root key
                JSONObject rootData;

                try {
                    rootData = jsonData.getJSONObject("photoset");
                } catch (JSONException e) {
                    try {
                        rootData = jsonData.getJSONObject("photos");
                    } catch (JSONException innerE) {
                        logger.error("Unable to find photos in response");
                        break;
                    }
                }
                boolean isFirstPage = x == 1;
                if (isFirstPage && album != null) {
                    Optional.ofNullable(rootData.optString("description", null))
                            .ifPresent(album::setDescription);
                    Optional.ofNullable(rootData.optString("title", null))
                            .ifPresent(album::setTitle);

                    // Example user:
                    //    "nsid" : "99999999@N06",
                    //    "path_alias" : "shaxxxxx_xxxta",
                    //    "username" : "SHAX XXXTA",
                    //    "realname" : "SHAXXXXX XXXTA"
                    Optional.ofNullable(rootData.optString("ownername", null))
                            .or(() -> Optional.ofNullable(jsonData.optJSONObject("user", null))
                                    .flatMap(o -> Optional.ofNullable(o.optString("username", null))))
                            .ifPresent(album::setUploader);
                }

                int totalPages = rootData.getInt("pages");
                int totalFiles = rootData.getInt("total");
                setItemsTotal(totalFiles);
                logger.info(jsonData);
                JSONArray pictures = rootData.getJSONArray("photo");
                for (int i = 0; i < pictures.length(); i++) {
                    if (isStopped()) {
                        return imageURLs;
                    }
                    logger.info(i);
                    JSONObject data = (JSONObject) pictures.get(i);
                    String flickrImageId = data.getString("id");

                    // No need to make ripUrlId for every image size because we only download the largest
                    RipUrlId ripUrlId = new RipUrlId(getClass(), getHost(), flickrImageId);

                    if ("video".equals(data.optString("media"))) {
                        downloadErrored(ripUrlId, "Video is not supported by FlickrRipper yet");
                        continue;
                    }

                    RemoteFile remoteFile = null;
                    try {
                        remoteFile = ripService.getRemoteFile(ripUrlId);
                        if (remoteFile.isFetched() || remoteFile.isIgnored()) {
                            logger.info("Already fetched: {} / {}", remoteFile.getUrlId(), remoteFile.getFilename());
                            downloadSkipped(ripUrlId, "Already fetched: " + remoteFile.getUrlId() + " / " + remoteFile.getFilename());
                            continue;
                        }
                    } catch (SQLException e) {
                        // Bad, but continue...
                    }
                    JSONObject photoInfoJson;
                    try {
                        photoInfoJson = getImageMeta(flickrImageId, apiKey);
                    } catch (URISyntaxException | IOException e) {
                        logger.error("Unable to get image info: {}", e.getMessage(), e);
                        continue;
                    }
                    JSONObject photoTagsJson = new JSONObject();
                    try {
                        // Space-separated mangled tags
                        String basicTags = data.optString("tags");
                        if (!basicTags.isEmpty()) {
                            // Get non-mangled tags
                            photoTagsJson = getPhotoTags(flickrImageId, apiKey);
                        }
                    } catch (URISyntaxException | IOException e) {
                        logger.error("Unable to get image info: {}", e.getMessage(), e);
                        continue;
                    }
                    try {
                        URL largestImageURL = getLargestImageURL(photoInfoJson);
                        if (remoteFile != null) {
                            setImageMeta(remoteFile, photoInfoJson, photoTagsJson);
                            ripService.saveRemoteFile(remoteFile);
                        }
                        addURLToDownload(largestImageURL, ripUrlId);
                    } catch (MalformedURLException | URISyntaxException e) {
                        logger.error("Flickr MalformedURLException: " + e.getMessage());
                    }

                }
                if (x >= totalPages) {
                    // The rips done
                    break;
                }
                // We have more pages to download so we rerun the loop
                x++;

            }
        }

        return imageURLs;
    }

    @Override
    public void downloadURL(URL url, int index) {
        throw new UnsupportedOperationException("This method should not be called, because this ripper is an ASAP ripper.");
        //addURLToDownload(url, getPrefix(index));
    }

    private URL getLargestImageURL(JSONObject photoInfoJson) throws MalformedURLException, URISyntaxException {
        TreeMap<Integer, String> imageURLMap = new TreeMap<>();

        JSONArray imageSizes = photoInfoJson.getJSONObject("photo").getJSONObject("sizes").getJSONArray("size");
        for (int i = 0; i < imageSizes.length(); i++) {
            JSONObject imageInfo = imageSizes.getJSONObject(i);
            imageURLMap.put(imageInfo.getInt("width") * imageInfo.getInt("height"), imageInfo.getString("source"));
        }

        return new URI(imageURLMap.lastEntry().getValue()).toURL();
    }

    private JSONObject getImageMeta(String flickrImageId, String apiKey) throws URISyntaxException, IOException {
        URL photoInfoUrl = apiPhotoInfoUrl(flickrImageId, apiKey);
        String photoInfoText = Http.url(photoInfoUrl).ignoreContentType().response().body();
        JSONObject photoInfoJson = new JSONObject(photoInfoText);
        return photoInfoJson;
    }

    private JSONObject getPhotoTags(String flickrImageId, String apiKey) throws URISyntaxException, IOException {
        URL photoTagsUrl = apiPhotoTagsUrl(flickrImageId, apiKey);
        String photoTagsText = Http.url(photoTagsUrl).ignoreContentType().response().body();
        JSONObject photoTagsJson = new JSONObject(photoTagsText);
        return photoTagsJson;
    }

    private void setImageMeta(RemoteFile remoteFile, JSONObject photoInfoJson, JSONObject photoTagsJson) {
        JSONObject photoJson = photoInfoJson.optJSONObject("photo");
        if (photoJson == null) {
            logger.error("Unable to get image info");
            return;
        }

        Optional.ofNullable(photoJson.optJSONObject("title", null))
                .map(x -> x.optString("_content", null))
                .map(StringEscapeUtils::unescapeHtml) // Note: storing unsafe HTML in db - TODO: just get html text node content?
                .ifPresent(remoteFile::setTitle);

        Optional.ofNullable(photoJson.optString("dateupload", null))
                .map(Utils::tryParseLong).map(Instant::ofEpochSecond)
                .ifPresent(remoteFile::setUploadedTs);

        Optional.ofNullable(photoJson.optJSONObject("owner", null))
                .map(x -> x.optString("username", null))
                .ifPresent(remoteFile::setUploader);

        Optional.ofNullable(photoJson.optJSONObject("description"))
                .map(x -> x.optString("_content", null))
                .map(StringEscapeUtils::unescapeHtml) // Note: storing unsafe HTML in db
                .ifPresent(remoteFile::setDescription);

        HashSet<String> tags = new HashSet<>();
        JSONObject tagsJson = photoJson.optJSONObject("tags");
        if (tagsJson != null) {
            for (Object o : tagsJson.optJSONArray("tag", new JSONArray(0))) {
                if (!(o instanceof JSONObject tagObj)) {
                    continue;
                }
                String rawTag = tagObj.optString("raw", null);
                if (rawTag != null) {
                    tags.add(rawTag);
                }
            }
        }
        JSONArray tagJsonArray = Optional.ofNullable(photoTagsJson.optJSONObject("photo", null))
                .map(x -> x.optJSONObject("tags", null))
                .map(x -> x.optJSONArray("tag", new JSONArray(0)))
                .orElse(new JSONArray(0));
        for (Object o : tagJsonArray) {
            if (!(o instanceof JSONObject tagObj)) {
                continue;
            }
            String rawTag = tagObj.optString("raw", null);
            if (rawTag != null) {
                tags.add(rawTag);
            }
        }
        if (!tags.isEmpty()) {
            remoteFile.setTags(tags);
        }

        //{
        //  "stat" : "ok",
        //  "photo" : {
        //    "server" : "5616",
        //    "dateuploaded" : "1477012817",
        //    "media_status" : "ready",
        //    "notes" : {
        //      "note" : [ ]
        //    },
        //    "datetakengranularity" : 0,
        //    "isfriend" : 0,
        //    "safety_level" : "0",
        //    "usage" : {
        //      "canshare" : 1,
        //      "canprint" : 0,
        //      "canblog" : 0,
        //      "candownload" : 0
        //    },
        //    "system_moderated" : 0,
        //    "contact_isfamily" : 0,
        //    "description" : {
        //      "_content" : "&quot;Autumn is a second spring when every leaf is a flower.&quot; \n- Albert Camus\n\nYou've seen them in Explore already and it's time for a Flickr Friday photo challenge of 'Autumn Leaves' from around the world! Get creative with it. Show some color, contrast, texture, or take a lifestyle approach to this week's shot, or just do your own thing with leaves to express the beauty of the season. Be sure to tag your photo <strong>#AutumnLeaves</strong> and submit it to the <a href=\"http://www.flickr.com/groups/flickrfriday\">Flickr Friday group pool</a> by Thursday afternoon next week! We'll publish a selection of our favorites next week on the Flickr Blog and in a Flickr Gallery.\n\nOriginal photo by <a href=\"https://www.flickr.com/photos/christian_philippe/29732526793/\">Christian Philippe.</a>"
        //    },
        //    "secret" : "0289f44012",
        //    "media" : "photo",
        //    "title" : {
        //      "_content" : "Flickr Friday - Autumn Leaves"
        //    },
        //    "visibility_source" : true,
        //    "urls" : {
        //      "url" : [ {
        //        "type" : "photopage",
        //        "_content" : "https://www.flickr.com/photos/flickr/30459338405/"
        //      } ]
        //    },
        //    "datetaken" : "2016-10-20 18:19:05",
        //    "sizes" : {
        //      "size" : [ {
        //        "width" : 75,
        //        "label" : "Square",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_s.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_s.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/sq/",
        //        "height" : 75
        //      }, {
        //        "width" : 150,
        //        "label" : "Large Square",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_q.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_q.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/q/",
        //        "height" : 150
        //      }, {
        //        "width" : 100,
        //        "label" : "Thumbnail",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_t.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_t.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/t/",
        //        "height" : 75
        //      }, {
        //        "width" : 240,
        //        "label" : "Small",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_m.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_m.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/s/",
        //        "height" : 180
        //      }, {
        //        "width" : 320,
        //        "label" : "Small 320",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_n.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_n.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/n/",
        //        "height" : 240
        //      }, {
        //        "width" : 400,
        //        "label" : "Small 400",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_w.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_w.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/w/",
        //        "height" : 300
        //      }, {
        //        "width" : 500,
        //        "label" : "Medium",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/m/",
        //        "height" : 375
        //      }, {
        //        "width" : 640,
        //        "label" : "Medium 640",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_z.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_z.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/z/",
        //        "height" : 480
        //      }, {
        //        "width" : 800,
        //        "label" : "Medium 800",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_c.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_c.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/c/",
        //        "height" : 600
        //      }, {
        //        "width" : 1024,
        //        "label" : "Large",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0289f44012_b.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0289f44012_b.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/l/",
        //        "height" : 768
        //      }, {
        //        "width" : 1600,
        //        "label" : "Large 1600",
        //        "source" : "https://live.staticflickr.com/5616/30459338405_0a525cf841_h.jpg",
        //        "media" : "photo",
        //        "source_cdn" : "https://live.staticflickr.com/5616/30459338405_0a525cf841_h.jpg",
        //        "url" : "https://www.flickr.com/photos/flickr/30459338405/sizes/h/",
        //        "height" : 1200
        //      } ]
        //    },
        //    "content_type" : "0",
        //    "editability" : {
        //      "cancomment" : 0,
        //      "canaddmeta" : 0
        //    },
        //    "ispublic" : 1,
        //    "farm" : 6,
        //    "id" : "30459338405",
        //    "pathalias" : "flickr",
        //    "views" : "374530",
        //    "owner" : {
        //      "gift" : {
        //        "eligible_durations" : [ ],
        //        "gift_eligible" : false,
        //        "new_flow" : true
        //      },
        //      "iconfarm" : 4,
        //      "noindexfollow" : 0,
        //      "pro_badge" : "legacy",
        //      "is_ad_free" : 0,
        //      "datecreate" : "2004-02-10 12:00:00",
        //      "realname" : "Flickr",
        //      "nsid" : "66956608@N06",
        //      "path_alias" : "flickr",
        //      "iconserver" : "3741",
        //      "location" : "",
        //      "ispro" : 1,
        //      "username" : "Flickr"
        //    },
        //    "owner_datecreate" : "2004-02-10 12:00:00",
        //    "comments" : {
        //      "_content" : "49"
        //    },
        //    "visibility" : {
        //      "content_type" : "0",
        //      "hidden" : 0,
        //      "ispublic" : 1,
        //      "isfriend" : 0,
        //      "safety_level" : "0",
        //      "moderation_mode" : "0",
        //      "isfamily" : 0
        //    },
        //    "publiceditability" : {
        //      "cancomment" : 1,
        //      "canaddmeta" : 1
        //    },
        //    "ownername" : "Flickr",
        //    "rotation" : 0,
        //    "HTMLMETA" : {
        //      "og:image" : "https://live.staticflickr.com/5616/30459338405_0289f44012_b.jpg",
        //      "twitter:app:url:iphone" : "flickr://flickr.com/photos/flickr/30459338405/",
        //      "twitter:card" : "summary_large_image",
        //      "og:image:width" : 1024,
        //      "og:site_name" : "Flickr",
        //      "keywords" : "autumn, leaves, flickrfriday, autumnleaves, pp92fc",
        //      "twitter:app:url:ipad" : "flickr://flickr.com/photos/flickr/30459338405/",
        //      "flickr_photos:galleries" : [ "https://www.flickr.com/photos/192773297@N07/galleries/72157718954267657", "https://www.flickr.com/photos/168676044@N07/galleries/72157707059740545", "https://www.flickr.com/photos/145306900@N07/galleries/72157676352933466" ],
        //      "description" : "&quot;Autumn is a second spring when every leaf is a flower.&quot;  - Albert Camus  You've seen them in Explore already and it's time for a Flickr Friday photo challenge of 'Autumn Leaves' from around the world! Get creative with it. Show some color, contrast, texture, or take a lifestyle approach to this week's shot, or just do your own thing with leaves to express the beauty of the season. Be sure to tag your photo #AutumnLeaves and submit it to the Flickr Friday group pool by Thursday afternoon next week! We'll publish a selection of our favorites next week on the Flickr Blog and in a Flickr Gallery.  Original photo by Christian Philippe.",
        //      "twitter:app:id:iphone" : "328407587",
        //      "al:ios:url" : "flickr://flickr.com/photos/flickr/30459338405/",
        //      "og:description" : "\"Autumn is a second spring when every leaf is a flower.\"  - Albert Camus  You've seen them in Explore already and it's time for a Flickr Friday photo challenge of 'Autumn Leaves' from around the world! Get creative with it. Show some color, contrast, texture, or take a lifestyle approach to this week's shot, or just do your own thing with leaves to express the beauty of the season. Be sure to tag your photo #AutumnLeaves and submit it to the Flickr Friday group pool by Thursday afternoon next week! We'll publish a selection of our favorites next week on the Flickr Blog and in a Flickr Gallery.  Original photo by Christian Philippe.",
        //      "twitter:creator" : "@Flickr",
        //      "al:ios:app_store_id" : "328407587",
        //      "twitter:image" : "https://live.staticflickr.com/5616/30459338405_0289f44012_b.jpg",
        //      "twitter:site" : "@flickr",
        //      "flickr_photos:by" : "https://www.flickr.com/people/flickr/",
        //      "og:type" : "article",
        //      "al:ios:app_name" : "Flickr",
        //      "og:title" : "Flickr Friday - Autumn Leaves",
        //      "og:image:height" : 768,
        //      "fb:app_id" : "462754987849668",
        //      "flickr_photos:groups" : [ "https://www.flickr.com/groups/flickrfriday/" ],
        //      "twitter:app:url:googleplay" : "https://www.flickr.com/photos/flickr/30459338405/",
        //      "twitter:description" : "\"Autumn is a second spring when every leaf is a flower.\"  - Albert Camus  You've seen them in Explore already and it's time for a Flickr Friday photo challenge of 'Autumn Leaves' from around the world! Get creative with it. Show some color, contrast, texture, or take a lifestyle approach to this week's shot, or just do your own thing with leaves to express the beauty of the season. Be sure to tag your photo #AutumnLeaves and submit it to the Flickr Friday group pool by Thursday afternoon next week! We'll publish a selection of our favorites next week on the Flickr Blog and in a Flickr Gallery.  Original photo by Christian Philippe.",
        //      "og:url" : "https://www.flickr.com/photos/flickr/30459338405/",
        //      "twitter:app:name:iphone" : "Flickr"
        //    },
        //    "dates" : {
        //      "taken" : "2016-10-20 18:19:05",
        //      "takengranularity" : 0,
        //      "lastupdate" : "1551609688",
        //      "takenunknown" : "1",
        //      "posted" : "1477012817"
        //    },
        //    "people" : {
        //      "haspeople" : 1
        //    },
        //    "isfamily" : 0,
        //    "tags" : {
        //      "tag" : [ ]
        //    },
        //    "dateupload" : "1477012817",
        //    "realname" : "Flickr",
        //    "license" : "0",
        //    "contact_isfriend" : 0,
        //    "contact_iscontact" : 0,
        //    "safe" : 1,
        //    "isfavorite" : 0,
        //    "datetakenunknown" : "1"
        //  }
        //}
    }
}

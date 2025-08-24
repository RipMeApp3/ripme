package com.rarchives.ripme.db.model;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public record SplitUrl(URL url) {

    public static SplitUrl of(URL url) {
        return new SplitUrl(url);
    }

    /**
     * @return The base part of the URL external form:<br>
     * {@code https://example.com} of {@code https://example.com/issues?state=open&sort=created#issue-2}
     */
    public String getBase() {
        if (url == null) {
            return null;
        }
        String temp = url.getAuthority();
        return url.getProtocol()
                + ':'
                + (temp != null && !temp.isEmpty() ? "//" + temp : "");
    }

    /**
     * @return The part of the URL external form after the base:<br>
     * {@code /issues?state=open&sort=created#issue-2} of {@code https://example.com/issues?state=open&sort=created#issue-2}
     */
    public String getPath() {
        if (url == null) {
            return null;
        }
        String temp;
        return ((temp = url.getPath()) != null ? temp : "")
                + ((temp = url.getQuery()) != null ? '?' + temp : "")
                + ((temp = url.getRef()) != null ? '#' + temp : "");
    }

    /**
     * Create a new URL without the query parameters and fragment
     * @return A new URL without the query parameters and fragment
     * @throws URISyntaxException if {@code new URI()} fails {@link URI#URI(String, String, String, String, String)}}
     * @throws MalformedURLException if {@code uri.toURL()} fails {@link URI#toURL()}
     */
    public URL noQueryFragment() throws URISyntaxException, MalformedURLException {
        URL noQueryFragment = new URI(url.getProtocol(), url.getAuthority(), url.getPath(), null, null).toURL();
        return noQueryFragment;
    }
}

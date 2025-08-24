package com.rarchives.ripme.test;

import com.rarchives.ripme.ripper.AbstractHTMLRipper;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public class FakeTestRipper extends AbstractHTMLRipper {
    public FakeTestRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    protected String getDomain() {
        return "localhost.localdomain";
    }

    @Override
    public String getHost() {
        return "localhost";
    }

    @Override
    public String getGID(URL url) throws MalformedURLException, URISyntaxException {
        return url.getPath();
    }

    @Override
    protected List<String> getURLsFromPage(Document page) throws UnsupportedEncodingException, URISyntaxException {
        return List.of();
    }

    @Override
    protected void downloadURL(URL url, int index) {

    }

    @Override
    protected Document getFirstPage() throws IOException, URISyntaxException {
        throw new HttpStatusException("404", 404, url.toString());
    }
}

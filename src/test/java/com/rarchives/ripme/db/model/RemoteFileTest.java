package com.rarchives.ripme.db.model;

import com.rarchives.ripme.ripper.RipUrlId;
import com.rarchives.ripme.test.FakeTestRipper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class RemoteFileTest {

    @Test
    void setRipperClass_setsRipperName() {
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setRipperClass(FakeTestRipper.class);
        assertEquals(FakeTestRipper.class, remoteFile.getRipperClass());
        assertEquals(FakeTestRipper.class.getSimpleName(), remoteFile.getRipperName());
    }

    @Test
    void setMimeType() {
        RemoteFile remoteFile = new RemoteFile();
        remoteFile.setMimeType("text/html");
        assertEquals("text/html", remoteFile.getMimeType());
        assertThrows(IllegalArgumentException.class, () -> remoteFile.setMimeType("no/parameters; foobar"));
        assertThrows(IllegalArgumentException.class, () -> remoteFile.setMimeType("NO/CAPITALS"));
    }

    @Test
    void setRipUrlId() throws IOException {
        RemoteFile remoteFile = new RemoteFile();
        FakeTestRipper fakeTestRipper = new FakeTestRipper(URI.create("http://localhost.localdomain/example1").toURL());
        RipUrlId ripUrlId = new RipUrlId(FakeTestRipper.class, fakeTestRipper.getHost(), "example1_file1");
        remoteFile.setRipUrlId(ripUrlId);
        assertEquals(FakeTestRipper.class, remoteFile.getRipperClass());
        assertEquals(FakeTestRipper.class.getSimpleName(), remoteFile.getRipperName());
        assertEquals(fakeTestRipper.getHost(), remoteFile.getRipperHost());
        assertEquals(ripUrlId.getRipUrlId(), remoteFile.getUrlId());
        assertNull(remoteFile.getUrl());
    }

    @Test
    void setRipUrlIdWithUrl() throws IOException {
        RemoteFile remoteFile = new RemoteFile();
        FakeTestRipper fakeTestRipper = new FakeTestRipper(URI.create("http://localhost.localdomain/example1").toURL());
        RipUrlId ripUrlId = new RipUrlId(FakeTestRipper.class, fakeTestRipper.getHost(), URI.create("http://localhost.localdomain/example1").toURL());
        remoteFile.setRipUrlId(ripUrlId);
        assertEquals(FakeTestRipper.class, remoteFile.getRipperClass());
        assertEquals(FakeTestRipper.class.getSimpleName(), remoteFile.getRipperName());
        assertEquals(fakeTestRipper.getHost(), remoteFile.getRipperHost());
        assertEquals(ripUrlId.getUrl(), remoteFile.getUrl());
        assertNull(remoteFile.getUrlId());
    }
}

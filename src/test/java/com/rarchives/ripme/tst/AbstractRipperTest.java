package com.rarchives.ripme.tst;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;

import com.rarchives.ripme.db.model.Album;
import com.rarchives.ripme.db.service.RipService;
import com.rarchives.ripme.test.FakeTestRipper;
import com.rarchives.ripme.test.SQLiteTestBase;
import com.rarchives.ripme.test.WithInMemoryDb;
import org.junit.jupiter.api.Test;

import com.rarchives.ripme.ripper.AbstractRipper;

public class AbstractRipperTest extends SQLiteTestBase {
   @Test
   public void testGetFileName() throws IOException, URISyntaxException {
      String fileName = AbstractRipper.getFileName(
            new URI("http://www.tsumino.com/Image/Object?name=U1EieteEGwm6N1dGszqCpA%3D%3D").toURL(),
            null, "test", "test");
      assertEquals("test.test", fileName);

      fileName = AbstractRipper.getFileName(
            new URI("http://www.tsumino.com/Image/Object?name=U1EieteEGwm6N1dGszqCpA%3D%3D").toURL(),
            null, "test", null);
      assertEquals("test", fileName);

      fileName = AbstractRipper.getFileName(
            new URI("http://www.tsumino.com/Image/Object?name=U1EieteEGwm6N1dGszqCpA%3D%3D").toURL(),
            null, null, null);
      assertEquals("Object", fileName);

      fileName = AbstractRipper.getFileName(new URI("http://www.test.com/file.png").toURL(),
            null, null, null);
      assertEquals("file.png", fileName);

      fileName = AbstractRipper.getFileName(new URI("http://www.test.com/file.").toURL(),
            null, null, null);
      assertEquals("file.", fileName);
   }

   @Test
   @WithInMemoryDb
   public void run404() throws IOException, URISyntaxException, SQLException {
       AbstractRipper fakeTestRipper = new FakeTestRipper(URI.create("http://localhost.localdomain/album1").toURL());
       RipService ripService = new RipService(db);
       fakeTestRipper.setup(ripService);
       fakeTestRipper.run();
       Album album = ripService.getAlbum(FakeTestRipper.class, "album1", fakeTestRipper.getGID(fakeTestRipper.getURL()), fakeTestRipper.getURL());
       assertEquals(1, album.getId());
       assertEquals("/album1", album.getGid());
   }

}

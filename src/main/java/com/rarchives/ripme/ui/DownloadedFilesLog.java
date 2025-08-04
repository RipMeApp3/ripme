package com.rarchives.ripme.ui;

import com.rarchives.ripme.ripper.AbstractRipper;
import com.rarchives.ripme.ripper.RipUrlId;
import com.rarchives.ripme.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class DownloadedFilesLog {
    private static final Logger logger = LogManager.getLogger(DownloadedFilesLog.class);

    public static final String DOWNLOADED_FILES_LOG = "ripme.downloaded.files.log";

    private static final Map<Class<? extends AbstractRipper>, Function<Path, RipUrlId>> pathParsers = new HashMap<>();

    private final Map<RipUrlId, File> ripUrlIds = Collections.synchronizedMap(new HashMap<>());

    static {
        // Trigger ripper classes to get loaded to ensure they call registerPathParser()
        Utils.getClassesForPackage("com.rarchives.ripme.ripper.rippers");
        Utils.getClassesForPackage("com.rarchives.ripme.ripper.rippers.video");
    }

    /**
     * Called by the Ripper implementation to register a Path -> RipUrlId parser
     * @param clazz  The Ripper's class associated with the parser
     * @param parser Reads a Path and returns the associated RipUrlId if able, otherwise returns null
     */
    public static void registerPathParser(Class<? extends AbstractRipper> clazz, Function<Path, RipUrlId> parser) {
        pathParsers.put(clazz, parser);
    }

    public int size() {
        return ripUrlIds.size();
    }

    public void add(RipUrlId ripUrlId, File file) {
        ripUrlIds.put(ripUrlId, file);
        try (FileWriter fw = new FileWriter(DOWNLOADED_FILES_LOG, true)) {
            fw.append("./").append(Utils.removeCWD(file.toPath())).append("\n");
        } catch (IOException e) {
            logger.error("Unable to append downloaded file to {}", DOWNLOADED_FILES_LOG, e);
        }
    }

    public boolean exists(RipUrlId ripUrlId) {
        if (ripUrlId == null) {
            return false;
        }
        return ripUrlIds.containsKey(ripUrlId);
    }

    public File get(RipUrlId ripUrlId) {
        if (ripUrlId == null) {
            return null;
        }
        File file = ripUrlIds.get(ripUrlId);
        return file;
    }

    public void load() {
        File dfLog = new File(DOWNLOADED_FILES_LOG);
        try {
            if (!dfLog.exists()) {
                boolean existed = dfLog.createNewFile();
            }
            load(dfLog);
        } catch (IOException e) {
            logger.error("Unable to read downloaded files log", e);
        }
    }

    public void load(File file) throws IOException {
        logger.info("Loading downloaded file history...");
        try (InputStream is = new FileInputStream(file);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    File originalFile = new File(line);
                    Path originalPath = originalFile.toPath();
                    for (Map.Entry<Class<? extends AbstractRipper>, Function<Path, RipUrlId>> classParser : pathParsers.entrySet()) {
                        Class<? extends AbstractRipper> ripperClass = classParser.getKey();
                        Function<Path, RipUrlId> parser = classParser.getValue();
                        RipUrlId ripUrlId = parser.apply(originalPath);
                        if (ripUrlId != null) {
                            logger.trace("Parsing file {} as {}", originalPath, ripperClass);
                            ripUrlIds.put(ripUrlId, originalFile);
                            break;
                        }
                    }
                }
            }
        }
        logger.info("Finished loading downloaded file history: {} files", size());
    }
}

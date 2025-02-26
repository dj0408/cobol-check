package org.openmainframeproject.cobolcheck.services.filehelpers;

import org.openmainframeproject.cobolcheck.services.Config;
import org.openmainframeproject.cobolcheck.services.Constants;
import org.openmainframeproject.cobolcheck.services.Messages;
import org.openmainframeproject.cobolcheck.services.log.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class PathHelper {

    private PathHelper() { }
    /**
     * Gets the path for the output file.
     */
    public static String getTestSourceOutPath(){
        StringBuilder testSourceOutPath = new StringBuilder();
        String configPath = Config.getGeneratedTestCodePath();
        if (configPath.equals("." + Constants.FILE_SEPARATOR))
            testSourceOutPath.append(new File(Constants.EMPTY_STRING).getAbsolutePath());
        else
            testSourceOutPath.append(new File(configPath).getAbsolutePath());

        testSourceOutPath.append(Constants.FILE_SEPARATOR);
        testSourceOutPath.append(Config.getGeneratedTestFileName());
        return testSourceOutPath.toString();
    }

    /**
     * Returns a string which has a matched file suffix
     *
     * @param filePath - Path to the file without a suffix.
     * @param applicationSuffixes - Possible suffixes for the file
     */
    public static String appendMatchingFileSuffix(String filePath, List<String> applicationSuffixes){
        for (String suffix : applicationSuffixes) {
            Log.debug("Initializer looking for source file <" + filePath + suffix + ">");
            if (Files.isRegularFile(Paths.get(filePath + suffix))) {
                filePath += suffix;
                Log.debug("Initializer recognized this file as a regular file: <" + filePath.toString() + ">");
                break;
            }
        }
        return filePath;
    }

    /**
     * Gets all directories with a matching name
     *
     * @param name - The name of directories to look for.
     * @param path - Determines what path to look for matching directories.
     * @throws IOException - pass any InterruptedException to the caller.
     */
    public static List<String> getMatchingDirectories(String name, String path) throws IOException{
        List<String> matchingDirectories;
        DirectoryNameMatcher directoryFinder = new DirectoryNameMatcher(name);

        Files.walkFileTree(Paths.get(path), directoryFinder);
        matchingDirectories = directoryFinder.getMatchingDirectories();
        if (matchingDirectories.isEmpty()) {
            Log.warn(Messages.get("WRN001", name, path));
        }
        return matchingDirectories;
    }

    /**
     * Appends a file separator, if string doesn't end one
     *
     * @param path - String to append to.
     */
    public static String endWithFileSeparator(String path){
        if (!path.endsWith(Constants.FILE_SEPARATOR)) {
            path += Constants.FILE_SEPARATOR;
        }
        return path;
    }

}

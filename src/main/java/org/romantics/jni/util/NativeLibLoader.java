/*--------------------------------------------------------------------------
 *  Copyright 2007 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/

package org.romantics.jni.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;


/**
 * Set the system properties, libraryBaseName.lib.path, appropriately so that the
 * SQLite JDBC driver can find *.dll, *.dylib and *.so files, according to the current OS (win,
 * linux, mac).
 *
 * <p>The library files are automatically extracted from this project's package (JAR).
 *
 * <p>usage: call {@link #initialize(String)} before using SQLite JDBC driver.
 *
 * @author leo
 */
public class NativeLibLoader {
    private static final Logger logger = LoggerFactory.getLogger(NativeLibLoader.class);

    private static final String LOCK_EXT = ".lck";
    private static Map<String, Boolean> extracted = new ConcurrentHashMap<String, Boolean>();

    /**
     * Loads SQLite native JDBC library.
     *
     * @return True if SQLite native library is successfully loaded; false otherwise.
     */
    public static synchronized boolean initialize(String nativeLibBaseName) throws Exception {
        // only cleanup before the first extract
        if (!extracted.getOrDefault(nativeLibBaseName, false)) {
            cleanup(nativeLibBaseName);
        }
        loadNativeLibrary(nativeLibBaseName);
        return extracted.getOrDefault(nativeLibBaseName, false);
    }

    private static File getTempDir(String nativeLibBaseName) {
        return new File(
                System.getProperty(nativeLibBaseName + ".lib.tmpdir", System.getProperty("java.io.tmpdir")));
    }

    /**
     * Deleted old native libraries e.g. on Windows the DLL file is not removed on VM-Exit (bug #80)
     */
    static void cleanup(String nativeLibBaseName) {
        String searchPattern = "library-" + getVersion();

        try (Stream<Path> dirList = Files.list(getTempDir(nativeLibBaseName).toPath())) {
            dirList.filter(
                            path ->
                                    !path.getFileName().toString().endsWith(LOCK_EXT)
                                            && path.getFileName()
                                            .toString()
                                            .startsWith(searchPattern))
                    .forEach(
                            nativeLib -> {
                                Path lckFile = Paths.get(nativeLib + LOCK_EXT);
                                if (Files.notExists(lckFile)) {
                                    try {
                                        Files.delete(nativeLib);
                                    } catch (Exception e) {
                                        logger.error("Failed to delete old native lib", e);
                                    }
                                }
                            });
        } catch (IOException e) {
            logger.error("Failed to open directory", e);
        }
    }

    /**
     * Checks if the SQLite JDBC driver is set to native mode.
     *
     * @return True if the SQLite JDBC driver is set to native Java mode; false otherwise.
     */
    public static boolean isNativeMode(String nativeLibBaseName) throws Exception {
        // load the driver
        initialize(nativeLibBaseName);
        return extracted.getOrDefault(nativeLibBaseName, false);
    }

    /**
     * Computes the MD5 value of the input stream.
     *
     * @param input InputStream.
     * @return Encrypted string for the InputStream.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static String md5sum(InputStream input) throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);

        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            DigestInputStream digestInputStream = new DigestInputStream(in, digest);
            for (; digestInputStream.read() >= 0; ) {
            }

            ByteArrayOutputStream md5out = new ByteArrayOutputStream();
            md5out.write(digest.digest());
            return md5out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available: " + e);
        } finally {
            in.close();
        }
    }

    private static boolean contentsEquals(InputStream in1, InputStream in2) throws IOException {
        if (!(in1 instanceof BufferedInputStream)) {
            in1 = new BufferedInputStream(in1);
        }
        if (!(in2 instanceof BufferedInputStream)) {
            in2 = new BufferedInputStream(in2);
        }

        int ch = in1.read();
        while (ch != -1) {
            int ch2 = in2.read();
            if (ch != ch2) {
                return false;
            }
            ch = in1.read();
        }
        int ch2 = in2.read();
        return ch2 == -1;
    }

    /**
     * Extracts and loads the specified library file to the target folder
     *
     * @param libFolderForCurrentOS Library path.
     * @param libraryFileName       Library name.
     * @param targetFolder          Target folder.
     * @return
     */
    private static boolean extractAndLoadLibraryFile(
            String libFolderForCurrentOS, String libraryFileName, String targetFolder)
            throws FileException {
        String nativeLibraryFilePath = libFolderForCurrentOS + "/" + libraryFileName;
        // Include architecture name in temporary filename in order to avoid conflicts
        // when multiple JVMs with different architectures running at the same time
        String uuid = UUID.randomUUID().toString();
        String extractedLibFileName =
                String.format("library-%s-%s-%s", getVersion(), uuid, libraryFileName);
        String extractedLckFileName = extractedLibFileName + LOCK_EXT;

        Path extractedLibFile = Paths.get(targetFolder, extractedLibFileName);
        Path extractedLckFile = Paths.get(targetFolder, extractedLckFileName);

        try {
            // Extract a native library file into the target directory
            try (InputStream reader = getResourceAsStream(nativeLibraryFilePath)) {
                if (Files.notExists(extractedLckFile)) {
                    Files.createFile(extractedLckFile);
                }
                Files.copy(reader, extractedLibFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                // Delete the extracted lib file on JVM exit.
                extractedLibFile.toFile().deleteOnExit();
                extractedLckFile.toFile().deleteOnExit();
            }

            // Set executable (x) flag to enable Java to load the native library
            extractedLibFile.toFile().setReadable(true);
            extractedLibFile.toFile().setWritable(true, true);
            extractedLibFile.toFile().setExecutable(true);

            // Check whether the contents are properly copied from the resource folder
            {
                try (InputStream nativeIn = getResourceAsStream(nativeLibraryFilePath);
                     InputStream extractedLibIn = Files.newInputStream(extractedLibFile)) {
                    if (!contentsEquals(nativeIn, extractedLibIn)) {
                        throw new FileException(
                                String.format(
                                        "Failed to write a native library file at %s",
                                        extractedLibFile));
                    }
                }
            }
            return loadNativeLibrary(targetFolder, extractedLibFileName);
        } catch (IOException e) {
            logger.error("Unexpected IOException", e);
            return false;
        }
    }

    // Replacement of java.lang.Class#getResourceAsStream(String) to disable sharing the resource
    // stream
    // in multiple class loaders and specifically to avoid
    // https://bugs.openjdk.java.net/browse/JDK-8205976
    private static InputStream getResourceAsStream(String name) {
        // Remove leading '/' since all our resource paths include a leading directory
        // See:
        // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Class.java#L3054
        String resolvedName = name.substring(1);
        ClassLoader cl = NativeLibLoader.class.getClassLoader();
        URL url = cl.getResource(resolvedName);
        if (url == null) {
            return null;
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (IOException e) {
            logger.error("Could not connect", e);
            return null;
        }
    }

    /**
     * Loads native library using the given path and name of the library.
     *
     * @param path Path of the native library.
     * @param name Name of the native library.
     * @return True for successfully loading; false otherwise.
     */
    private static boolean loadNativeLibrary(String path, String name) {
        File libPath = new File(path, name);
        if (libPath.exists()) {

            try {
                System.load(new File(path, name).getAbsolutePath());
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error(
                        "Failed to load native library: {}. osinfo: {}",
                        name,
                        OSInfo.getNativeLibFolderPathForCurrentOS(),
                        e);
                return false;
            }

        } else {
            return false;
        }
    }

    private static boolean loadNativeLibraryJdk(String nativeLibBaseName) {
        try {
            System.loadLibrary(nativeLibBaseName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            logger.error("Failed to load native library through System.loadLibrary", e);
            return false;
        }
    }

    /**
     * Loads SQLite native library using given path and name of the library.
     *
     * @throws
     */
    private static void loadNativeLibrary(String nativeLibBaseName) throws FileException {
        if (extracted.getOrDefault(nativeLibBaseName, false)) {
            return;
        }

        List<String> triedPaths = new LinkedList<>();

        // Try loading library from libraryBaseName.lib.path library path */
        String nativeLibPath = System.getProperty(nativeLibBaseName + ".lib.path");

        String nativeLibName = LibraryLoaderUtil.getNativeLibName(nativeLibBaseName);
        if (nativeLibPath != null) {
            if (loadNativeLibrary(nativeLibPath, nativeLibName)) {
                extracted.put(nativeLibBaseName, true);
                return;
            } else {
                triedPaths.add(nativeLibPath);
            }
        }

        // Load the os-dependent library from the jar file
        nativeLibPath = LibraryLoaderUtil.getNativeLibResourcePath();
        boolean hasNativeLib =
                LibraryLoaderUtil.hasNativeLib(nativeLibPath, nativeLibName);

        if (hasNativeLib) {
            // temporary library folder
            String tempFolder = getTempDir(nativeLibBaseName).getAbsolutePath();
            // Try extracting the library from jar
            if (extractAndLoadLibraryFile(
                    nativeLibPath, nativeLibName, tempFolder)) {
                extracted.put(nativeLibBaseName, true);
                return;
            } else {
                triedPaths.add(nativeLibPath);
            }
        }

        // As a last resort try from java.library.path
        String javaLibraryPath = System.getProperty("java.library.path", "");
        for (String ldPath : javaLibraryPath.split(File.pathSeparator)) {
            if (ldPath.isEmpty()) {
                continue;
            }
            if (loadNativeLibrary(ldPath, nativeLibName)) {
                extracted.put(nativeLibBaseName, true);
                return;
            } else {
                triedPaths.add(ldPath);
            }
        }

        // As an ultimate last resort, try loading through System.loadLibrary
        if (loadNativeLibraryJdk(nativeLibBaseName)) {
            extracted.put(nativeLibBaseName, true);
            return;
        }

        extracted.put(nativeLibBaseName, false);
        throw new NativeLibraryNotFoundException(
                String.format(
                        "No native library found for os.name=%s, os.arch=%s, paths=[%s]",
                        OSInfo.getOSName(),
                        OSInfo.getArchName(),
                        StringUtils.join(triedPaths, File.pathSeparator)));
    }

    @SuppressWarnings("unused")
    private static void getNativeLibraryFolderForTheCurrentOS() {
        String osName = OSInfo.getOSName();
        String archName = OSInfo.getArchName();
    }

    /**
     * @return The major version of the SQLite JDBC driver.
     */
    public static int getMajorVersion() {
        String[] c = getVersion().split("//.");
        return (c.length > 0) ? Integer.parseInt(c[0]) : 1;
    }

    /**
     * @return The minor version of the SQLite JDBC driver.
     */
    public static int getMinorVersion() {
        String[] c = getVersion().split("//.");
        return (c.length > 1) ? Integer.parseInt(c[1]) : 0;
    }

    /**
     * @return The version of the SQLite JDBC driver.
     */
    public static String getVersion() {
        return VersionHolder.VERSION;
    }

    /**
     * This class will load the version from resources during <clinit>. By initializing this at
     * build-time in native-image, the resources do not need to be included in the native
     * executable, and we're eliminating the IO operations as well.
     */
    public static final class VersionHolder {
        private static final String VERSION;

        static {
            URL versionFile =
                    VersionHolder.class.getResource(
                            "/META-INF/maven/org.romantics/jni/pom.properties");
            if (versionFile == null) {
                versionFile =
                        VersionHolder.class.getResource(
                                "/META-INF/maven/org.romantics/jni/VERSION");
            }

            String version = "unknown";
            try {
                if (versionFile != null) {
                    Properties versionData = new Properties();
                    versionData.load(versionFile.openStream());
                    version = versionData.getProperty("version", version);
                    version = version.trim().replaceAll("[^0-9//.]", "");
                }
            } catch (IOException e) {
                // inline creation of logger to avoid build-time initialization of the logging
                // framework in native-image
                LoggerFactory.getLogger(VersionHolder.class)
                        .error("Could not read version from file: {}", versionFile, e);
            }
            VERSION = version;
        }
    }
}

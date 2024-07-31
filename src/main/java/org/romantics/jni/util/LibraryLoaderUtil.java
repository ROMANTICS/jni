package org.romantics.jni.util;

public class LibraryLoaderUtil {

    /**
     * Get the OS-specific resource directory within the jar, where the relevant sqlitejdbc native
     * library is located.
     */
    public static String getNativeLibResourcePath() {
        String packagePath = LibraryLoaderUtil.class.getPackage().getName().replace(".", "/").replace("/util","");
        return String.format(
                "/%s/native/%s", packagePath, OSInfo.getNativeLibFolderPathForCurrentOS());
    }


    public static String getNativeLibName(String nativeLibBaseName) {
        return System.mapLibraryName(nativeLibBaseName);
    }

    public static boolean hasNativeLib(String path, String libraryName) {
        System.out.println(LibraryLoaderUtil.class.getResource(path + "/" + libraryName));
        return LibraryLoaderUtil.class.getResource(path + "/" + libraryName) != null;
    }
}

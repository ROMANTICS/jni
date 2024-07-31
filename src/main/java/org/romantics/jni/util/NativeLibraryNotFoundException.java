package org.romantics.jni.util;

public class NativeLibraryNotFoundException extends RuntimeException {
    public NativeLibraryNotFoundException(String message) {
        super(message);
    }
}

package org.romantics.jni;

import org.romantics.jni.util.NativeLibLoader;

import java.io.IOException;
import java.net.URISyntaxException;

public class Main {
    static {
        try {
            NativeLibLoader.initialize("math");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static native int add(int a, int b);

    static native int sub(int a, int b);

    public static void main(String[] args) throws IOException, URISyntaxException {

        System.out.println(add(1, 2));
        System.out.println(sub(2, 1));

    }


}
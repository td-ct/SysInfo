package com.pixcaelium.sysinfo;

/**
 * JNI-Bruecke zur echten C++-Camera2-NDK (ACameraManager).
 */
public final class NativeCameraProbe {
    static {
        System.loadLibrary("sysinfo_ndk_probe");
    }

    private NativeCameraProbe() {}

    public static native String nativeCameraReport();
}

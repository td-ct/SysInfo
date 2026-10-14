package com.pixcaelium.sysinfo;

import android.util.Log;

public final class LogUtil {
    public static final String TAG = "PixCaelium";
    private LogUtil() {}
    public static void w(String msg) { Log.w(TAG, msg); }
    public static void i(String msg) { Log.i(TAG, msg); }
    public static void e(String msg, Throwable t) { Log.e(TAG, msg, t); }
}

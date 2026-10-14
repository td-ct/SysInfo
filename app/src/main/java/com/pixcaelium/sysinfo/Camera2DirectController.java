package com.pixcaelium.sysinfo;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

/**
 * Alternative Kamera-Anbindung direkt ueber die Camera2-Java-API (ohne CameraX).
 * Dient dem API-Vergleich in der App: CameraX vs. Camera2-Java sehen dieselben
 * Kamera-IDs/Capabilities, da beide auf dem Camera-Service aufsetzen.
 */
public class Camera2DirectController {

    public interface ErrorListener {
        void onError(String message);
    }

    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private HandlerThread thread;
    private Handler handler;
    private final Activity activity;
    private final TextureView textureView;
    private final ErrorListener errorListener;
    private String activeCameraId;

    public Camera2DirectController(Activity activity, TextureView textureView,
                                   ErrorListener errorListener) {
        this.activity = activity;
        this.textureView = textureView;
        this.errorListener = errorListener;
    }

    public void open(int lensFacing) {
        close();
        CameraManager cm = activity.getSystemService(CameraManager.class);
        String id = findCamera(cm, lensFacing);
        if (id == null) {
            if (errorListener != null)
                errorListener.onError("Keine Kamera mit facing=" + lensFacing + " gefunden");
            return;
        }
        activeCameraId = id;
        thread = new HandlerThread("cam2-direct");
        thread.start();
        handler = new Handler(thread.getLooper());
        try {
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                }
                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (errorListener != null)
                        errorListener.onError("Camera2-Fehler " + error + " (ID " + id + ")");
                }
            }, handler);
        } catch (SecurityException e) {
            if (errorListener != null) errorListener.onError("Keine CAMERA-Berechtigung");
        } catch (Exception e) {
            if (errorListener != null) errorListener.onError("openCamera fehlgeschlagen: " + e);
        }
    }

    private void startPreview() {
        if (cameraDevice == null || textureView == null) return;
        SurfaceTexture st = textureView.getSurfaceTexture();
        if (st == null) return;
        st.setDefaultBufferSize(1280, 720);
        Surface surface = new Surface(st);
        try {
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                CaptureRequest.Builder req =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                req.addTarget(surface);
                                s.setRepeatingRequest(req.build(), null, handler);
                                Log.i(LogUtil.TAG, "Camera2-Direct-Preview aktiv (ID " + activeCameraId + ")");
                            } catch (Exception e) {
                                if (errorListener != null)
                                    errorListener.onError("Preview-Request fehlgeschlagen: " + e);
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            if (errorListener != null)
                                errorListener.onError("CaptureSession-Konfiguration fehlgeschlagen");
                        }
                    }, handler);
        } catch (Exception e) {
            if (errorListener != null) errorListener.onError("createCaptureSession: " + e);
        }
    }

    public String getActiveCameraId() {
        return activeCameraId;
    }

    private static String findCamera(CameraManager cm, int lensFacing) {
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer f = c.get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == lensFacing) return id;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void close() {
        try {
            if (session != null) { session.close(); session = null; }
            if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
            if (thread != null) { thread.quitSafely(); thread = null; handler = null; }
        } catch (Exception ignored) {
        }
    }
}

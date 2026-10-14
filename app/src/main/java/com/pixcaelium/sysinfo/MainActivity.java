package com.pixcaelium.sysinfo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PixCaelium";
    private static final int REQ_CAMERA = 100;

    private PreviewView previewView;
    private TextView diagText;
    private android.widget.ScrollView diagScroll;
    private SeekBar zoomBar;

    private Camera camera;
    private CameraControl control;
    private ImageCapture imageCapture;
    private float minZoom = 1f;
    private float maxZoom = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        diagText = findViewById(R.id.diagText);
        diagScroll = findViewById(R.id.diagScroll);
        zoomBar = findViewById(R.id.zoomBar);

        findViewById(R.id.btnDiagnose).setOnClickListener(v -> runDiagnosis());
        findViewById(R.id.btnWide).setOnClickListener(v -> switchToWide());
        findViewById(R.id.btnMain).setOnClickListener(v -> switchToMain());
        findViewById(R.id.btnCapture).setOnClickListener(v -> takePhoto());

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && control != null) {
                    float ratio = minZoom + (maxZoom - minZoom) * progress / 100f;
                    control.setZoomRatio(ratio);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void runDiagnosis() {
        StringBuilder sb = new StringBuilder();
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                int facing = c.get(CameraCharacteristics.LENS_FACING);
                int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                Range<Float> zoomRange = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                Integer cfaBox = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
                String msg = "ID=" + id
                        + " facing=" + facingName(facing)
                        + "\n  caps=" + Arrays.toString(caps)
                        + "\n  zoomRange=" + (zoomRange != null ? zoomRange.toString() : "n/a")
                        + "\n  physical=" + c.getPhysicalCameraIds()
                        + "\n  CFA=" + (cfaBox != null ? cfaName(cfaBox) : "n/a")
                        + "\n  focal=" + c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                Log.i(TAG, msg);
                sb.append(msg).append("\n\n");

                for (String physId : c.getPhysicalCameraIds()) {
                    try {
                        CameraCharacteristics pc = cm.getCameraCharacteristics(physId);
                        int[] pcaps = pc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                        Integer pcfa = pc.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
                        boolean depthCap = pcaps != null && contains(pcaps,
                                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);
                        String pmsg = "  -> Phys " + physId
                                + " caps=" + Arrays.toString(pcaps)
                                + " CFA=" + (pcfa != null ? cfaName(pcfa) : "n/a")
                                + (depthCap ? " [DEPTH_OUTPUT!]" : "");
                        Log.i(TAG, pmsg);
                        sb.append(pmsg).append("\n");
                    } catch (Exception e) {
                        Log.e(TAG, "Phys-ID " + physId + " nicht lesbar", e);
                    }
                }
            }
            diagText.setText(sb.toString());
            diagScroll.setVisibility(android.view.View.VISIBLE);
            Toast.makeText(this, "Diagnose fertig (siehe Panel + Logcat)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Diagnose fehlgeschlagen", e);
            Toast.makeText(this, "Diagnose fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean contains(int[] arr, int v) {
        if (arr == null) return false;
        for (int x : arr) if (x == v) return true;
        return false;
    }

    private static String facingName(int facing) {
        switch (facing) {
            case CameraCharacteristics.LENS_FACING_BACK: return "BACK";
            case CameraCharacteristics.LENS_FACING_FRONT: return "FRONT";
            case CameraCharacteristics.LENS_FACING_EXTERNAL: return "EXTERNAL";
            default: return "?" + facing;
        }
    }

    private static String cfaName(int cfa) {
        switch (cfa) {
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB: return "RGGB";
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG: return "GRBG";
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG: return "GBRG";
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR: return "BGGR";
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO: return "MONO(no Bayer)";
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR: return "NIR";
            default: return "?" + cfa;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture);
                control = camera.getCameraControl();

                readZoomRange();
                Log.i(TAG, "Kamera gestartet, zoomRange=[" + minZoom + "," + maxZoom + "]");
            } catch (Exception e) {
                Log.e(TAG, "Kamera-Start fehlgeschlagen", e);
                Toast.makeText(this, "Kamera-Start fehlgeschlagen", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void readZoomRange() {
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                if (ch.get(CameraCharacteristics.LENS_FACING) != null
                        && ch.get(CameraCharacteristics.LENS_FACING)
                            == CameraCharacteristics.LENS_FACING_BACK) {
                    Range<Float> zr = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (zr != null) {
                        minZoom = zr.getLower();
                        maxZoom = zr.getUpper();
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        minZoom = Math.min(minZoom, 1f);
        maxZoom = Math.max(maxZoom, 1f);
        zoomBar.setProgress((int) (100 * (1f - minZoom) / (maxZoom - minZoom)));
    }

    private void switchToWide() {
        if (control == null) {
            Toast.makeText(this, "Kamera nicht bereit", Toast.LENGTH_SHORT).show();
            return;
        }
        float target = Math.min(0.6f, minZoom);
        control.setZoomRatio(target);
        zoomBar.setProgress((int) (100 * (target - minZoom) / (maxZoom - minZoom)));
        Log.i(TAG, "Zoom gesetzt auf " + target);
    }

    private void switchToMain() {
        if (control == null) return;
        control.setZoomRatio(1.0f);
        zoomBar.setProgress((int) (100 * (1f - minZoom) / (maxZoom - minZoom)));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Kamera nicht bereit", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(getExternalFilesDir(null), "Bilder");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "pix_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults out) {
                Log.i(TAG, "Foto gespeichert: " + file.getAbsolutePath());
                Toast.makeText(MainActivity.this, "Gespeichert: " + file.getName(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(@NonNull ImageCaptureException e) {
                Log.e(TAG, "Foto fehlgeschlagen", e);
                Toast.makeText(MainActivity.this, "Foto fehlgeschlagen", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Kamera-Berechtigung nötig", Toast.LENGTH_LONG).show();
        }
    }
}

package com.pixcaelium.sysinfo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.TextureView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;

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
    private int currentFacing = CameraSelector.LENS_FACING_BACK;

    private static final int API_CAMERAX = 0;
    private static final int API_CAMERA2_JAVA = 1;
    private static final int API_NDK_INFO = 2;
    private int currentApi = API_CAMERAX;

    private Camera2DirectController camera2Controller;
    private TextureView cam2TextureView;

    private static final String UPDATE_REPO_API =
            "https://api.github.com/repos/td-ct/SysInfo/contents/releases?ref=main";
    private static final String UPDATE_REPO_DOWNLOAD =
            "https://github.com/td-ct/SysInfo/raw/main/releases/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        diagText = findViewById(R.id.diagText);
        diagScroll = findViewById(R.id.diagScroll);
        zoomBar = findViewById(R.id.zoomBar);
        cam2TextureView = findViewById(R.id.cam2TextureView);

        findViewById(R.id.btnMenu).setOnClickListener(v -> showMenu());

        findViewById(R.id.btnDiagnose).setOnClickListener(v -> runDiagnosis());
        findViewById(R.id.btnWide).setOnClickListener(v -> switchToWide());
        findViewById(R.id.btnMain).setOnClickListener(v -> switchToMain());
        findViewById(R.id.btnSelfie).setOnClickListener(v -> switchCamera());
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
                boolean depthCapTop = caps != null && contains(caps,
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);
                boolean logicalMulti = caps != null && contains(caps,
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
                String msg = "ID=" + id
                        + " facing=" + facingName(facing)
                        + "\n  caps=" + Arrays.toString(caps)
                        + (logicalMulti ? " [LOGICAL_MULTI_CAMERA]" : "")
                        + (depthCapTop ? " [DEPTH_OUTPUT]" : "")
                        + "\n  zoomRange=" + (zoomRange != null ? zoomRange.toString() : "n/a")
                        + "\n  physical=" + c.getPhysicalCameraIds()
                        + "\n  concurrent=" + cm.getConcurrentCameraIds()
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
        startCamera(currentFacing);
    }

    private void startCamera(int lensFacing) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture);
                control = camera.getCameraControl();

                readZoomRange(lensFacing);
                Log.i(TAG, "Kamera gestartet (facing=" + lensFacing
                        + "), zoomRange=[" + minZoom + "," + maxZoom + "]");
            } catch (Exception e) {
                Log.e(TAG, "Kamera-Start fehlgeschlagen", e);
                Toast.makeText(this, "Kamera-Start fehlgeschlagen", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void switchCamera() {
        currentFacing = (currentFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        applyApiChoice(currentApi, currentFacing);
    }

    // ---------- Menü (oben rechts): Über / API / Diagnose / Update ----------
    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, findViewById(R.id.btnMenu));
        popup.getMenu().add("Über");
        Menu apiMenu = popup.getMenu().addSubMenu("Kamera-API");
        MenuItem cx = apiMenu.add(0, API_CAMERAX, 0, "CameraX (Standard)");
        MenuItem c2 = apiMenu.add(0, API_CAMERA2_JAVA, 1, "Camera2 direkt (Java)");
        apiMenu.add(0, API_NDK_INFO, 2, "Native/NDK-Sicht (Info)");
        apiMenu.setGroupCheckable(0, true, true);
        switch (currentApi) {
            case API_CAMERA2_JAVA: c2.setChecked(true); break;
            case API_NDK_INFO: apiMenu.findItem(API_NDK_INFO).setChecked(true); break;
            default: cx.setChecked(true);
        }
        popup.getMenu().add("Update");
        popup.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Über".equals(title)) {
                startActivity(new Intent(this, SplashActivity.class));
                return true;
            }
            if ("Update".equals(title)) {
                checkForUpdate();
                return true;
            }
            if (item.getGroupId() == 0) {
                applyApiChoice(item.getItemId(), currentFacing);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void applyApiChoice(int api, int lensFacing) {
        currentApi = api;
        TextView badge = findViewById(R.id.apiBadge);
        boolean isCameraX = (api == API_CAMERAX);
        boolean isCamera2 = (api == API_CAMERA2_JAVA);

        previewView.setVisibility(isCameraX ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        if (cam2TextureView != null)
            cam2TextureView.setVisibility(isCamera2 ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        if (badge != null) {
            if (isCameraX) badge.setText("API: CameraX");
            else if (isCamera2) badge.setText("API: Camera2 (Java)");
            else badge.setText("API: Native/NDK (Info)");
        }

        if (api == API_NDK_INFO) {
            stopAllCameras();
            startActivity(new Intent(this, NdkInfoActivity.class));
            return;
        }
        if (isCamera2) {
            stopCameraX();
            if (camera2Controller == null)
                camera2Controller = new Camera2DirectController(this, cam2TextureView,
                        msg -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
            camera2Controller.open(lensFacing);
        } else {
            if (camera2Controller != null) camera2Controller.close();
            startCamera(lensFacing);
        }
    }

    private void stopAllCameras() {
        stopCameraX();
        if (camera2Controller != null) camera2Controller.close();
    }

    private void stopCameraX() {
        try {
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            if (future.isDone()) future.get().unbindAll();
        } catch (Exception ignored) {
        }
        control = null;
        imageCapture = null;
    }

    // ---------- Update-Pruefung (Logik wie TrailCam/Breakout) ----------
    private void checkForUpdate() {
        final String currentVersion;
        try {
            currentVersion = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            LogUtil.w("Versionsermittlung fehlgeschlagen: " + e);
            Toast.makeText(this, "Version konnte nicht ermittelt werden", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Prüfe auf Update...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String latestVersion = null;
            try {
                java.net.URL url = new java.net.URL(UPDATE_REPO_API);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    latestVersion = parseLatestApkVersion(sb.toString());
                }
                conn.disconnect();
            } catch (Exception e) {
                LogUtil.w("Update-Check fehlgeschlagen: " + e);
            }
            final String latest = latestVersion;
            runOnUiThread(() -> {
                if (latest == null) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update")
                            .setMessage("Es konnte keine Verbindung zu GitHub hergestellt werden. Bitte später erneut versuchen.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                if (compareVersions(latest, currentVersion) <= 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update")
                            .setMessage("Du nutzt bereits die neueste Version (v" + currentVersion + ").")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                if (isInstalledFromPlayStore()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update verfügbar")
                            .setMessage("Version v" + latest + " ist verfügbar (installiert: v"
                                    + currentVersion + "). Update im Play Store öffnen?")
                            .setPositiveButton("Play Store", (d, w) -> openPlayStore())
                            .setNegativeButton("Abbrechen", null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update verfügbar")
                        .setMessage("Version v" + latest + " ist verfügbar (installiert: v"
                                + currentVersion + "). Jetzt herunterladen und installieren?")
                        .setPositiveButton("Herunterladen", (d, w) ->
                                downloadAndInstallUpdate(latest))
                        .setNegativeButton("Abbrechen", null)
                        .show();
            });
        }, "update-check").start();
    }

    private void downloadAndInstallUpdate(final String latestVersion) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(UPDATE_REPO_DOWNLOAD
                            + "SysInfo-PixCaelium-v" + latestVersion + ".apk")));
            Toast.makeText(this, "Download im Browser gestartet. Nach dem Download APK öffnen und installieren.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            LogUtil.w("Download-Link konnte nicht geöffnet werden: " + e);
            Toast.makeText(this, "Download konnte nicht gestartet werden", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isInstalledFromPlayStore() {
        try {
            String installer;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                installer = getPackageManager().getInstallSourceInfo(getPackageName())
                        .getInstallingPackageName();
            } else {
                installer = getPackageManager().getInstallerPackageName(getPackageName());
            }
            if (installer == null) return false;
            return "com.android.vending".equals(installer)
                    || "com.google.android.feedback".equals(installer);
        } catch (Exception e) {
            LogUtil.w("Install-Quelle konnte nicht ermittelt werden: " + e);
            return false;
        }
    }

    private void openPlayStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id="
                                + getPackageName())));
            } catch (Exception e2) {
                LogUtil.w("Play Store konnte nicht geöffnet werden: " + e2);
                Toast.makeText(this, "Play Store konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int compareVersions(String a, String b) {
        if (a == null || b == null) return 0;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int majorA = 0, majorB = 0, minorA = 0, minorB = 0;
        try {
            majorA = pa.length > 0 ? Integer.parseInt(pa[0]) : 0;
            minorA = pa.length > 1 ? Integer.parseInt(pa[1]) : 0;
        } catch (NumberFormatException ignored) {
        }
        try {
            majorB = pb.length > 0 ? Integer.parseInt(pb[0]) : 0;
            minorB = pb.length > 1 ? Integer.parseInt(pb[1]) : 0;
        } catch (NumberFormatException ignored) {
        }
        if (majorA != majorB) return Integer.compare(majorA, majorB);
        return Integer.compare(minorA, minorB);
    }

    private static String parseLatestApkVersion(String json) {
        if (json == null || json.isEmpty()) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "SysInfo-PixCaelium-v(\\d+\\.\\d+)\\.apk");
        java.util.regex.Matcher m = p.matcher(json);
        String bestVersion = null;
        while (m.find()) {
            String v = m.group(1);
            if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                bestVersion = v;
            }
        }
        return bestVersion;
    }

    private void readZoomRange(int lensFacing) {
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer f = ch.get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == lensFacing) {
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
        if (minZoom >= 1.0f) {
            Toast.makeText(this, "Kein Weitwinkel verfügbar: minZoom=1.0 — "
                    + "Motorola exposes den Ultraweitwinkel nicht an Dritt-Apps "
                    + "(weder Zoom<1.0 noch physische Kamera-ID)", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Weitwinkel nicht möglich: CONTROL_ZOOM_RATIO_RANGE beginnt bei " + minZoom);
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

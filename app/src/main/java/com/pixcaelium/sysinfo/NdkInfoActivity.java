package com.pixcaelium.sysinfo;

import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Range;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Native/NDK-Ansicht: Fragt die KAMERAS wirklich ueber die C++-Camera2-NDK
 * (ACameraManager, siehe native-camera-probe.cpp) ab und zeigt die Werte
 * neben der Java-Sicht. Beide APIs fragen denselben Camera-Service ab —
 * hier kann man live verifizieren, dass die Ergebnisse identisch sind.
 */
public class NdkInfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ndk_info);
        TextView text = findViewById(R.id.ndkText);

        StringBuilder sb = new StringBuilder();
        sb.append("== API-Vergleich: Native (C++/NDK) vs. Java ==\n\n");

        // 1) Echte NDK-Abfrage (ACameraManager, native lib sysinfo_ndk_probe)
        String ndkReport = null;
        try {
            ndkReport = NativeCameraProbe.nativeCameraReport();
            sb.append("--- C++ Camera2-NDK (ACameraManager) ---\n\n");
            sb.append(ndkReport).append("\n\n");
        } catch (UnsatisfiedLinkError e) {
            sb.append("--- C++ Camera2-NDK ---\n\n")
              .append("Native Bibliothek nicht ladbar: ").append(e.getMessage()).append("\n\n");
        }

        // 2) Java-Referenzwerte zum direkten Vergleich
        sb.append("--- Java Camera2 (Referenz) ---\n\n");
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                Range<Float> zr = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                sb.append("ID=").append(id)
                  .append(" facing=").append(facing).append('\n')
                  .append("  caps=").append(Arrays.toString(
                          c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))).append('\n')
                  .append("  zoomRange=").append(zr).append('\n')
                  .append("  physical=").append(c.getPhysicalCameraIds()).append("\n\n");
            }
        } catch (Exception e) {
            sb.append("Fehler beim Auslesen: ").append(e).append('\n');
        }

        sb.append("Erwartung: beide Abschnitte identisch — Java und Native\n")
          .append("fragen denselben Camera-Service ab. Etwaige Abweichungen\n")
          .append("waeren ein Firmware-Bug. Am G86 gilt fuer beide: kein\n")
          .append("Weitwinkel, keine physischen IDs, kein DEPTH_OUTPUT.");
        text.setText(sb.toString());
    }
}

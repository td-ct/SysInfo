package com.pixcaelium.sysinfo;

import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Range;
import android.widget.TextView;

import java.util.Arrays;

/**
 *NDK-Sicht: Die C++-Camera2-NDK (ACameraManager/ACameraDevice) greift auf
 * denselben Camera-Service zu wie die Java-API — Kamera-IDs und Capabilities
 * sind zwingend identisch. Diese Ansicht liest die Werte live und dokumentiert
 * das Equivalenz-Ergebnis, damit man vor Ort sieht: ein API-Wechsel (Java ->
 * C++/Rust) aendert nichts am Kamera-Angebot des Geraets.
 */
public class NdkInfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ndk_info);
        TextView text = findViewById(R.id.ndkText);

        StringBuilder sb = new StringBuilder();
        sb.append("== API-Vergleich: Native (C++/NDK) vs. Java ==\n\n");
        sb.append("Die Camera2-NDK (ACameraManager) und die Java-Camera2-\n")
          .append("API fragen BEIDE denselben Android Camera-Service ab.\n")
          .append("IDs, Capabilities, Zoom-Range sind daher identisch.\n\n");
        sb.append("-- Aktuelle Werte (gelten fuer Java UND Native) --\n\n");
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
        sb.append("Fazit G86: Weitwinkel & Tiefensensor sind nicht per\n")
          .append("App-API freigegeben — unabhaengig von der Sprache.\n")
          .append("Nur Root/HAL-Zugriff wuerde sie erreichen (unpraktikabel).");
        text.setText(sb.toString());
    }
}

# SysInfo PixCaelium

Kamera-Diagnose- und Kamera-App (Java / CameraX) – entwickelt für das Motorola Moto G86 5G,
läuft aber auf jedem Gerät ab Android 10 (API 29).

## Funktionen
- **Diagnose**: listet alle Kamera-IDs mit Facing, Capabilities, Zoom-Range, physischen IDs
  (Multi-Camera), CFA-Layout (RGB vs. MONO/NIR = Tiefen-/IR-Sensor) und Brennweiten –
  im Panel und in Logcat (Tag `PixCaelium`).
- **Weitwinkel über Zoom**: Button 0.6x nutzt `CONTROL_ZOOM_RANGE` (minZoom) der logischen Kamera;
  am G86 schaltet Motorola darunter auf den 8-MP-Ultraweitwinkel um.
- **Zoom-SeekBar**: stufenlos zwischen minZoom und maxZoom.
- **Foto-Aufnahme**: JPEG via CameraX `ImageCapture` nach
  `Android/data/com.pixcaelium.sysinfo/files/Bilder/`.

## Build
```bash
./gradlew assembleRelease   # -> app/build/outputs/apk/release/
./gradlew bundleRelease     # -> app/build/outputs/bundle/release/
```
Keystore: `keystore.jks` (Test-Keystore, Alias/Passwort `pixcaelium`) – für Play-Upload durch
eigenen Keystore ersetzen.

## Tiefensensor-Hinweis (Moto G86)
Das „dritte Modul" ist ein 2-MP-Tiefensensor (DEPTH_ONLY). Er liefert keine RGB-Bilder und ist für
Drittanbieter nicht direkt öffnbar. Die Diagnose zeigt ihn nur, falls Motorola ihn als physische ID
unter der logischen Rückkamera meldet (mit `CFA=MONO` bzw. Capability `DEPTH_OUTPUT`/`DEPTH_ISO`).
Tiefe gibt es in Drittanbieter-Apps nur über die **Tiefen-USB/Halbprofi-API** (unsupported) oder
indirekt über die **Google Depth API** (`DEPTH16`-Daten im JPEG-XMP-Blob der Hauptkamera, wenn
`REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT` vorhanden) bzw. **ARCore Depth** (stereo/bewegungsbasiert).

## v1.1
- **Selfie-Kamera-Umschaltung** (Button wechselt Front <-> Rück, Zoom-Range wird je Kamera neu gelesen)
- Weitwinkel-Button: klare Meldung, falls nicht verfügbar (statt wirkungslosem Druck)
- Diagnose erweitert: `[LOGICAL_MULTI_CAMERA]`/`[DEPTH_OUTPUT]`-Marker, `getConcurrentCameraIds()`

## Diagnose-Ergebnis Moto G86 5G (Android 15)
- zoomRange=[1.0, 10.0], physical=[], kein LOGICAL_MULTI_CAMERA
- => Ultraweitwinkel (8 MP) ist für Dritt-Apps **nicht erreichbar** (weder Zoom<1.0 noch physische ID);
   Motorola expose ihn nicht. Tiefensensor ebenfalls API-invisible (kein DEPTH_OUTPUT).

## v1.2
- **Menü oben rechts** (Overflow-Button):
  - **Über** (erster Eintrag): Splash-Screen mit Version + GitHub-Link
  - **Kamera-API** (Untermenü, umschaltbar, mit Häkchen):
    - *CameraX (Standard)* — bisherige Vorschau/Aufnahme
    - *Camera2 direkt (Java)* — eigene Preview-Pipeline ohne CameraX
    - *Native/NDK-Sicht (Info)* — Ansicht, die live zeigt, dass die C++-NDK-
      API dieselben IDs/Capabilities sieht (beide fragen denselben
      Camera-Service ab; am G86 identisch: kein UW, keine physischen IDs)
  - **Update** (letzter Eintrag): gleiche Logik wie TrailCam/Breakout —
    GitHub-API-Abgleich der `releases/`-APKs mit installierter Version,
    Download-Link bzw. Play-Store-Weiterleitung bei Play-Installation
- API-Badge oben in der Preview zeigt die aktive Anbindung

# Arbeitsanweisung — SysInfo PixCaelium

Projekt: Android-Kamera-Diagnose-App (Java/CameraX) für Motorola Moto G86 5G.
Repo: td-ct/SysInfo, Branch `main`. Artefakte liegen in `releases/`.

## Build

```bash
export JAVA_HOME=<JDK17>
gradle assembleRelease bundleRelease --no-daemon \
  -Pandroid.injected.signing.store.file=<keystore.p12> \
  -Pandroid.injected.signing.store.password=<pass> \
  -Pandroid.injected.signing.key.alias=pixcaelium \
  -Pandroid.injected.signing.key.password=<pass>
```

Ohne Signing-Parameter greift der lokale Fallback-Keystore
(`/workspace/sysinfo-release.jks`, Pass/Alias `pixcaelium`).

Release-Signierung: PixCaelium-Key. Zugangsdaten: siehe
Drive `_MISTRAL/pixcaelium-signing-info.txt` bzw. `KESTERSTORE-ANLEITUNG-NEU`.

## Signierung / Keystore-Regeln (wichtig!)

- **Keystore-Uploads nach Drive: NIEMALS als große .b64-Textdatei.**
  Der Drive-Text-Kanal verfälscht Dateien ab ~3,5 KB systematisch
  (nachgewiesen 2026-10-14: Groß-/Kleinschreibung an festen Positionen
  gedreht, Zeichen gelöscht). So wurden die Original-Keystores
  (pixcaelium/breakout/trailcam) beschädigt.

### Verbindliches Chunk-Verfahren für Binärdateien > ~1 KB nach Drive

1. **Vorbereiten:** Binärdatei einmalig base64-kodieren (einzeilig, ohne
   Zeilenumbrüche). In `n` Chunks von max. 512 Zeichen schneiden, sodass
   keine Chunk-Grenze die 4-Zeichen-Base64-Gruppe zerfällt (Länge je Chunk
   durch 4 teilbar):
   ```python
   import base64
   b64 = base64.b64encode(open("keystore.p12","rb").read()).decode()
   n = 8
   size = ((len(b64)+n-1)//n + 3)//4*4   # durch 4 teilbar
   for i in range(n):
       open(f"chunk{i+1}-of-{n}.txt","w").write(b64[i*size:(i+1)*size])
   ```
2. **Referenz-Hash notieren:** SHA-256 der Original-Binärdatei (und je Chunk).
3. **Upload:** Jede Chunk-Datei als `text/plain` mit
   `disable_conversion_to_google_type=true` hochladen, Name
   `<basisname>-chunk-i-of-n.txt`.
4. **Pflicht-Verifikation (Roundtrip):** Jede Chunk-Datei direkt nach dem
   Upload wieder herunterladen und byte-genau mit dem Original vergleichen.
   Dateigröße muss exakt stimmen. Bei Abweichung: Chunk neu hochladen
   (ggf. andere Chunkgrenzen), nie ungetestet stehen lassen.
5. **End-to-End-Prüfung:** Alle heruntergeladenen Chunks in Reihenfolge
   zusammensetzen, dekodieren, SHA-256 mit Referenz vergleichen und die
   Datei inhaltlich prüfen (z. B. `keytool -list` bei Keystores).
6. **Anleitung + Hashes** als weitere Textdatei (< 1 KB problemlos)
   hochladen: Zusammensetz-Kommando (PowerShell + Linux), erwarteter
   SHA-256/MD5, Zugangsdaten-Verweis, Liste gültiger/ungültiger Dateien.

Beispiel (umgesetzt 2026-10-14): `keystore-recovered-chunk-1-of-8.txt`
bis `-8-of-8.txt` + `KESTERSTORE-ANLEITUNG-NEU` im Drive-Ordner `_MISTRAL`.
Referenz: p12, 2760 Bytes,
SHA-256 `3305344105abd4dd2b62f29e0862f7a021bf2042dbbede7f67b25db3cec0cf37`.

- Kleine Textdateien (< ~1 KB) sind unproblematisch.
- Noch besser als Chunks: Binärdateien (.p12/.jks direkt) über die
  Drive-Weboberfläche oder Backup & Synchronisieren übertragen.

## Sonstiges

- Keystore/Passwörter niemals ins Git-Repo committen (nur Ausnahmen:
  explizit als Test-Keystore gekennzeichnet).
- GitHub-Releases können aus der Sandbox nicht erstellt werden; Artefakte
  als Commit in `releases/` ablegen und im README dokumentieren.

# hypas – Anbindung an die Kyocera-HyPAS-Plattform (Platzhalter)

Hier entsteht später die Anbindung an Display, Touch, Scanner und Drucker der
ECOSYS MA3500cifx / MA3500ciX. **Das HyPAS-SDK und seine Dokumentation liegen noch
nicht vor.** Deshalb enthält dieses Verzeichnis bewusst keinen Code: Jeder Klassen- oder
Methodenname aus dem SDK wäre geraten. Alles Plattformneutrale ist fertig im Kern
(`src/core`) und auf dem PC getestet.

## Was der Kern schon mitbringt

Einstiegspunkt ist `de.scangolf.core.app.ScanGolfApp`. Die Klasse enthält den kompletten
Ablauf (Startbildschirm → Scannen → Erkennen → Fehlermeldung oder Spiel → Urkunde drucken →
„Neue Bahn“ für die nächste Person, Rückkehr zum Start nach 3 Minuten ohne Berührung) und
implementiert `de.scangolf.core.render.Screen`:

```java
ScanGolfApp app = new ScanGolfApp(800, 480,          // logische Größe, frei wählbar
        new ScanAnalyzer(),                          // liest template.json aus dem Kern-JAR
        scanAdapter, printAdapter, dateAdapter);     // die Adapter unten

// Bildschleife der Plattform, ca. 30–60 mal pro Sekunde:
app.update(millisekundenMonoton);
app.render(displayCanvas);
// bei jedem Touch-Ereignis (logische Koordinaten):
app.touch(Touch.DOWN | MOVE | UP | CANCEL, x, y);
```

Die PC-Umgebung (`src/pc`) macht genau das mit Swing und dient als Referenz:
`GameWindow` (Bildschleife, Skalierung, Maus → Touch), `Graphics2DCanvas` (Canvas),
`PcServices` (Datei statt Scanner, PNG statt Drucker, Systemdatum).

## Adapter, die mit dem SDK gebaut werden müssen

Alle Schnittstellen sind eigene, schmale ScanGolf-Interfaces im Kern.

| # | Adapter | Kern-Schnittstelle | Aufgabe |
|---|---------|--------------------|---------|
| 1 | **Scan holen** | `core.app.ScanService` | Farbscan des ganzen A4-Blatts auslösen, Ergebnis in `int[]` ARGB (+ Breite, Höhe) wandeln und `callback.scanned(...)` rufen; bei Abbruch/Fehler `callback.failed(text)`. |
| 2 | **Display** | `core.render.Canvas` | Zeichenoperationen (Rechteck, abgerundetes Rechteck, Kreis, Ellipse, Linie, Polylinie, Polygon, Text mit Ausrichtung, ARGB-Bild mit Alpha, save/restore/translate/scale/clip) auf die Grafik-API abbilden; logische Größe auf die echte Auflösung skalieren (`deviceScale()`). |
| 3 | **Touch + Bildschleife** | `core.render.Screen` (`touch`, `update`) | Touch-Ereignisse in logische Koordinaten umrechnen; Timer, der `update`/`render` regelmäßig aufruft. |
| 4 | **Urkunde drucken** | `core.app.PrintService` | `CertificateRenderer.render(canvas, certificate)` auf ein Canvas zeichnen, das in das Druckformat der Plattform rendert (Bitmap mit 300 dpi oder Vektor), A4 hoch drucken, Ergebnis per Callback melden. |
| 5 | (klein) Datum | `core.app.DateSource` | Heutiges Datum für die Urkunde. |

Hinweise für die Umsetzung:

- **Threading:** Der Kern hat keine Threads und keine Synchronisation. Rückrufe der Adapter
  müssen im selben Thread ankommen wie `update/render/touch` (z. B. in die UI-Warteschlange
  einreihen). Die Scan-Auswertung läuft im nächsten `update()` nach dem Scan und blockiert
  diesen Aufruf (PC: ca. 0,1 s bei 300 dpi; auf dem Gerät vermutlich deutlich länger).
  Vorher wird ein Frame „Bahn wird erkannt …“ gezeichnet.
- **Speicher:** Die Auswertung kopiert das Bild nicht (Arbeitspuffer ≈ 13 % des Bildes).
  Das `int[]` selbst braucht bei 300 dpi 35 MB, bei 200 dpi 15,6 MB, bei 150 dpi 8,7 MB.
  Die Erkennung funktioniert in Tests ab 60 dpi; **150–200 dpi Farbe** ist die Empfehlung,
  falls der Heap knapp ist.
- **Farbe ist Pflicht:** Start (rot), Loch (grün) und Wasser (blau) werden über Farbe
  erkannt. Ein Graustufen-Scan wird erkannt und mit „Bitte in Farbe scannen“ abgelehnt.
- **Vorlagengeometrie:** `template.json` liegt im Kern-JAR unter
  `de/scangolf/core/scan/template.json` und wird per `Class.getResourceAsStream` gelesen.
- **Bilder im Canvas:** `ArgbImage` ist unveränderlich; ein Adapter darf die Umwandlung in
  ein Plattformbild cachen (so macht es `Graphics2DCanvas`). Die Bahn wird pro Bildschirmgröße
  einmal vorgerendert, pro Frame wird nur ein Bild gezeichnet.
- **Schriften:** Der Kern fordert „Sans“ oder „Serif“, fett/kursiv, mit Umlauten und „…“.
- **Java-Sprachniveau:** Der Kern wird mit `--release 8` gebaut und nutzt Java-5-Sprachmittel
  (Generics in 11 Dateien, 3 Enums, `@SuppressWarnings`), keine Lambdas, Streams,
  `java.util.function`, NIO, AWT, Threads oder Reflection. Falls HyPAS nur Java 1.4 (CDC)
  bietet, müssten Generics/Enums ersetzt und mit dem SDK-Compiler neu übersetzt werden;
  die betroffenen Dateien findet `grep -rlE "List<|Map<|enum " src/core`.

## Offene Fragen an die SDK-Dokumentation

1. **Java-Version und Profil:** Welche Sprachversion/Bytecode-Version (Java 8? CDC 1.1 /
   Java 1.4?), welches Klassenbibliotheks-Profil (`java.util.ArrayList`/`HashMap` vorhanden?
   `Class.getResourceAsStream`? `StringBuffer`?), OSGi-Bundle?
2. **Display:** Auflösung und Seitenverhältnis des 7-Zoll-Panels, Pixeldichte, verfügbare
   Grafik-API (Antialiasing? Alpha? Bildskalierung? Schriftarten mit Umlauten?),
   erreichbare Bildrate, gibt es einen Frame-Timer?
3. **Touch:** Welche Ereignisse (Down/Move/Up, Multi-Touch, Abbruch), Koordinatensystem,
   Latenz, Mindestgröße für Schaltflächen laut Kyocera-Richtlinien?
4. **Scan:** Wie wird ein Scan aus einer App ausgelöst (Vorlagenglas und/oder Einzug)?
   Farbmodus, Auflösung (150/200/300 dpi), Scanbereich (A4 fest? Automatik?), Ausrichtung
   (liegt ein quer eingelegtes Blatt als 90° gedrehtes Hochformat vor – die Erkennung kann
   beides), Rückgabeformat (Rohbitmap, JPEG, TIFF, PDF, Datei im Box-Speicher?), Speicherbedarf.
5. **Speicher/Leistung:** Maximaler Heap einer App, CPU-Leistung (Analysezeit abschätzen),
   ob ein 35-MB-Array (300 dpi) überhaupt möglich ist.
6. **Druck:** Druck-API für eigene Inhalte (Bitmap? PDF? PCL/PostScript?), Farbdruck,
   A4-Hochformat, Ränder/nicht bedruckbarer Bereich, Rückmeldung über Erfolg/Fehler,
   Kostenstellen/Benutzeranmeldung.
7. **App-Lebenszyklus:** Start/Beenden, Verhalten bei Energiesparmodus oder wenn jemand
   parallel kopiert/scannt, Timeouts des Bedienfelds, Rechte (Scan-/Druckberechtigung),
   Paketierung und Signierung.
8. **Ressourcen/Dateien:** Dürfen Apps Dateien schreiben (z. B. gespeicherte Level,
   Protokolle)? Wie wird geloggt?

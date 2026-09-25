# ScanGolf

Minigolf zum Selbstzeichnen für den Kyocera-Multifunktionsdrucker ECOSYS MA3500cifx / MA3500ciX:

1. Auf der A4-Vorlage eine Bahn zeichnen – Wände schwarz, Start rot, Loch grün, optional
   Wasser blau, dazu Name, Bahnname und ein Par-Kreuz.
2. Blatt scannen.
3. Die App erkennt daraus ein Level; gespielt wird mit dem Finger auf dem 7-Zoll-Display.
4. Am Ende druckt der Drucker eine Urkunde mit Name, Bahn, Ballspur und Ergebnis.

Dieses Repository enthält den **plattformunabhängigen Kern** (Scan-Auswertung, Level, Spiel,
Darstellung, Urkunde, App-Ablauf) und eine **PC-Testumgebung**. Die Anbindung an HyPAS
(Kyoceras Java-App-Plattform) folgt, sobald SDK und Doku vorliegen – siehe
[`src/hypas/README.md`](src/hypas/README.md).

> **Hinweis zur Vorlage:** Die ursprünglich vorhandenen Dateien (`vorlage/generate.py`,
> `template.json`, PDFs, Beispiel-Scan) lagen in dieser Arbeitsumgebung nicht vor. Die Vorlage
> wurde deshalb nach der Aufgabenbeschreibung neu aufgebaut (A4 quer, drei QR-artige
> Suchmuster 7×7 Module + voller Block unten rechts, Spielfeld 240 × 144 mm mit hellgrauem
> Rahmen und Punktraster, Namens- und Bahnfeld, Par-Kästchen 2–5, Farblegende). Alle Maße
> kommen ausschließlich aus `vorlage/generate.py` → `vorlage/out/template.json`; der Kern
> liest diese Datei. Soll die Originalvorlage verwendet werden, muss ihr `template.json`
> dieselben Schlüssel liefern (siehe unten) oder `SheetTemplate` angepasst werden.

## Voraussetzungen

- JDK ≥ 11 (getestet mit OpenJDK 21). Der Kern wird mit `--release 8` gebaut.
- Python 3 mit `pycairo` und `Pillow` (Vorlage und Testbilder), `pdftoppm` (poppler-utils).
- Optional `xvfb-run`: Dann läuft der Test des echten Swing-Fensters auch ohne Bildschirm;
  ohne Display und ohne Xvfb wird er als **SKIP** gezählt (nicht als bestanden).
- Keine externen Java-Bibliotheken, kein Gradle/Maven, kein Internet nötig.

## Bauen, testen, starten

```sh
./build.sh test                     # kompilieren, Testbilder erzeugen (falls nötig), alle Tests
./build.sh run testbilder/out/beispiel-300dpi.png   # scannen + spielen im Fenster (800x480, skalierbar)
./build.sh run                      # Startbildschirm; "Scannen" öffnet eine Dateiauswahl
./build.sh run test/levels/gewunden.json --size 1024x600   # gespeichertes Level, andere logische Größe
./build.sh analyze scan.png [ordner]    # Ergebnis ausgeben, scan.debug.png + scan.level.json schreiben
./build.sh batch ordner/            # alle Bilder eines Ordners auswerten (Tabelle + Debug-Bilder)
./build.sh visual                   # Kontrollbilder: Scan-Debug, Spiel-Screenshots, Urkunden -> build/visual
./build.sh grenzen                  # härtere Varianten erzeugen und auswerten (Grenzen der Erkennung)
./build.sh template | testimages    # Vorlage bzw. Testbilder neu erzeugen
./build.sh jar | clean | all        # all = clean + test + jar
./build.sh deb                      # build/deb/scangolf_0.1.0_all.deb (Debian/Ubuntu, Befehl "scangolf")
./build.sh dist                     # build/dist: scangolf-pc.zip (JARs, Startskripte, Vorlage, Level)
                                    #             + scangolf-quellcode.zip
```

Im Fenster ist die Maus der Finger: Kugel anklicken, nach hinten ziehen, loslassen.
„Urkunde drucken“ speichert die Urkunde als PNG mit 300 dpi unter `urkunden/`.

## Architektur

```
src/core/de/scangolf/core/   plattformneutral (Regeln unten), wird separat kompiliert
  scan/    Scan-Auswertung: Suchmuster, Lage, Papierweiß, Raster, Aufräumen, Prüfungen, Par,
           Namens-/Bahnausschnitte, Debug-Bild; SheetTemplate liest template.json
  level/   unveränderliches Level, Zelltypen, Regeln/Maße, Geometrie/Erreichbarkeit, JSON-Format
  game/    Physik (120 Hz, fest), Spielregeln, Ergebnisnamen, Schlagspuren, Schrittuhr
  render/  Canvas-Interface, Touch, Level-Rasterizer, GameScreen, MessageScreen, Farben
  cert/    Urkunde (A4 hoch, in mm) über dasselbe Canvas
  app/     ScanGolfApp (gesamter Ablauf) + eigene Adapter-Schnittstellen
           ScanService, PrintService, DateSource
  util/    ArgbImage, Json, Base64, FloatList
src/pc/de/scangolf/pc/       Bild laden (ImageIO), Graphics2DCanvas, Swing-Fenster, CLI, PNG-Export
src/hypas/                   nur README: was dort mit dem SDK andockt, offene Fragen
test/de/scangolf/test/       eigener Test-Runner + Tests; test/levels/ feste Level
vorlage/                     generate.py (pycairo) -> out/: template.json, PDFs, Beispiel-Scan
testbilder/                  generate_testbilder.py (Pflichtfälle), generate_grenzen.py (Grenzen)
```

Datenfluss: Scan (`int[]` ARGB) → `ScanAnalyzer` → `ScanResult` (Level **oder** Fehlerliste,
plus Warnungen und Diagnose) → `GameScreen`/`Game` → `Certificate` → `CertificateRenderer`.
`ScanGolfApp` verbindet das mit den Plattform-Adaptern.

### Kern-Regeln (werden automatisch geprüft)

- Kompiliert mit `javac --release 8 -Xlint:all -Xlint:-options -Werror`.
  `-Xlint:-options` ist nötig, weil neuere JDKs „source/target 8 is obsolete“ warnen und
  `-Werror` sonst immer scheitert.
- Zweiter Durchlauf mit `--limit-modules java.base`: Ein versehentlicher Import von AWT,
  Swing oder ImageIO im Kern ist ein Kompilierfehler (nicht erst eine Warnung).
- `build.sh` und `CoreRulesTest` suchen im Kern nach verbotenen Konstrukten: `java.awt`,
  `javax.`, `java.nio`, `java.util.function`, Streams, `->`, `::`, `Optional`, `String.join`,
  `var`, Records, Threads, `synchronized`, Reflection, `java.time`; zusätzlich (für ältere
  Embedded-Java) `StringBuilder`, `String.format`, `Arrays.copyOf`, Diamond `<>`,
  try-with-resources, `@Override`, `System.nanoTime`, `Math.hypot`.
- Bilder im Kern sind `int[]` ARGB + Breite + Höhe (`ArgbImage`). Der Scan wird nicht kopiert.

### Vorlage und `template.json`

Alle Maße in mm, Ursprung oben links auf der Seite (297 × 210). Schlüssel: `page`, `marks`
(`module`, `tl`/`tr`/`bl` Suchmuster, `br` voller Block, je `cx`, `cy`, `size`), `field`
(x 28,5 / y 34 / 240 × 144, Rahmen 0,6 mm in 78 % Grau, Punktraster 10 mm in 80 % Grau),
`name_box`, `lane_box`, `par_boxes` (Par 2–5, je mit `inset`), `par_default`, `color_ref`
(grüner Kreis im Logo als Farbkontrollfeld). Die PDFs sind bytegleich reproduzierbar.

## Wie die Scan-Auswertung arbeitet

1. **Arbeitsbild:** Graustufen, per Box-Filter so verkleinert, dass ein Modul der
   Suchmuster ~6–9 Pixel hat (300 dpi → Faktor 2); danach lokal auf Papierweiß normiert
   (gleicht Schatten/Verläufe aus). Das Originalbild wird nicht kopiert.
2. **Suchmuster:** zeilenweise Lauflängen 1:1:3:1:1, senkrecht und nochmals waagerecht
   bestätigt, benachbarte Treffer zusammengefasst. Das Verhältnis gilt für jede Drehung.
3. **Lage:** Aus allen Dreiergruppen wird die gewählt, die zur Vorlage passt (rechter Winkel
   bei tl, Seitenverhältnis 268,5 : 181,5 mm, Maßstab aus Modulgröße). Das unterscheidet
   tr von bl bei jeder Drehung (auch 90°/180°/270°); eine gespiegelte Anordnung wird
   abgelehnt. Affine Abbildung mm → Pixel aus den drei Mittelpunkten.
4. **Kontrollblock** unten rechts an der vorhergesagten Stelle: fehlt er → „Ecke fehlt“,
   liegt er daneben (> 2,5 mm) oder ist zu groß → „Blatt nicht erkannt“.
5. **Papierweiß** je 12-mm-Kachel aus den hellen, unbunten Proben (größtes Weiß der
   5×5-Nachbarschaft, damit große Teiche nicht als Papier gelten).
6. **Raster** 240 × 144 Zellen à 1 mm, je Zelle 4 × 4 Proben (Mittelwert über ~0,25 mm).
   Jede Probe relativ zum Papierweiß: Wand = dunkel (< 60 %) und unbunt; Rot/Grün/Blau über
   Farbton bei ausreichender Sättigung; dunkles Blau (Kugelschreiber) zählt als Wand.
   Die 2 Zellen am Rahmen werden ignoriert, der Feldrand ist im Spiel immer Wand.
7. **Aufräumen:** Komponenten unter 4 Zellen weg, Wasser schließen und nur umrandete
   Teiche füllen, Wandlücken bis 4 mm schließen (Rand zählt als Wand).
8. **Start/Loch:** rote bzw. grüne Flecken im Umkreis von 3 mm zu Gruppen zusammenfassen,
   Gruppen ≥ 6 mm² zählen; keine → Fehler, mehrere → Fehler; Mittelpunkt = Schwerpunkt.
9. **Prüfungen:** Start/Loch nicht in Wand oder Wasser; Start zu dicht an der Wand → bis
   5 mm verschieben (Warnung); Loch per Flood-Fill über Zellen mit ≥ Kugelradius Wandabstand
   erreichbar (Wasser sperrt).
10. **Par:** Füllgrad im Inneren jedes Kästchens; ab 8 % gilt es als angekreuzt.
11. **Namens-/Bahnfeld:** Ausschnitt mit 12 px/mm als „Tinte mit Alpha“ (Papier wird
    durchsichtig), auf die Schrift zugeschnitten; kaum Tinte → `null`.

Fehlercodes (`ScanError`, jeweils mit kurzem deutschen Text fürs Display): `IMAGE_TOO_SMALL`,
`SHEET_NOT_FOUND`, `MARKS_INCOMPLETE`, `FIELD_EMPTY`, `GRAYSCALE_SCAN`, `NO_START`,
`MULTIPLE_STARTS`, `NO_HOLE`, `MULTIPLE_HOLES`, `START_IN_WALL`, `START_IN_WATER`,
`HOLE_IN_WALL`, `HOLE_IN_WATER`, `HOLE_UNREACHABLE`. Warnungen (`ScanWarning`):
`PAR_NOT_MARKED`, `PAR_MULTIPLE`, `START_MOVED`, `START_NEAR_HOLE`, `FAINT_LINES`,
`BLOCK_OFFSET`.

## Spiel

- Feste Zeitschritte 120 Hz, ganzzahlige Schrittuhr → unabhängig von der Bildrate und
  bitgenau reproduzierbar.
- Kugel 3,5 mm Radius (Durchgänge müssen ≥ 7 mm breit sein; die Vorlage empfiehlt 15 mm),
  Loch 5,5 mm; Rollreibung 120 mm/s² + 0,35/s Dämpfung; volle Stärke 420 mm/s
  (≈ 1,6 Feldbreiten).
- Wände = Wandzellen (Quadrate) + Feldrand. Kontaktnormale gemittelt über alle berührten
  Zellen (gewichtet mit Eindringtiefe), dadurch prallt die Kugel auch an schrägen,
  treppenförmig gerasterten Wänden sinnvoll ab. Abprall: 70 % normal, 95 % tangential.
- Unterschritte ≤ 0,25 mm; lässt sich eine Überlappung nicht auflösen, geht die Kugel auf
  die letzte gültige Position zurück. Sie kann weder tunneln noch stecken bleiben.
- Loch: „Mulde“ zieht die Kugel zur Mitte; eingelocht bei < 4 mm Abstand und < 190 mm/s,
  schneller rollt sie (leicht abgelenkt) darüber.
- Wasser: +1 Strafschlag, Kugel zurück an die Position vor dem Schlag.
- Höchstens 10 Schläge (inkl. Strafschlägen), danach „Aufgegeben“. Bezeichnungen:
  Hole-in-One, Albatros, Eagle, Birdie, Par, Bogey, Doppel-Bogey, Triple-Bogey, „n über Par“.

## Darstellung und Urkunde

- Logische Größe frei wählbar (Standard 800 × 480), die Plattform skaliert. HUD mit
  großer Schrift (Schlag, Par, Bahnname-Scan), Touch-Radius 64 px um die Kugel,
  Zielhilfe mit Stärkeanzeige (blendet sich weg vom Ball ein), Ergebnis mit großen
  Schaltflächen „Urkunde drucken“, „Nochmal“ und (am Gerät) „Neue Bahn“.
- Die Bahn wird einmal pro Bildschirmgröße als Bild vorgerendert (Rasen mit Mähstreifen,
  Wände mit Fase und Schlagschatten, Wasser mit Uferlinie).
- Urkunde A4 hoch in mm: Titel, eingescannter Name (sonst „Unbekannter Profi“),
  Bahnname-Scan, Bahn mit nummerierten Ballspuren (Wasser markiert), Schläge, Par,
  Ergebnisband, Datum, Siegel.

## Tests

`./build.sh test` führt alles aus (eigener Runner, Namenskonvention `test*`,
datengetriebene Fälle, Exit-Code ≠ 0 bei Fehlern). Stand: **125 Tests, 125 bestanden,
0 fehlgeschlagen, 0 übersprungen** (≈ 33 s).

- **Scan (63 Testbilder):** 34 Positivfälle (Beispiel bei 150/200/300/600 dpi, 90°/180°/270°,
  ±2–4° schief, verschoben, Rauschen, JPEG 60, unscharf, grau/gelblich, zu dunkel,
  Schattenverlauf, Kombination; eigene Bahnen: schräg, dünn 0,3 mm, Lücken, gewunden, Wasser
  in drei Stilen, Par-Varianten, ohne Namen, Start nah an der Wand, hellgraue Wände). Geprüft:
  Start/Loch ≤ 2 mm (gemessen: im Mittel 0,08/0,11 mm, max. 0,26 mm), Par, Warnungen,
  Namens-/Bahnfeld, ≥ 98 % der Soll-Wandpunkte getroffen (bei Lücken 100 %), **keine**
  Wandzelle weiter als 3 mm von einer gezeichneten Wand, Wasser innen ≥ 97 % und nirgends
  sonst. 19 Negativfälle mit dem richtigen Fehlercode (u. a. Start fehlt, zwei Löcher,
  eingemauert, Loch hinter Wasser, Start/Loch in Wand, leeres Blatt, Fotos ohne Marken,
  abgeschnittene Ecke/Rand, Block verdeckt, gespiegelt, weiß, winzig, Graustufen, verzerrt).
  10 leere Vorlagen (150/300/600 dpi, Rauschen, JPEG, unscharf, dunkel, gelb, grau, gedreht):
  **null** Wand-, Wasser- oder Farbzellen.
- **Laufzeit/Speicher:** 300 dpi (8,7 MPixel) warm ≈ 95 ms, in frischer JVM ≈ 270–290 ms
  (ohne PNG-Laden); 600 dpi ≈ 140 ms. Arbeitspuffer 4,6 MB = 13 % des Bildspeichers,
  gemessener Heap-Zuwachs 9,4 MB bei 34,8 MB Bild.
- **Physik-Fuzzing:** 15 400 zufällige Schläge (fester Seed) auf 7 Leveln (5 gescannt,
  2 synthetisch mit 1-Zellen-Treppen, spitzen Keilen, Taschen, Labyrinth). Nach jedem
  Unterschritt: im Feld, Wandabstand ≥ Radius (gemessenes Minimum exakt 3,5000 mm),
  Schritt ≤ 0,6 mm, freier Bereich unverändert (kein Tunneln); jeder Schlag kommt von selbst
  zur Ruhe (längster 1,9 s).
- **Physik-Einzelfälle:** gerader Schlag ins Loch, zu schnell rollt drüber, langsam am Rand
  fällt hinein, Wasser (Strafschlag, exakte Rückstellung), Abprall an senkrechter Wand
  (−141,6° gemessen, Modell −141,9°) und an 45°-Wand (−86,8°, ideal −90°).
- **Determinismus:** bitgleiche Wiederholung; gleiche Schläge bei 7/16/33 ms und
  unregelmäßigen Bildzeiten ergeben exakt denselben Ablauf.
- **Level:** Speichern → Laden identisch (mit und ohne Bilder), fehlerhafte Dateien werden
  abgelehnt, Unveränderlichkeit, Geometrie, Erreichbarkeit.
- **Darstellung/Bedienung:** Ziehen am Ball schlägt entgegengesetzt, Stärke gedeckelt,
  Tippen neben dem Ball/Mini-Zug/Abbruch schlagen nicht; Ergebnis-Schaltflächen; Layout bei
  800×480, 1024×600, 480×272, 1280×720, 800×600: kein Text außerhalb, keine überlappenden
  Texte; echtes Swing-Fenster per `java.awt.Robot` bedient (unter Xvfb).
- **Urkunde:** rendert mit und ohne Namensbild, bei vielen Schlägen, alles innerhalb der Seite.
- **App-Ablauf:** Scan → Spiel → Urkunde → Neue Bahn, Fehler + Neu scannen, Scannerfehler,
  verspäteter Rückruf, Warnung als Hinweis, Rückkehr nach Inaktivität.

Sichtprüfung: `./build.sh visual` schreibt Debug-Bilder der Auswertung, Spiel-Screenshots
(auch 1024×600 und 480×272), App-Bildschirme und Urkunden nach `build/visual/`.

## Bekannte Grenzen

Gemessen mit `./build.sh grenzen` (23 härtere Varianten, 22 verhalten sich wie erwartet):

| Variante | Ergebnis |
|---|---|
| 60 / 75 / 100 dpi | erkannt, Positionen ≤ 0,26 mm, Wände 100 % |
| 8° / 15° / 30° / 45° schief | erkannt |
| JPEG Qualität 15, Rauschen σ 35, Unschärfe r = 4 px | erkannt |
| Helligkeitsverlauf 30 → 100 %, Gamma 2 ×0,55, orange Papier, blasser Druck (45 % Kontrast) | erkannt |
| Bleistift (grau 45 %, 0,5 mm), Buntstift-Wasser (hellblau) | erkannt |
| **Wände nur hellgrau (62 %)** | nicht als Wand erkannt → Warnung „Linien zu hell“, Level ohne diese Wände |
| **Unschärfe r = 7 px (≈ 0,6 mm)** | Blatt nicht erkannt (Suchmuster verschwimmen) |
| **Schräg fotografiert (Trapez 2–8 %)** | abgelehnt („Blatt nicht erkannt“) – nur Flachbett/Einzug, keine Handyfotos (dafür bräuchte es eine projektive Entzerrung) |
| **Graustufen-Scan** | abgelehnt mit „Bitte in Farbe scannen“ |

Weitere Grenzen und Annahmen:

- Die Testbilder sind synthetisch (pycairo). Echte Filzstifte, Papier, Scanner-Gamma und
  JPEG-Kompression der MA3500 sind **nicht** getestet – das ist der nächste Schritt.
- Farben: Rot umfasst auch Pink; **Orange, Gelb, Lila** werden ignoriert (dunkel → Wand).
  Ein roter Punkt, der über eine Wand gemalt wird, unterbricht diese Wand.
- Wände sind auf 1 mm gerastert; kleine Buckel (≤ 1 mm) aus dem Lückenschließen sind in
  Spiel und Urkunde sichtbar.
- Lücken bis 4 mm werden geschlossen; das ist unkritisch, weil die Kugel (7 mm) dort ohnehin
  nicht durchpasst.
- Ecken müssen frei sein (9 mm Rand um die Marken); das Blatt darf nicht über den
  Scanbereich hinausragen.
- Der Erreichbarkeitstest arbeitet auf Zellmitten mit 0,3 mm Toleranz – extrem knappe
  Durchgänge (≈ 7 mm) können als erreichbar gelten, obwohl sie im Spiel sehr schwer sind.
- Die Analyse läuft im UI-Aufruf (`update`) – auf dem Gerät ggf. in einen Worker verlegen.

## Nächste Schritte

1. HyPAS-SDK: Fragen in `src/hypas/README.md` klären, die vier Adapter bauen.
2. Echte Scans von der MA3500 mit `./build.sh batch <ordner>` auswerten (siehe Liste im
   Abschlussbericht bzw. unten).
3. Bei Bedarf die Originalvorlage übernehmen (`template.json`-Schlüssel angleichen).

Empfohlene echte Testscans: Beispielblatt in Farbe bei 150/200/300 dpi über Vorlagenglas
**und** Einzug; quer und hoch eingelegt; 180° gedreht; leicht schief; mit echten
Filzstiften, dünnem Fineliner, Bleistift und Buntstift; Wasser ausgemalt/schraffiert/nur
umrandet; blasses Papier/Umweltpapier; leere Vorlage (Raster darf nie Wand werden);
JPEG- und PDF-Ausgabe des Scanners; die Standard-Scaneinstellung (Farbe automatisch?);
Blatt mit Knick/Schatten; auf einem anderen Drucker gedruckte Vorlage (Maßstab!).

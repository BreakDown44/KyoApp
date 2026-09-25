package de.scangolf.test;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelGeometry;
import de.scangolf.core.level.LevelIO;
import de.scangolf.core.level.Rules;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.pc.Images;

/** Level: Serialisierung, Unveränderlichkeit, Geometrie, Erreichbarkeit. */
public class LevelTest {

    static final String SMALL = String.join("\n",
            "{",
            "  \"par\": 2,",
            "  \"grid\": [",
            "    \"....................\",",
            "    \"....................\",",
            "    \"..S.......#.........\",",
            "    \"..........#.........\",",
            "    \"..........#......H..\",",
            "    \"..........#.........\",",
            "    \"~~~~................\",",
            "    \"....................\"",
            "  ]",
            "}");

    public void testRoundTripScannedLevel() {
        Images.Raw raw = Manifest.load("beispiel-300dpi.png");
        ScanResult r = Manifest.analyzer().analyze(raw.argb, raw.width, raw.height);
        Level a = r.level();
        Check.notNull(a, "Level aus Beispiel");
        Check.notNull(a.nameImage(), "Namensbild vorhanden");
        String json = LevelIO.toJson(a);
        Level b = LevelIO.fromJson(json);
        Check.equal(a, b, "Level nach Speichern/Laden identisch");
        Check.equal(json, LevelIO.toJson(b), "erneutes Speichern ergibt denselben Text");
        Check.isTrue(a.nameImage().sameContent(b.nameImage()), "Namensbild pixelgleich");
        Check.isTrue(a.laneImage().sameContent(b.laneImage()), "Bahnbild pixelgleich");
        Check.info(String.format("Leveldatei %.1f kB", json.length() / 1024.0));
    }

    public void testRoundTripWithoutImages() {
        Level a = LevelIO.fromJson(SMALL);
        Check.equal(20, a.cols(), "Spalten");
        Check.equal(8, a.rows(), "Zeilen");
        Check.equal(2, a.par(), "Par");
        Check.near(2.5, a.startX(), 1e-9, "Start x aus S-Zelle");
        Check.near(17.5, a.holeX(), 1e-9, "Loch x aus H-Zelle");
        Check.equal(CellType.WATER, a.cell(0, 6), "Wasserzelle");
        Level b = LevelIO.fromJson(LevelIO.toJson(a));
        Check.equal(a, b, "Rundreise ohne Bilder");
        Check.isTrue(b.nameImage() == null && b.laneImage() == null, "keine Bilder");
    }

    public void testLevelIsImmutable() {
        Grid g = new Grid(10, 10, 1.0);
        Level l = new Level(g, 2, 2, 8, 8, 3, null, null);
        g.set(5, 5, CellType.WALL);
        Check.equal(CellType.EMPTY, l.cell(5, 5), "Änderung am Quellraster wirkt nicht aufs Level");
        Grid c = l.gridCopy();
        c.set(4, 4, CellType.WALL);
        Check.equal(CellType.EMPTY, l.cell(4, 4), "Änderung an der Kopie wirkt nicht aufs Level");
        Check.equal(CellType.WALL, l.cell(-1, 3), "außerhalb gilt Wand");
    }

    public void testRejectsBrokenFiles() {
        String[] bad = {
            "{\"grid\": []}",
            "{\"grid\": [\"..S\", \"..\"]}",
            "{\"grid\": [\"..X..\"]}",
            "{\"format\": \"etwas\", \"grid\": [\"S.H\"]}",
            "{\"grid\": [\"....\"]}",
        };
        for (String s : bad) {
            try {
                LevelIO.fromJson(s);
                throw Check.fail("kein Fehler bei " + s);
            } catch (IllegalArgumentException expected) {
                // gewollt
            }
        }
    }

    public void testWallDistance() {
        Grid g = new Grid(20, 20, 1.0);
        g.set(10, 10, CellType.WALL);
        Check.near(0.0, LevelGeometry.wallDistance(g, 10.5, 10.5, 5), 1e-9, "in der Wand");
        Check.near(1.5, LevelGeometry.wallDistance(g, 12.5, 10.5, 5), 1e-9, "rechts daneben");
        Check.near(Math.hypot(1, 1), LevelGeometry.wallDistance(g, 12, 12, 5), 1e-9, "diagonal zur Ecke");
        Check.near(0.5, LevelGeometry.wallDistance(g, 0.5, 5, 5), 1e-9, "Feldrand zählt als Wand");
        Check.near(5.0, LevelGeometry.wallDistance(g, 5, 5, 5), 1e-9, "gedeckelt");
    }

    public void testReachability() {
        // Korridor 20 mm breit, dann Wand mit Lücke
        Grid g = new Grid(60, 30, 1.0);
        for (int y = 0; y < 30; y++) {
            if (y < 10 || y >= 10 + 6) {
                g.set(30, y, CellType.WALL);
            }
        }
        double r = Rules.BALL_RADIUS_MM;
        double cap = Rules.HOLE_CAPTURE_RADIUS_MM;
        Check.isFalse(LevelGeometry.reachable(g, 10, 15, 50, 15, r, cap), "6-mm-Lücke ist zu schmal für 7-mm-Kugel");
        for (int y = 16; y < 19; y++) {
            g.set(30, y, CellType.EMPTY);
        }
        Check.isTrue(LevelGeometry.reachable(g, 10, 15, 50, 15, r, cap), "9-mm-Lücke ist passierbar");
        for (int y = 0; y < 30; y++) {
            g.set(40, y, CellType.WATER);
        }
        Check.isFalse(LevelGeometry.reachable(g, 10, 15, 50, 15, r, cap), "durchgehender Wassergraben sperrt");
    }

    public void testNearestFit() {
        Grid g = new Grid(40, 40, 1.0);
        for (int y = 0; y < 40; y++) {
            g.set(20, y, CellType.WALL);
        }
        double[] p = LevelGeometry.nearestFit(g, 18.0, 20.0, 3.5, 5.0);
        Check.notNull(p, "Position gefunden");
        Check.near(16.5, p[0], 0.26, "nach links geschoben");
        Check.near(20.0, p[1], 0.26, "y unverändert");
        Check.isTrue(LevelGeometry.nearestFit(g, 20.5, 20, 3.5, 2.0) == null, "in der Wand ohne Ausweg");
    }
}

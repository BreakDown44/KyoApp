package de.scangolf.test;

import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.SheetTemplate;
import de.scangolf.core.util.Json;
import de.scangolf.pc.Images;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Zugriff auf testbilder/out/manifest.json und die Vorlage. */
final class Manifest {

    private Manifest() {
    }

    static Path outDir() {
        return Check.root().resolve("testbilder/out");
    }

    static List<Map<String, Object>> images() {
        Path m = outDir().resolve("manifest.json");
        if (!Files.exists(m)) {
            throw new AssertionError("Testbilder fehlen – ./build.sh testimages ausführen");
        }
        Object root = Json.parse(Check.readText(m));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.list(root, "images")) {
            out.add(Json.map(o));
        }
        return out;
    }

    static SheetTemplate template() {
        return SheetTemplate.parse(Check.readText(Check.root().resolve("vorlage/out/template.json")));
    }

    static ScanAnalyzer analyzer() {
        return new ScanAnalyzer(template());
    }

    static Images.Raw load(String file) {
        try {
            return Images.load(new File(outDir().toFile(), file));
        } catch (IOException e) {
            throw new AssertionError("Bild nicht ladbar: " + file + ": " + e.getMessage());
        }
    }

    static double[] point(Object o) {
        @SuppressWarnings("unchecked")
        List<Object> l = (List<Object>) o;
        return new double[] {(Double) l.get(0), (Double) l.get(1)};
    }

    static List<double[]> points(Object o) {
        List<double[]> out = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> l = (List<Object>) o;
        for (Object p : l) {
            out.add(point(p));
        }
        return out;
    }
}

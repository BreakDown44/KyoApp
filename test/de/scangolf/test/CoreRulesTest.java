package de.scangolf.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prüft die Kern-Regeln direkt am Quelltext (zusätzlich zur Prüfung in build.sh):
 * keine AWT/Swing/ImageIO/NIO-Imports, keine Lambdas, Methodenreferenzen, Streams usw.
 */
public class CoreRulesTest {

    private static final String[] FORBIDDEN = {
        "java\\.awt", "javax\\.", "java\\.nio", "java\\.util\\.function", "java\\.util\\.stream",
        "java\\.lang\\.reflect", "->", "::", "\\.stream\\(\\)", "\\bOptional\\b", "String\\.join",
        "\\bvar\\s", "\\brecord\\s", "\\bThread\\b", "\\bsynchronized\\b", "StringBuilder",
        "String\\.format", "Arrays\\.copyOf", "<>", "try \\(", "@Override", "System\\.nanoTime",
    };

    private List<Path> coreFiles() throws IOException {
        try (Stream<Path> s = Files.walk(Check.root().resolve("src/core"))) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    public void testCoreHasSources() throws IOException {
        Check.isTrue(!coreFiles().isEmpty(), "Kern-Quellen gefunden");
    }

    public void testNoForbiddenConstructsInCore() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : coreFiles()) {
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                    continue;
                }
                for (String f : FORBIDDEN) {
                    if (Pattern.compile(f).matcher(line).find()) {
                        hits.add(p.getFileName() + ":" + (i + 1) + " [" + f + "] " + line);
                    }
                }
            }
        }
        Check.isTrue(hits.isEmpty(), "Verbotene Konstrukte im Kern:\n" + String.join("\n", hits));
    }

    public void testCorePackagesArePlatformNeutral() throws IOException {
        for (Path p : coreFiles()) {
            for (String line : Files.readAllLines(p)) {
                if (line.startsWith("import ")) {
                    boolean ok = line.startsWith("import java.util.") || line.startsWith("import java.io.")
                            || line.startsWith("import de.scangolf.core.");
                    Check.isTrue(ok, "Unerlaubter Import in " + p.getFileName() + ": " + line);
                }
            }
        }
    }
}

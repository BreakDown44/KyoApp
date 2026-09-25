package de.scangolf.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Zusicherungen für die Tests. Jede Verletzung wirft AssertionError. */
public final class Check {

    private Check() {
    }

    public static void isTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    public static void isFalse(boolean cond, String msg) {
        isTrue(!cond, msg);
    }

    public static void equal(Object expected, Object actual, String msg) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(msg + ": erwartet <" + expected + ">, war <" + actual + ">");
        }
    }

    public static void near(double expected, double actual, double tol, String msg) {
        if (!(Math.abs(expected - actual) <= tol)) {
            throw new AssertionError(msg + ": erwartet " + expected + " ± " + tol + ", war " + actual);
        }
    }

    public static void notNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg + ": war null");
        }
    }

    public static AssertionError fail(String msg) {
        throw new AssertionError(msg);
    }

    /** Projektwurzel (von build.sh gesetzt, sonst Arbeitsverzeichnis). */
    public static Path root() {
        return Paths.get(System.getProperty("scangolf.root", "."));
    }

    public static String readText(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Datei nicht lesbar: " + p + " (" + e.getMessage() + ")");
        }
    }

    /** Ausgabe von Messwerten im Testlauf. */
    public static void info(String s) {
        System.out.println("      info: " + s);
    }
}

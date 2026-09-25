package de.scangolf.test;

/** Test kann in dieser Umgebung nicht laufen (wird als SKIP gezählt, nicht als bestanden). */
public final class TestSkipped extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TestSkipped(String reason) {
        super(reason);
    }
}

package de.scangolf.test;

/** Ein benannter, datengetriebener Testfall. */
public final class TestCase {
    public final String name;
    public final Runnable body;

    public TestCase(String name, Runnable body) {
        this.name = name;
        this.body = body;
    }
}

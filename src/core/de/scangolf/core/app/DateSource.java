package de.scangolf.core.app;

/** Plattform-Adapter: heutiges Datum für die Urkunde (der Kern hat keine Uhr). */
public interface DateSource {

    /** {Jahr, Monat 1..12, Tag 1..31} */
    int[] today();
}

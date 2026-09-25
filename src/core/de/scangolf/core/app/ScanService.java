package de.scangolf.core.app;

/**
 * Plattform-Adapter: liefert ein gescanntes Blatt als ARGB-Bild.
 * (Eigene Schnittstelle von ScanGolf – kein SDK-Typ.)
 */
public interface ScanService {

    /** Rückmeldung eines Scans; darf auch später oder aus einem anderen Kontext kommen. */
    interface Callback {
        void scanned(int[] argb, int width, int height);

        void failed(String message);
    }

    /** Scan anstoßen. Die Umsetzung ruft genau einmal callback.scanned oder callback.failed. */
    void requestScan(Callback callback);
}

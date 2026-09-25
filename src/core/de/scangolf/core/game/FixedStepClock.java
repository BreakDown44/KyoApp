package de.scangolf.core.game;

/**
 * Wandelt beliebige Bildzeiten in eine ganze Zahl fester Simulationsschritte um.
 * Rechnet exakt in Ganzzahlen, damit die Schrittzahl nicht von Rundung abhängt.
 */
public final class FixedStepClock {

    private final int hz;
    private final int maxStepsPerAdvance;
    private long acc;

    public FixedStepClock(int hz, int maxStepsPerAdvance) {
        this.hz = hz;
        this.maxStepsPerAdvance = maxStepsPerAdvance;
    }

    /** Anzahl Schritte, die für die vergangenen Millisekunden fällig sind. */
    public int advance(long elapsedMs) {
        if (elapsedMs < 0) {
            elapsedMs = 0;
        }
        acc += elapsedMs * hz;
        long steps = acc / 1000;
        if (steps > maxStepsPerAdvance) {
            // Nach langen Aussetzern (z. B. Gerät beschäftigt) nicht nachholen
            acc = 0;
            return maxStepsPerAdvance;
        }
        acc -= steps * 1000;
        return (int) steps;
    }

    public void reset() {
        acc = 0;
    }
}

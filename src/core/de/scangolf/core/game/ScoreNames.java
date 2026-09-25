package de.scangolf.core.game;

/** Ergebnisbezeichnung relativ zu Par. */
public final class ScoreNames {

    private ScoreNames() {
    }

    public static String label(int strokes, int par, boolean holed) {
        if (!holed) {
            return "Aufgegeben";
        }
        if (strokes == 1) {
            return "Hole-in-One";
        }
        int diff = strokes - par;
        switch (diff) {
            case -3: return "Albatros";
            case -2: return "Eagle";
            case -1: return "Birdie";
            case 0: return "Par";
            case 1: return "Bogey";
            case 2: return "Doppel-Bogey";
            case 3: return "Triple-Bogey";
            default:
                return diff < 0 ? "Kondor" : diff + " über Par";
        }
    }

    /** "-1", "±0", "+2" */
    public static String relative(int strokes, int par) {
        int d = strokes - par;
        if (d == 0) {
            return "±0";
        }
        return d > 0 ? "+" + d : Integer.toString(d);
    }
}

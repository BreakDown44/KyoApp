package de.scangolf.core.cert;

import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.game.ScoreNames;
import de.scangolf.core.game.ShotTrace;
import de.scangolf.core.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Inhalt einer Urkunde: Level (mit Namens-/Bahnbild), Ergebnis, Ballspur, Datum. */
public final class Certificate {

    private static final String[] MONTHS = {
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober",
        "November", "Dezember",
    };

    final Level level;
    final int strokes;
    final boolean holed;
    final List<ShotTrace> traces;
    final int day;
    final int month;
    final int year;

    /**
     * @param month 1..12 (das Datum kommt von der Plattform, der Kern hat keine Uhr)
     */
    public Certificate(Level level, int strokes, boolean holed, List<ShotTrace> traces, int day, int month, int year) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            throw new IllegalArgumentException("Datum ungültig");
        }
        this.level = level;
        this.strokes = strokes;
        this.holed = holed;
        this.traces = new ArrayList<ShotTrace>(traces);
        this.day = day;
        this.month = month;
        this.year = year;
    }

    public static Certificate fromGame(Game g, int day, int month, int year) {
        return new Certificate(g.level(), g.strokes(), g.state() == GameState.HOLED, g.traces(), day, month, year);
    }

    public String resultLabel() {
        return ScoreNames.label(strokes, level.par(), holed);
    }

    /** z. B. "25. September 2026" */
    public String dateText() {
        return day + ". " + MONTHS[month - 1] + " " + year;
    }

    public int strokes() {
        return strokes;
    }

    public boolean holed() {
        return holed;
    }

    public Level level() {
        return level;
    }
}

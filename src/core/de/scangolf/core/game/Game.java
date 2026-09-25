package de.scangolf.core.game;

import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Runde auf einem Level: Zustände, Schlagzähler, Regeln (Wasser, Loch, Maximum).
 * Vollständig deterministisch – gleiche Schläge ergeben exakt denselben Ablauf.
 */
public final class Game {

    /** Schläge unter dieser Stärke (0..1) werden ignoriert. */
    public static final double MIN_POWER = 0.03;
    /** Abstand der Spurpunkte in mm. */
    static final double TRACE_STEP_MM = 1.0;

    private final Level level;
    private final Physics phys;
    private GameState state = GameState.AIMING;
    private int strokes;
    private int penalties;
    private double shotStartX;
    private double shotStartY;
    private int rollSteps;
    private long totalSteps;
    private int pendingEvents;
    private boolean forcedStop;
    private final List<ShotTrace> traces = new ArrayList<ShotTrace>();
    private ShotTrace current;

    public Game(Level level) {
        this.level = level;
        this.phys = new Physics(level);
        phys.x = level.startX();
        phys.y = level.startY();
    }

    public Level level() {
        return level;
    }

    public GameState state() {
        return state;
    }

    public boolean isFinished() {
        return state == GameState.HOLED || state == GameState.GAVE_UP;
    }

    /** Schläge inklusive Strafschlägen. */
    public int strokes() {
        return strokes;
    }

    public int penalties() {
        return penalties;
    }

    public int par() {
        return level.par();
    }

    public double ballX() {
        return phys.x;
    }

    public double ballY() {
        return phys.y;
    }

    public double ballVX() {
        return phys.vx;
    }

    public double ballVY() {
        return phys.vy;
    }

    public double ballSpeed() {
        return phys.speed();
    }

    public long totalSteps() {
        return totalSteps;
    }

    /** Wurde eine Kugel je durch das Sicherheitsnetz (zu lange Rollzeit) gestoppt? */
    public boolean hadForcedStop() {
        return forcedStop;
    }

    public void setSubstepListener(Physics.SubstepListener l) {
        phys.setSubstepListener(l);
    }

    /**
     * Schlag ausführen. Richtung (dx, dy) muss nicht normiert sein; power 0..1.
     * Rückgabe false, wenn gerade nicht geschlagen werden kann.
     */
    public boolean shoot(double dx, double dy, double power) {
        if (state != GameState.AIMING) {
            return false;
        }
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-9 || power < MIN_POWER) {
            return false;
        }
        if (power > 1) {
            power = 1;
        }
        double v = power * Physics.MAX_SHOT_SPEED;
        phys.vx = dx / len * v;
        phys.vy = dy / len * v;
        shotStartX = phys.x;
        shotStartY = phys.y;
        strokes++;
        rollSteps = 0;
        state = GameState.ROLLING;
        current = new ShotTrace(phys.x, phys.y);
        traces.add(current);
        return true;
    }

    /** Einen festen Simulationsschritt ausführen (nur im Zustand ROLLING wirksam). */
    public void step() {
        if (state != GameState.ROLLING) {
            return;
        }
        totalSteps++;
        rollSteps++;
        int ev = phys.step();
        pendingEvents |= ev;
        if ((ev & Physics.EV_HOLED) != 0) {
            current.add(level.holeX(), level.holeY());
            current.endHoled();
            phys.x = level.holeX();
            phys.y = level.holeY();
            phys.vx = 0;
            phys.vy = 0;
            state = GameState.HOLED;
            return;
        }
        if ((ev & Physics.EV_WATER) != 0) {
            current.add(phys.x, phys.y);
            current.endInWater();
            strokes++;
            penalties++;
            phys.x = shotStartX;
            phys.y = shotStartY;
            phys.vx = 0;
            phys.vy = 0;
            endOfShot();
            return;
        }
        double dx = phys.x - current.lastX();
        double dy = phys.y - current.lastY();
        if (dx * dx + dy * dy >= TRACE_STEP_MM * TRACE_STEP_MM) {
            current.add(phys.x, phys.y);
        }
        if ((ev & Physics.EV_STOPPED) == 0 && rollSteps >= Physics.MAX_ROLL_STEPS) {
            phys.vx = 0;
            phys.vy = 0;
            forcedStop = true;
            pendingEvents |= Physics.EV_FORCED_STOP;
            ev |= Physics.EV_STOPPED;
        }
        if ((ev & Physics.EV_STOPPED) != 0) {
            current.add(phys.x, phys.y);
            endOfShot();
        }
    }

    private void endOfShot() {
        state = strokes >= Rules.MAX_STROKES ? GameState.GAVE_UP : GameState.AIMING;
    }

    /** Runde abbrechen (zählt als aufgegeben). */
    public void giveUp() {
        if (!isFinished()) {
            phys.vx = 0;
            phys.vy = 0;
            state = GameState.GAVE_UP;
        }
    }

    /** Liefert die seit dem letzten Aufruf aufgetretenen Ereignis-Bits (Physics.EV_*) und löscht sie. */
    public int pollEvents() {
        int e = pendingEvents;
        pendingEvents = 0;
        return e;
    }

    /** Ergebnisbezeichnung ("Birdie", "Aufgegeben", ...). */
    public String resultLabel() {
        return ScoreNames.label(strokes, level.par(), state == GameState.HOLED);
    }

    /** Spuren aller bisherigen Schläge (unveränderliche Sicht). */
    public List<ShotTrace> traces() {
        return new ArrayList<ShotTrace>(traces);
    }
}

package de.scangolf.core.render;

import de.scangolf.core.game.FixedStepClock;
import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.game.Physics;
import de.scangolf.core.game.ScoreNames;
import de.scangolf.core.game.ShotTrace;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;
import de.scangolf.core.util.ArgbImage;

import java.util.List;

/**
 * Spielbildschirm: zeichnet Bahn, Kugel, Zielhilfe, Anzeige und Ergebnis über {@link Canvas}
 * und setzt Touch-Gesten in Schläge um (am Ball berühren, zurückziehen, loslassen).
 *
 * Logische Größe frei wählbar (Standard 800 x 480); die Plattform skaliert.
 */
public final class GameScreen implements Screen {

    /** Aktionen, die die Plattform umsetzt. */
    public interface Listener {
        /** "Urkunde drucken" wurde gedrückt. */
        void onPrintCertificate(Game game);

        /** "Nochmal" wurde gedrückt; die neue Runde läuft bereits. */
        void onPlayAgain(Game newGame);

        /** "Neue Bahn" wurde gedrückt (nur sichtbar, wenn eingeschaltet). */
        void onNewLevel();
    }

    public static final int DEFAULT_WIDTH = 800;
    public static final int DEFAULT_HEIGHT = 480;

    static final double TOUCH_RADIUS = 64;
    static final double MAX_DRAG = 170;
    static final double MIN_DRAG = 14;
    static final long RESULT_DELAY_MS = 800;
    static final long SINK_MS = 400;

    private final int width;
    private final int height;
    private final Level level;
    private final Listener listener;
    private final LevelRasterizer rasterizer;
    private Game game;
    private final FixedStepClock clock = new FixedStepClock(Physics.HZ, 30);
    private long now;
    private long lastUpdate = -1;

    // Layout
    private final double hudH;
    private double fieldX;
    private double fieldY;
    private double scale;

    private ArgbImage background;
    private double backgroundScale;

    private boolean dragging;
    private double dragX;
    private double dragY;
    private boolean anyShot;
    private long finishedAt = -1;
    private String toast;
    private int toastColor;
    private long toastUntil;
    private final Button printButton = new Button("Urkunde drucken", true);
    private final Button againButton = new Button("Nochmal", false);
    private final Button newLevelButton = new Button("Neue Bahn", false);
    private boolean newLevelEnabled;
    private Button pressed;

    public GameScreen(Level level, int width, int height, Listener listener) {
        this.level = level;
        this.width = width;
        this.height = height;
        this.listener = listener;
        this.rasterizer = new LevelRasterizer(level);
        this.hudH = Math.max(48, Math.round(height * 0.12));
        layout();
        newGame();
    }

    public GameScreen(Level level, Listener listener) {
        this(level, DEFAULT_WIDTH, DEFAULT_HEIGHT, listener);
    }

    private void layout() {
        double margin = Math.max(8, width * 0.012);
        double availW = width - 2 * margin;
        double availH = height - hudH - 2 * margin;
        scale = Math.min(availW / level.widthMm(), availH / level.heightMm());
        fieldX = (width - level.widthMm() * scale) / 2;
        fieldY = hudH + margin + (availH - level.heightMm() * scale) / 2;
        double bh = Math.max(64, height * 0.15);
        double by = height / 2.0 + height * 0.19;
        if (newLevelEnabled) {
            double pw = Math.min(width - 40, 600) - 36;
            double gap = 14;
            double bw = (pw - 2 * gap) / 2.6;
            double x0 = width / 2.0 - pw / 2;
            printButton.layout(x0, by, bw * 1.2, bh);
            againButton.layout(x0 + bw * 1.2 + gap, by, bw * 0.7, bh);
            newLevelButton.layout(x0 + bw * 1.9 + 2 * gap, by, bw * 0.7, bh);
        } else {
            double bw = Math.min(290, width * 0.36);
            double gap = 24;
            double total = bw + gap + bw * 0.75;
            printButton.layout(width / 2.0 - total / 2, by, bw, bh);
            againButton.layout(width / 2.0 - total / 2 + bw + gap, by, bw * 0.75, bh);
        }
    }

    /** Dritte Schaltfläche "Neue Bahn" im Ergebnis einblenden (z. B. am Gerät für die nächste Person). */
    public void setNewLevelButton(boolean enabled) {
        newLevelEnabled = enabled;
        layout();
    }

    public void newGame() {
        game = new Game(level);
        finishedAt = -1;
        dragging = false;
        pressed = null;
        toast = null;
    }

    public Game game() {
        return game;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Hinweis oben einblenden (z. B. Druckstatus). */
    public void showToast(String text, int argb, long durationMs) {
        toast = text;
        toastColor = argb;
        if (lastUpdate < 0) {
            toastUntil = -durationMs;   // startet mit dem ersten update()
        } else {
            toastUntil = now + durationMs;
        }
    }

    /** Pixel je Millimeter der Bahn (logisch). */
    public double fieldScale() {
        return scale;
    }

    public double toScreenX(double mm) {
        return fieldX + mm * scale;
    }

    public double toScreenY(double mm) {
        return fieldY + mm * scale;
    }

    // ================================================================ Zeit

    public void update(long nowMs) {
        if (lastUpdate < 0) {
            lastUpdate = nowMs;
        }
        long elapsed = nowMs - lastUpdate;
        lastUpdate = nowMs;
        now = nowMs;
        if (toast != null && toastUntil < 0) {
            toastUntil = now - toastUntil;
        }
        int steps = clock.advance(elapsed);
        for (int i = 0; i < steps && game.state() == GameState.ROLLING; i++) {
            game.step();
        }
        int ev = game.pollEvents();
        if ((ev & Physics.EV_WATER) != 0) {
            showToast("Wasser! +1 Strafschlag", 0xE01565C0, 1800);
        }
        if (game.isFinished() && finishedAt < 0) {
            finishedAt = now;
        }
    }

    private boolean resultVisible() {
        return finishedAt >= 0 && now - finishedAt >= (game.state() == GameState.HOLED ? RESULT_DELAY_MS : 300);
    }

    // ================================================================ Touch

    public void touch(int type, double x, double y) {
        if (resultVisible()) {
            touchResult(type, x, y);
            return;
        }
        if (type == Touch.DOWN) {
            if (game.state() == GameState.AIMING) {
                double bx = toScreenX(game.ballX());
                double by = toScreenY(game.ballY());
                if (dist(x, y, bx, by) <= TOUCH_RADIUS) {
                    dragging = true;
                    dragX = x;
                    dragY = y;
                }
            }
        } else if (type == Touch.MOVE) {
            if (dragging) {
                dragX = x;
                dragY = y;
            }
        } else if (type == Touch.UP) {
            if (dragging) {
                dragX = x;
                dragY = y;
                dragging = false;
                double[] aim = aim();
                if (aim != null && game.shoot(aim[0], aim[1], aim[2])) {
                    anyShot = true;
                }
            }
        } else {
            dragging = false;
        }
    }

    /** Aktuelle Zielvorgabe {dx, dy, Stärke} oder null, wenn der Zug zu kurz ist. */
    private double[] aim() {
        double bx = toScreenX(game.ballX());
        double by = toScreenY(game.ballY());
        double dx = bx - dragX;
        double dy = by - dragY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < MIN_DRAG) {
            return null;
        }
        double power = Math.min(1.0, (len - MIN_DRAG) / (MAX_DRAG - MIN_DRAG));
        return new double[] {dx / len, dy / len, Math.max(Game.MIN_POWER, power)};
    }

    private void touchResult(int type, double x, double y) {
        if (type == Touch.DOWN) {
            pressed = printButton.hit(x, y) ? printButton : againButton.hit(x, y) ? againButton
                    : newLevelEnabled && newLevelButton.hit(x, y) ? newLevelButton : null;
        } else if (type == Touch.UP) {
            Button b = pressed;
            pressed = null;
            if (b == null || !b.hit(x, y)) {
                return;
            }
            if (b == newLevelButton) {
                if (listener != null) {
                    listener.onNewLevel();
                }
            } else if (b == printButton) {
                showToast("Urkunde wird gedruckt …", 0xE02E7D32, 2500);
                if (listener != null) {
                    listener.onPrintCertificate(game);
                }
            } else {
                newGame();
                if (listener != null) {
                    listener.onPlayAgain(game);
                }
            }
        } else if (type == Touch.CANCEL) {
            pressed = null;
        }
    }

    // ================================================================ Zeichnen

    public void render(Canvas c) {
        c.fillRect(0, 0, width, height, Colors.FRAME);
        drawField(c);
        drawTraces(c);
        drawHole(c);
        drawBall(c);
        if (dragging && game.state() == GameState.AIMING) {
            drawAim(c);
        } else if (game.state() == GameState.AIMING && finishedAt < 0) {
            drawBallHint(c, !toastVisible());
        }
        drawHud(c);
        if (resultVisible()) {
            drawResult(c);
        } else if (toastVisible()) {
            drawToast(c, hudH + 14, width * 0.9, Math.max(16, height * 0.045));
        }
    }

    private void drawField(Canvas c) {
        double w = level.widthMm() * scale;
        double h = level.heightMm() * scale;
        // Bande (Holz/Stein-Rand)
        c.fillRoundRect(fieldX - 7, fieldY - 7, w + 14, h + 14, 10, 0xFF6B4A2B);
        c.fillRoundRect(fieldX - 7, fieldY - 7, w + 14, 6, 6, 0x40FFFFFF);
        c.fillRect(fieldX - 2, fieldY - 2, w + 4, h + 4, 0xFF3D2A18);
        double want = scale * c.deviceScale();
        if (background == null || Math.abs(want - backgroundScale) > 0.01) {
            background = rasterizer.render(want);
            backgroundScale = want;
        }
        c.image(background, fieldX, fieldY, w, h);
        // Abschlag
        double sx = toScreenX(level.startX());
        double sy = toScreenY(level.startY());
        double t = Rules.BALL_RADIUS_MM * scale * 1.9;
        c.fillRoundRect(sx - t, sy - t, 2 * t, 2 * t, t * 0.4, 0x5530A040);
        c.strokeRoundRect(sx - t, sy - t, 2 * t, 2 * t, t * 0.4, 1.5, 0x80FFFFFF);
    }

    private void drawTraces(Canvas c) {
        List<ShotTrace> traces = game.traces();
        int n = traces.size();
        double[] xs = new double[256];
        double[] ys = new double[256];
        for (int i = 0; i < n; i++) {
            ShotTrace t = traces.get(i);
            int pc = t.pointCount();
            if (xs.length < pc) {
                xs = new double[pc];
                ys = new double[pc];
            }
            for (int k = 0; k < pc; k++) {
                xs[k] = toScreenX(t.x(k));
                ys[k] = toScreenY(t.y(k));
            }
            boolean current = i == n - 1 && game.state() == GameState.ROLLING;
            c.polyline(xs, ys, pc, current ? 2.2 : 1.6, current ? 0x70FFFFFF : 0x38FFFFFF);
        }
    }

    private void drawHole(Canvas c) {
        double hx = toScreenX(level.holeX());
        double hy = toScreenY(level.holeY());
        double r = Rules.HOLE_RADIUS_MM * scale;
        c.fillCircle(hx, hy, r + 2.5, 0x55FFFFFF);
        c.fillCircle(hx, hy, r, Colors.HOLE);
        c.fillEllipse(hx, hy + r * 0.25, r * 0.8, r * 0.6, 0xFF050805);
        // Fahne (durchsichtig, wenn die Kugel dahinter liegt)
        double bx = toScreenX(game.ballX());
        double by = toScreenY(game.ballY());
        double pole = Math.max(30, 11 * scale);
        boolean near = bx > hx - 14 && bx < hx + pole * 0.9 && by > hy - pole - 12 && by < hy + 6;
        int a = near ? 90 : 255;
        c.line(hx + 1.5, hy, hx + pole * 0.45, hy + pole * 0.2, 3, Colors.withAlpha(0x000000, a / 4));
        c.line(hx, hy, hx, hy - pole, 2.6, Colors.withAlpha(0xF5F5F5, a));
        double[] fxs = {hx + 1, hx + pole * 0.62, hx + 1};
        double[] fys = {hy - pole, hy - pole * 0.82, hy - pole * 0.64};
        c.fillPolygon(fxs, fys, 3, Colors.withAlpha(Colors.FLAG, a));
    }

    private void drawBall(Canvas c) {
        double bx = toScreenX(game.ballX());
        double by = toScreenY(game.ballY());
        double r = Rules.BALL_RADIUS_MM * scale;
        if (game.state() == GameState.HOLED) {
            double t = Math.min(1.0, (now - finishedAt) / (double) SINK_MS);
            if (t >= 1.0) {
                return;
            }
            r *= 1.0 - 0.7 * t;
            c.fillCircle(bx, by, r, Colors.mix(0xFFFFFFFF, 0xFF404040, t));
            return;
        }
        c.fillEllipse(bx + r * 0.35, by + r * 0.55, r * 1.05, r * 0.85, 0x55000000);
        c.fillCircle(bx, by, r, 0xFFFDFDFD);
        c.strokeCircle(bx, by, r, 1.2, 0x55000000);
        c.fillCircle(bx + r * 0.2, by + r * 0.25, r * 0.55, 0x14000000);
        c.fillCircle(bx - r * 0.35, by - r * 0.35, r * 0.3, 0xFFFFFFFF);
    }

    private boolean toastVisible() {
        return toast != null && now < toastUntil;
    }

    private void drawBallHint(Canvas c, boolean withText) {
        double bx = toScreenX(game.ballX());
        double by = toScreenY(game.ballY());
        double pulse = 0.5 + 0.5 * Math.sin(now / 260.0);
        double r = Rules.BALL_RADIUS_MM * scale + 10 + 6 * pulse;
        c.strokeCircle(bx, by, r, 3, Colors.withAlpha(0xFFFFFF, (int) (110 + 110 * pulse)));
        if (!anyShot && withText) {
            String s = "Kugel berühren, nach hinten ziehen, loslassen";
            double size = Math.max(14, height * 0.045);
            while (c.textWidth(s, size, Canvas.FONT_BOLD) + 40 > width * 0.94 && size > 10) {
                size -= 1;
            }
            double tw = c.textWidth(s, size, Canvas.FONT_BOLD) + 40;
            double boxH = size * 1.6;
            double top = overlayTop(boxH);
            c.fillRoundRect(width / 2.0 - tw / 2, top, tw, boxH, boxH / 2, 0xC0101810);
            c.text(s, width / 2.0, top + boxH * 0.68, size, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, Colors.TEXT);
        }
    }

    /**
     * Obere Kante für eine Einblendung der Höhe h: unten im Feld, außer die Kugel liegt in der
     * unteren Hälfte – dann oben unter der Anzeige, damit nichts die Kugel verdeckt.
     */
    private double overlayTop(double h) {
        double by = toScreenY(game.ballY());
        double fieldBottom = fieldY + level.heightMm() * scale;
        if (by > fieldY + (fieldBottom - fieldY) * 0.55) {
            return hudH + 12;
        }
        return height - h - 12;
    }

    private static int powerColor(double p) {
        return p < 0.5 ? Colors.mix(0xFF66E36B, 0xFFFFD54F, p * 2) : Colors.mix(0xFFFFD54F, 0xFFFF5A36, (p - 0.5) * 2);
    }

    private void drawAim(Canvas c) {
        double bx = toScreenX(game.ballX());
        double by = toScreenY(game.ballY());
        c.line(bx, by, dragX, dragY, 2, 0x66FFFFFF);
        c.fillCircle(dragX, dragY, 9, 0x55FFFFFF);
        double[] aim = aim();
        if (aim == null) {
            return;
        }
        double p = aim[2];
        int col = powerColor(p);
        double len = 40 + p * 190;
        double r = Rules.BALL_RADIUS_MM * scale;
        for (double d = r + 10; d < len; d += 13) {
            c.fillCircle(bx + aim[0] * d, by + aim[1] * d, 3.6, col);
        }
        double tx = bx + aim[0] * (len + 8);
        double ty = by + aim[1] * (len + 8);
        double nx = -aim[1];
        double ny = aim[0];
        double[] xs = {tx + aim[0] * 14, tx + nx * 9, tx - nx * 9};
        double[] ys = {ty + aim[1] * 14, ty + ny * 9, ty - ny * 9};
        c.fillPolygon(xs, ys, 3, col);
        // Stärkeanzeige
        double bw = Math.min(320, width * 0.42);
        double bh = Math.max(24, height * 0.055);
        double x0 = width / 2.0 - bw / 2;
        double y0 = overlayTop(bh + 12) + 6;
        c.fillRoundRect(x0 - 6, y0 - 6, bw + 12, bh + 12, (bh + 12) / 2, 0xC0101810);
        c.fillRoundRect(x0, y0, bw, bh, bh / 2, 0x40FFFFFF);
        c.fillRoundRect(x0, y0, Math.max(bh, bw * p), bh, bh / 2, col);
        c.text("Stärke " + Math.round(p * 100) + " %", width / 2.0, y0 + bh * 0.74, bh * 0.72,
                Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, 0xFF102010);
    }

    private void drawHud(Canvas c) {
        c.fillRect(0, 0, width, hudH, Colors.HUD_BG);
        c.fillRect(0, hudH - 2, width, 2, 0x40F2C14E);
        double label = hudH * 0.24;
        double value = hudH * 0.52;
        double pad = 18;
        c.text("SCHLAG", pad, hudH * 0.33, label, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, 0xB0F7F4EA);
        String st = Integer.toString(game.strokes());
        c.text(st, pad, hudH * 0.86, value, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, Colors.GOLD);
        double sw = c.textWidth(st, value, Canvas.FONT_BOLD);
        c.text("/ " + Rules.MAX_STROKES, pad + sw + 8, hudH * 0.86, label * 1.3, Canvas.FONT_BOLD,
                Canvas.ALIGN_LEFT, 0x90F7F4EA);
        c.text("PAR", width - pad, hudH * 0.33, label, Canvas.FONT_BOLD, Canvas.ALIGN_RIGHT, 0xB0F7F4EA);
        c.text(Integer.toString(level.par()), width - pad, hudH * 0.86, value, Canvas.FONT_BOLD,
                Canvas.ALIGN_RIGHT, Colors.GOLD);
        ArgbImage lane = level.laneImage();
        double maxW = width * 0.5;
        double maxH = hudH - 10;
        if (lane != null) {
            double s = Math.min(maxW / lane.width(), maxH / lane.height());
            double w = lane.width() * s;
            double h = lane.height() * s;
            c.fillRoundRect(width / 2.0 - w / 2 - 12, (hudH - h) / 2 - 2, w + 24, h + 4, 10, 0xE8FFF8E7);
            c.image(lane, width / 2.0 - w / 2, (hudH - h) / 2, w, h);
        } else {
            c.text("ScanGolf", width / 2.0, hudH * 0.68, value * 0.8, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER,
                    Colors.TEXT);
        }
    }

    private void drawToast(Canvas c, double y, double maxW, double size0) {
        double size = size0;
        String[] lines = wrap(c, toast, size, maxW - 48);
        if (lines.length > 3) {
            size *= 0.8;
            lines = wrap(c, toast, size, maxW - 48);
        }
        double tw = 0;
        for (int i = 0; i < lines.length; i++) {
            tw = Math.max(tw, c.textWidth(lines[i], size, Canvas.FONT_BOLD));
        }
        tw += 48;
        double lh = size * 1.3;
        double bh = lh * lines.length + size * 0.6;
        c.fillRoundRect(width / 2.0 - tw / 2, y, tw, bh, Math.min(bh / 2, size * 0.9), toastColor);
        for (int i = 0; i < lines.length; i++) {
            c.text(lines[i], width / 2.0, y + size * 0.3 + lh * i + size * 1.0, size, Canvas.FONT_BOLD,
                    Canvas.ALIGN_CENTER, 0xFFFFFFFF);
        }
    }

    /** Zeilenumbruch an Leerzeichen. */
    static String[] wrap(Canvas c, String s, double size, double maxW) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String rest = s;
        while (rest.length() > 0) {
            int cut = rest.length();
            while (cut > 1 && c.textWidth(rest.substring(0, cut), size, Canvas.FONT_BOLD) > maxW) {
                int sp = rest.lastIndexOf(' ', cut - 1);
                cut = sp > 0 ? sp : cut - 1;
            }
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut).trim();
        }
        return out.toArray(new String[out.size()]);
    }

    private void drawResult(Canvas c) {
        c.fillRect(0, 0, width, height, 0x99000000);
        double pw = Math.min(width - 40, 600);
        double ph = Math.min(height - 40, 380);
        double px = (width - pw) / 2;
        double py = (height - ph) / 2;
        c.fillRoundRect(px + 4, py + 8, pw, ph, 28, 0x50000000);
        c.fillRoundRect(px, py, pw, ph, 28, 0xFFFFF8E7);
        c.strokeRoundRect(px + 8, py + 8, pw - 16, ph - 16, 22, 2, 0xFFE0C98A);
        boolean holed = game.state() == GameState.HOLED;
        String title = holed ? game.resultLabel() + "!" : "Runde beendet";
        double ts = Math.min(60, height * 0.12);
        while (c.textWidth(title, ts, Canvas.FONT_BOLD) > pw - 60 && ts > 20) {
            ts -= 2;
        }
        c.text(title, width / 2.0, py + ph * 0.27, ts, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, 0xFF1F5F30);
        String sub;
        if (holed) {
            sub = game.strokes() + (game.strokes() == 1 ? " Schlag" : " Schläge") + "  ·  Par " + level.par()
                    + "  ·  " + ScoreNames.relative(game.strokes(), level.par());
        } else if (game.strokes() >= Rules.MAX_STROKES) {
            sub = Rules.MAX_STROKES + " Schläge sind um – beim nächsten Mal klappt's!";
        } else {
            sub = "Aufgegeben nach " + game.strokes() + (game.strokes() == 1 ? " Schlag" : " Schlägen");
        }
        double ss = Math.min(28, height * 0.058);
        while (c.textWidth(sub, ss, Canvas.FONT_SANS) > pw - 40 && ss > 10) {
            ss -= 1;
        }
        c.text(sub, width / 2.0, py + ph * 0.43, ss, Canvas.FONT_SANS, Canvas.ALIGN_CENTER, 0xFF3A3A2E);
        ArgbImage name = level.nameImage();
        if (toastVisible()) {
            // Rückmeldung (z. B. "Urkunde gedruckt") im Ergebnisfeld statt des Namens
            drawToast(c, py + ph * 0.47, pw - 40, Math.min(20, height * 0.042));
        } else if (name != null) {
            double s = Math.min((pw * 0.5) / name.width(), (ph * 0.14) / name.height());
            double w = name.width() * s;
            double h = name.height() * s;
            c.image(name, width / 2.0 - w / 2, py + ph * 0.48, w, h);
        }
        printButton.draw(c, pressed == printButton);
        againButton.draw(c, pressed == againButton);
        if (newLevelEnabled) {
            newLevelButton.draw(c, pressed == newLevelButton);
        }
    }

    private static double dist(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Für Tests und Screenshots: Position der Schaltflächen. */
    public double[] printButtonCenter() {
        return new double[] {printButton.centerX(), printButton.centerY()};
    }

    public double[] againButtonCenter() {
        return new double[] {againButton.centerX(), againButton.centerY()};
    }

    public double[] newLevelButtonCenter() {
        return new double[] {newLevelButton.centerX(), newLevelButton.centerY()};
    }

    public boolean isResultVisible() {
        return resultVisible();
    }
}

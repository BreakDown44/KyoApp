package de.scangolf.core.render;

/**
 * Einfacher Hinweisbildschirm (z. B. "Scan nicht erkannt" mit den Gründen) und einer
 * großen Schaltfläche.
 */
public final class MessageScreen implements Screen {

    /** Wird beim Drücken der Schaltfläche aufgerufen. Ohne Beschriftung gibt es keine Schaltfläche. */
    public interface Listener {
        void onConfirm();
    }

    private final int width;
    private final int height;
    private final String title;
    private final String[] lines;
    private final Button button;
    private final Listener listener;
    private boolean pressed;

    public MessageScreen(int width, int height, String title, String[] lines, String buttonLabel, Listener l) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.lines = lines;
        this.button = buttonLabel == null ? null : new Button(buttonLabel, true);
        this.listener = l;
        if (button != null) {
            double bw = Math.min(320, width * 0.4);
            double bh = Math.max(64, height * 0.15);
            button.layout((width - bw) / 2, height - bh - height * 0.08, bw, bh);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void update(long nowMs) {
    }

    public void touch(int type, double x, double y) {
        if (button == null) {
            return;
        }
        if (type == Touch.DOWN) {
            pressed = button.hit(x, y);
        } else if (type == Touch.UP) {
            boolean fire = pressed && button.hit(x, y);
            pressed = false;
            if (fire && listener != null) {
                listener.onConfirm();
            }
        } else if (type == Touch.CANCEL) {
            pressed = false;
        }
    }

    public void render(Canvas c) {
        c.fillRect(0, 0, width, height, Colors.FRAME);
        double ts = Math.min(44, height * 0.09);
        c.text(title, width / 2.0, height * 0.2, ts, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, Colors.GOLD);
        double size = Math.min(26, height * 0.055);
        double y = height * 0.2 + ts * 1.4;
        double maxW = width * 0.84;
        for (int i = 0; i < lines.length; i++) {
            String rest = lines[i];
            while (rest.length() > 0) {
                int cut = rest.length();
                while (cut > 1 && c.textWidth(rest.substring(0, cut), size, Canvas.FONT_SANS) > maxW) {
                    int sp = rest.lastIndexOf(' ', cut - 1);
                    cut = sp > 0 ? sp : cut - 1;
                }
                c.text(rest.substring(0, cut), width / 2.0, y, size, Canvas.FONT_SANS, Canvas.ALIGN_CENTER, Colors.TEXT);
                y += size * 1.35;
                rest = rest.substring(cut).trim();
            }
            y += size * 0.4;
        }
        if (button != null) {
            button.draw(c, pressed);
        }
    }
}

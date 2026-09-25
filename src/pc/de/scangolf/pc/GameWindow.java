package de.scangolf.pc;

import de.scangolf.core.render.Screen;
import de.scangolf.core.render.Touch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Swing-Fenster als Ersatz für das Druckerdisplay: logische Größe (Standard 800 x 480)
 * wird mit festem Seitenverhältnis in das Fenster skaliert, Maus = Touch.
 */
public final class GameWindow extends JPanel {

    private static final long serialVersionUID = 1L;

    private transient Screen screen;
    private double scale = 1;
    private double offX;
    private double offY;
    private final long t0 = System.nanoTime();

    public GameWindow(Screen screen) {
        this.screen = screen;
        setPreferredSize(new Dimension(screen.width(), screen.height()));
        setBackground(Color.BLACK);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                send(Touch.DOWN, e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                send(Touch.MOVE, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                send(Touch.UP, e);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        new Timer(15, e -> {
            this.screen.update(nowMs());
            repaint();
        }).start();
    }

    private long nowMs() {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    /** Bildschirmkoordinate eines logischen Punkts (für automatisierte Tests mit Robot). */
    public java.awt.Point logicalToScreen(double x, double y) {
        java.awt.Point p = getLocationOnScreen();
        double sc = Math.min(getWidth() / (double) screen.width(), getHeight() / (double) screen.height());
        double ox = (getWidth() - screen.width() * sc) / 2;
        double oy = (getHeight() - screen.height() * sc) / 2;
        return new java.awt.Point((int) Math.round(p.x + ox + x * sc), (int) Math.round(p.y + oy + y * sc));
    }

    public void setScreen(Screen s) {
        this.screen = s;
        repaint();
    }

    private void send(int type, MouseEvent e) {
        screen.touch(type, (e.getX() - offX) / scale, (e.getY() - offY) / scale);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            scale = Math.min(getWidth() / (double) screen.width(), getHeight() / (double) screen.height());
            offX = (getWidth() - screen.width() * scale) / 2;
            offY = (getHeight() - screen.height() * scale) / 2;
            g.translate(offX, offY);
            g.clipRect(0, 0, (int) Math.ceil(screen.width() * scale), (int) Math.ceil(screen.height() * scale));
            screen.render(new Graphics2DCanvas(g, screen.width(), screen.height(), scale, true));
        } finally {
            g.dispose();
        }
    }

    public static GameWindow open(String title, Screen screen) {
        GameWindow[] holder = new GameWindow[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame f = new JFrame(title);
                GameWindow w = new GameWindow(screen);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                f.setContentPane(w);
                f.pack();
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                holder[0] = w;
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return holder[0];
    }
}

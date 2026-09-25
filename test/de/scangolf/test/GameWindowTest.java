package de.scangolf.test;

import de.scangolf.core.game.GameState;
import de.scangolf.core.level.Level;
import de.scangolf.core.render.GameScreen;
import de.scangolf.pc.GameWindow;
import de.scangolf.pc.Images;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.io.File;
import javax.swing.SwingUtilities;

/**
 * Das echte Swing-Fenster mit Maus bedienen (braucht ein Display; build.sh nutzt xvfb-run,
 * wenn vorhanden). Ohne Display: SKIP.
 */
public class GameWindowTest {

    public void testWindowIsPlayableWithMouse() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new TestSkipped("kein Display (xvfb-run installieren oder am Desktop ausführen)");
        }
        Level l = TestLevels.load("beispiel");
        GameScreen screen = new GameScreen(l, 800, 480, null);
        GameWindow w = GameWindow.open("ScanGolf-Test", screen);
        Window frame = SwingUtilities.getWindowAncestor(w);
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(15);
            Thread.sleep(500);
            // Fenster vergrößern: logische 800x480 werden skaliert
            SwingUtilities.invokeAndWait(() -> frame.setBounds(0, 0, 1100, 700));
            Thread.sleep(500);
            Point ball = w.logicalToScreen(screen.toScreenX(l.startX()), screen.toScreenY(l.startY()));
            Point pull = w.logicalToScreen(screen.toScreenX(l.startX()) - 20, screen.toScreenY(l.startY()) + 110);
            robot.mouseMove(ball.x, ball.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (int i = 1; i <= 10; i++) {
                robot.mouseMove(ball.x + (pull.x - ball.x) * i / 10, ball.y + (pull.y - ball.y) * i / 10);
            }
            Thread.sleep(150);
            Rectangle r = new Rectangle(w.getLocationOnScreen(), w.getSize());
            Images.savePng(robot.createScreenCapture(r), new File(Check.root().toFile(), "build/visual/fenster-zielen.png"));
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            long t0 = System.currentTimeMillis();
            while (screen.game().strokes() == 0 && System.currentTimeMillis() - t0 < 2000) {
                Thread.sleep(20);
            }
            Check.equal(1, screen.game().strokes(), "Mausziehen hat einen Schlag ausgelöst");
            Check.isTrue(screen.game().ballVY() < 0 || screen.game().ballY() < l.startY(), "Schuss nach oben (entgegen dem Ziehen)");
            while (screen.game().state() == GameState.ROLLING && System.currentTimeMillis() - t0 < 8000) {
                Thread.sleep(50);
            }
            Check.isTrue(screen.game().state() != GameState.ROLLING, "Kugel kommt in Echtzeit zur Ruhe");
            Images.savePng(robot.createScreenCapture(r), new File(Check.root().toFile(), "build/visual/fenster-nach-schlag.png"));
            Check.info(String.format("Fenster %dx%d, Kugel nach Schlag bei %.1f/%.1f mm", w.getWidth(), w.getHeight(),
                    screen.game().ballX(), screen.game().ballY()));
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}

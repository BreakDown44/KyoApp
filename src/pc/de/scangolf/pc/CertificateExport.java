package de.scangolf.pc;

import de.scangolf.core.cert.Certificate;
import de.scangolf.core.cert.CertificateRenderer;
import de.scangolf.core.game.Game;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Urkunde als PNG (Standard 300 dpi) – PC-Ersatz für den Druck. */
public final class CertificateExport {

    private CertificateExport() {
    }

    public static BufferedImage render(Certificate cert, double dpi) {
        double pxPerMm = dpi / 25.4;
        return Offscreen.render(CertificateRenderer.PAGE_W, CertificateRenderer.PAGE_H, pxPerMm,
                c -> CertificateRenderer.render(c, cert));
    }

    public static Certificate fromGame(Game g) {
        LocalDate d = LocalDate.now();
        return Certificate.fromGame(g, d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    /** Speichert die Urkunde in dir und liefert die Datei. */
    public static File save(Game g, File dir) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File f = new File(dir, "urkunde-" + stamp + ".png");
        Images.savePng(render(fromGame(g), 300), f, 300);
        return f;
    }
}

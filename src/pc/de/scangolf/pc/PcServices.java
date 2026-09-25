package de.scangolf.pc;

import de.scangolf.core.app.DateSource;
import de.scangolf.core.app.PrintService;
import de.scangolf.core.app.ScanService;
import de.scangolf.core.cert.Certificate;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** PC-Umsetzungen der Plattform-Adapter: Bilddatei statt Scanner, PNG statt Drucker. */
public final class PcServices {

    private PcServices() {
    }

    /** "Scannt", indem eine Bilddatei geladen wird (fest vorgegeben oder per Dateiauswahl). */
    public static final class FileScan implements ScanService {
        private File next;

        public FileScan(File first) {
            this.next = first;
        }

        @Override
        public void requestScan(Callback cb) {
            File f = next;
            next = null;
            if (f == null) {
                JFileChooser ch = new JFileChooser(new File(System.getProperty("scangolf.root", "."), "testbilder/out"));
                ch.setFileFilter(new FileNameExtensionFilter("Scans (PNG, JPEG)", "png", "jpg", "jpeg"));
                if (ch.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                    cb.failed("Kein Bild ausgewählt.");
                    return;
                }
                f = ch.getSelectedFile();
            }
            try {
                Images.Raw raw = Images.load(f);
                System.out.println("Scan: " + f);
                cb.scanned(raw.argb, raw.width, raw.height);
            } catch (IOException e) {
                cb.failed("Bild nicht lesbar: " + e.getMessage());
            }
        }
    }

    /** "Druckt" die Urkunde als PNG mit 300 dpi in einen Ordner. */
    public static final class PngPrint implements PrintService {
        private final File dir;

        public PngPrint(File dir) {
            this.dir = dir;
        }

        @Override
        public void print(Certificate c, Callback cb) {
            try {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                File f = new File(dir, "urkunde-" + stamp + ".png");
                Images.savePng(CertificateExport.render(c, 300), f, 300);
                System.out.println("Urkunde gespeichert: " + f);
                cb.printed("Urkunde gespeichert: " + f.getName());
            } catch (IOException e) {
                cb.failed(e.getMessage());
            }
        }
    }

    /** Systemdatum. */
    public static final class Today implements DateSource {
        @Override
        public int[] today() {
            LocalDate d = LocalDate.now();
            return new int[] {d.getYear(), d.getMonthValue(), d.getDayOfMonth()};
        }
    }
}

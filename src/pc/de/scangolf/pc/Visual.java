package de.scangolf.pc;

import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanResult;

import java.io.File;
import java.io.IOException;

/** Erzeugt Kontrollbilder für die Sichtprüfung (Scan-Debug, Spiel-Screenshots, Urkunde). */
public final class Visual {

    private Visual() {
    }

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "build/visual");
        File root = new File(System.getProperty("scangolf.root", "."));
        File imgs = new File(root, "testbilder/out");
        String[] scans = {
            "beispiel-300dpi.png", "beispiel-rot90.png", "beispiel-schief+4.png", "beispiel-kombi.jpg",
            "beispiel-150dpi.png", "bahn-schraeg.png", "bahn-duenn.png", "bahn-luecken.png",
            "bahn-gewunden.png", "bahn-wasser.png", "bahn-start-nah-wand.png", "neg-start-in-wand.png",
            "neg-loch-eingemauert.png", "neg-ecke-abgeschnitten.png", "leer-gelb-sehr-dunkel.png",
            "neg-foto.png",
        };
        ScanAnalyzer an = new ScanAnalyzer();
        for (String s : scans) {
            File f = new File(imgs, s);
            if (!f.exists()) {
                System.err.println("fehlt: " + f);
                continue;
            }
            Images.Raw raw = Images.load(f);
            ScanResult r = an.analyze(raw.argb, raw.width, raw.height);
            File o = new File(out, "scan-" + s.replaceFirst("\\.[^.]+$", "") + ".png");
            Images.savePng(Main.debugImage(raw, r, an), o);
            System.out.println(o + "  " + (r.isOk() ? "OK" : r.errors()));
        }
    }
}

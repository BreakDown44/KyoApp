package de.scangolf.core.scan;

import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Level;
import de.scangolf.core.util.ArgbImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis der Scan-Auswertung: entweder ein Level oder eine Liste von Fehlern, dazu
 * Warnungen und Diagnosedaten (für Debug-Bild und Tests).
 */
public final class ScanResult {

    Level level;
    final List<ScanError> errors = new ArrayList<ScanError>();
    final List<ScanWarning> warnings = new ArrayList<ScanWarning>();
    String detail = "";

    int imageWidth;
    int imageHeight;
    int workFactor;
    List<FinderPattern> finderCandidates = new ArrayList<FinderPattern>();
    FinderPattern tl;
    FinderPattern tr;
    FinderPattern bl;
    Affine pageToImage;
    double blockPredX = Double.NaN;
    double blockPredY = Double.NaN;
    double blockFoundX = Double.NaN;
    double blockFoundY = Double.NaN;
    double blockResidualMm = Double.NaN;
    int paperRgb;
    Grid grid;
    double[] start;
    double[] startDrawn;
    double[] hole;
    double[] parFill;
    int par;
    ArgbImage nameImage;
    ArgbImage laneImage;
    long msMarks;
    long msGrid;
    long msTotal;
    long workingBytes;

    ScanResult() {
    }

    void error(ScanError e) {
        if (!errors.contains(e)) {
            errors.add(e);
        }
    }

    void warn(ScanWarning w) {
        if (!warnings.contains(w)) {
            warnings.add(w);
        }
    }

    public boolean isOk() {
        return level != null && errors.isEmpty();
    }

    /** Das erkannte Level oder null, wenn Fehler auftraten. */
    public Level level() {
        return level;
    }

    public List<ScanError> errors() {
        return new ArrayList<ScanError>(errors);
    }

    public List<ScanWarning> warnings() {
        return new ArrayList<ScanWarning>(warnings);
    }

    public boolean hasError(ScanError e) {
        return errors.contains(e);
    }

    public boolean hasWarning(ScanWarning w) {
        return warnings.contains(w);
    }

    /** Zusätzliche technische Information (z. B. "gespiegelt"). */
    public String detail() {
        return detail;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public int imageHeight() {
        return imageHeight;
    }

    /** Verkleinerungsfaktor des Arbeitsbildes für die Markensuche. */
    public int workFactor() {
        return workFactor;
    }

    /** Alle Suchmuster-Kandidaten, Koordinaten in Pixeln des Vollbildes. */
    public List<double[]> finderCandidatesImagePx() {
        List<double[]> l = new ArrayList<double[]>();
        for (int i = 0; i < finderCandidates.size(); i++) {
            FinderPattern f = finderCandidates.get(i);
            l.add(new double[] {f.x * workFactor, f.y * workFactor, f.module * workFactor, f.count});
        }
        return l;
    }

    /** Abbildung Seiten-mm auf Bildpixel, oder null wenn die Marken nicht gefunden wurden. */
    public Affine pageToImage() {
        return pageToImage;
    }

    public double blockPredictedX() {
        return blockPredX;
    }

    public double blockPredictedY() {
        return blockPredY;
    }

    public double blockFoundX() {
        return blockFoundX;
    }

    public double blockFoundY() {
        return blockFoundY;
    }

    /** Abstand gefundener zu vorhergesagtem Block in mm (NaN, wenn nicht geprüft). */
    public double blockResidualMm() {
        return blockResidualMm;
    }

    public double pixelsPerMm() {
        return pageToImage == null ? Double.NaN : pageToImage.scale();
    }

    public double dpi() {
        return pixelsPerMm() * 25.4;
    }

    public double rotationDegrees() {
        return pageToImage == null ? Double.NaN : pageToImage.rotationDegrees();
    }

    /** Geschätztes Papierweiß als RGB. */
    public int paperRgb() {
        return paperRgb;
    }

    /** Klassifiziertes Raster (auch bei Fehlern vorhanden, sobald das Blatt erkannt wurde). */
    public Grid grid() {
        return grid == null ? null : grid.copy();
    }

    /** Start in Feld-mm (ggf. verschoben), oder null. */
    public double[] start() {
        return start == null ? null : new double[] {start[0], start[1]};
    }

    /** Mittelpunkt des gezeichneten roten Punkts in Feld-mm, oder null. */
    public double[] startDrawn() {
        return startDrawn == null ? null : new double[] {startDrawn[0], startDrawn[1]};
    }

    public double[] hole() {
        return hole == null ? null : new double[] {hole[0], hole[1]};
    }

    /** Füllgrad je Par-Kästchen (Reihenfolge wie in der Vorlage), oder null. */
    public double[] parFill() {
        if (parFill == null) {
            return null;
        }
        double[] c = new double[parFill.length];
        System.arraycopy(parFill, 0, c, 0, c.length);
        return c;
    }

    public int par() {
        return par;
    }

    public ArgbImage nameImage() {
        return nameImage;
    }

    public ArgbImage laneImage() {
        return laneImage;
    }

    public long millisMarks() {
        return msMarks;
    }

    public long millisGrid() {
        return msGrid;
    }

    public long millisTotal() {
        return msTotal;
    }

    /** Summe der Arbeitsspeicher-Puffer, die die Auswertung angelegt hat (Bytes). */
    public long workingBytes() {
        return workingBytes;
    }

    /** Kurzer deutscher Text für Menschen (Display/Konsole). */
    public String summary() {
        StringBuffer sb = new StringBuffer();
        if (isOk()) {
            sb.append("Bahn erkannt: Par ").append(level.par());
        } else {
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(errors.get(i).message());
            }
        }
        for (int i = 0; i < warnings.size(); i++) {
            sb.append('\n').append("Hinweis: ").append(warnings.get(i).message());
        }
        return sb.toString();
    }
}

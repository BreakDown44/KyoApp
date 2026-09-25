package de.scangolf.core.scan;

/** Fehler der Scan-Auswertung: Code plus kurzer Text für das Druckerdisplay. */
public enum ScanError {

    IMAGE_TOO_SMALL("Das Bild ist zu klein. Bitte mit mindestens 150 dpi scannen."),
    SHEET_NOT_FOUND("Blatt nicht erkannt. Bitte die ScanGolf-Vorlage gerade und vollständig auflegen."),
    MARKS_INCOMPLETE("Eine Ecke der Vorlage fehlt oder ist verdeckt. Bitte alle vier Ecken frei lassen."),
    FIELD_EMPTY("Auf dem Blatt ist noch keine Bahn gezeichnet."),
    GRAYSCALE_SCAN("Der Scan ist schwarzweiß. Bitte in Farbe scannen, damit Start, Loch und Wasser erkannt werden."),
    NO_START("Kein Startpunkt gefunden. Bitte einen roten Punkt zeichnen."),
    MULTIPLE_STARTS("Bitte nur einen roten Punkt zeichnen."),
    NO_HOLE("Kein Loch gefunden. Bitte einen grünen Punkt zeichnen."),
    MULTIPLE_HOLES("Bitte nur einen grünen Punkt (das Loch) zeichnen."),
    START_IN_WALL("Der rote Startpunkt liegt in oder zu dicht an einer Wand."),
    START_IN_WATER("Der rote Startpunkt liegt im Wasser."),
    HOLE_IN_WALL("Das grüne Loch liegt in einer Wand."),
    HOLE_IN_WATER("Das grüne Loch liegt im Wasser."),
    HOLE_UNREACHABLE("Das Loch ist vom Start aus nicht erreichbar. Ist es eingemauert oder ein Weg schmaler als 1 cm?");

    private final String message;

    ScanError(String message) {
        this.message = message;
    }

    /** Kurzer deutscher Text für Menschen. */
    public String message() {
        return message;
    }
}

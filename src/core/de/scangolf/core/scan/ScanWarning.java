package de.scangolf.core.scan;

/** Hinweise, die das Spielen nicht verhindern. */
public enum ScanWarning {

    PAR_NOT_MARKED("Kein Par angekreuzt – es gilt Par 3."),
    PAR_MULTIPLE("Mehrere Par-Kästchen angekreuzt – das kleinste gilt."),
    START_MOVED("Der Startpunkt lag zu dicht an der Wand und wurde etwas verschoben."),
    START_NEAR_HOLE("Start und Loch liegen sehr dicht beieinander."),
    FAINT_LINES("Einige Linien sind sehr hell und zählen nicht als Wand. Bitte mit dunklem Stift nachzeichnen."),
    BLOCK_OFFSET("Das Blatt ist leicht verzerrt gescannt; die Erkennung kann ungenau sein.");

    private final String message;

    ScanWarning(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}

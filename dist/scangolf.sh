#!/bin/sh
# ScanGolf – PC-Testumgebung starten (Java 11 oder neuer nötig).
#   ./scangolf.sh run vorlage/beispiel-scan-300dpi.png   Scan auswerten und spielen
#   ./scangolf.sh run levels/gewunden.json               gespeichertes Level spielen
#   ./scangolf.sh run                                    Startbildschirm, Scan per Dateiauswahl
#   ./scangolf.sh analyze scan.png                       Auswertung + Debug-Bild + Leveldatei
#   ./scangolf.sh batch ordner/                          alle Bilder eines Ordners auswerten
DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -Xmx2g -Dscangolf.root="$DIR" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "$DIR/scangolf-pc.jar" "$@"

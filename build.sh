#!/usr/bin/env bash
# ScanGolf – Build ohne Gradle/Maven, nur javac/jar.
#
#   ./build.sh compile            Kern + PC + Tests kompilieren (inkl. Kern-Regelprüfung)
#   ./build.sh test               kompilieren, Testbilder erzeugen (falls nötig), alle Tests
#   ./build.sh run <scan|level>   spielbares Swing-Fenster (800x480, skalierbar)
#   ./build.sh analyze <scan.png> Scan auswerten, Debug-Bild + Level-Datei schreiben
#   ./build.sh visual             Debug-Bilder, Spiel-Screenshots, Urkunde nach build/visual
#   ./build.sh batch <ordner>     alle Scans eines Ordners auswerten (Tabelle + Debug-Bilder)
#   ./build.sh grenzen            härtere Varianten erzeugen und auswerten (Grenzen der Erkennung)
#   ./build.sh testimages         Testbilder neu erzeugen (testbilder/out)
#   ./build.sh template           Vorlage neu erzeugen (vorlage/out)
#   ./build.sh jar                build/scangolf-core.jar und build/scangolf-pc.jar
#   ./build.sh dist               PC-Paket (JARs, Startskripte, Vorlage, Level) + Quellcode als ZIP
#   ./build.sh clean              build/ löschen
#   ./build.sh all                clean, compile, test, jar
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"
BUILD="$ROOT/build"

# JAVA_TOOL_OPTIONS-Hinweise der JVM ("Picked up ...") nicht in jede Ausgabe mischen.
quiet_java_filter() { grep -v '^Picked up JAVA_TOOL_OPTIONS' || true; }
# javac mit gefilterter Ausgabe; Rückgabewert ist der von javac (pipefail).
jc() { javac "$@" 2>&1 | quiet_java_filter; }

# Kern: Java-8-API, alle Lint-Warnungen sind Fehler. "-Xlint:-options" ist nötig,
# weil neuere JDKs sonst "release 8 is obsolete" warnen und -Werror daran scheitert.
CORE_FLAGS=(--release 8 -encoding UTF-8 -Xlint:all -Xlint:-options -Werror)
# Zweiter Kern-Durchlauf nur mit java.base: jeder AWT/Swing/ImageIO-Import ist ein Fehler.
CORE_CHECK_FLAGS=(--release 11 --limit-modules java.base -encoding UTF-8 -Xlint:all -Werror)
PC_FLAGS=(--release 11 -encoding UTF-8 -Xlint:all -Werror)

core_sources() { find src/core -name '*.java' | sort; }
pc_sources()   { find src/pc -name '*.java' | sort; }
test_sources() { find test -name '*.java' | sort; }

check_core_rules() {
    # Verbotene Sprachmittel/APIs im Kern (siehe README, "Kern-Regeln").
    local bad=0
    local patterns=(
        'java\.awt' 'javax\.' 'java\.nio' 'java\.util\.function' 'java\.util\.stream'
        'java\.lang\.reflect' 'java\.time' '\.stream\(\)' '->' '::' 'Optional'
        'String\.join' '\bvar\b' '\brecord\b' '\bThread\b' 'Runnable' 'synchronized'
        'StringBuilder' 'String\.format' 'Arrays\.copyOf' '<>' 'try \(' '@Override'
        'System\.nanoTime' 'Math\.hypot'
    )
    for p in "${patterns[@]}"; do
        # Kommentare (// und * am Zeilenanfang) werden nicht geprüft.
        local hits
        hits=$(grep -rnP --include="*.java" -e "$p" src/core \
               | grep -vP '^[^:]+:\d+:\s*(//|\*|/\*)' || true)
        if [[ -n "$hits" ]]; then
            echo "Kern-Regel verletzt ($p):" >&2
            echo "$hits" >&2
            bad=1
        fi
    done
    if [[ $bad -ne 0 ]]; then
        echo "FEHLER: Kern enthält verbotene Konstrukte." >&2
        exit 1
    fi
}

compile_core() {
    rm -rf "$BUILD/core" "$BUILD/core-check"
    mkdir -p "$BUILD/core" "$BUILD/core-check"
    check_core_rules
    jc "${CORE_FLAGS[@]}" -d "$BUILD/core" $(core_sources) \
        || { echo "Kern-Kompilierung fehlgeschlagen" >&2; exit 1; }
    jc "${CORE_CHECK_FLAGS[@]}" -d "$BUILD/core-check" $(core_sources) \
        || { echo "Kern nutzt APIs außerhalb von java.base (z. B. AWT)" >&2; exit 1; }
    rm -rf "$BUILD/core-check"
    # Vorlagen-Geometrie als Ressource in den Kern legen.
    mkdir -p "$BUILD/core/de/scangolf/core/scan"
    cp vorlage/out/template.json "$BUILD/core/de/scangolf/core/scan/template.json"
}

compile_pc() {
    rm -rf "$BUILD/pc"
    mkdir -p "$BUILD/pc"
    jc "${PC_FLAGS[@]}" -cp "$BUILD/core" -d "$BUILD/pc" $(pc_sources) \
        || { echo "PC-Kompilierung fehlgeschlagen" >&2; exit 1; }
}

compile_test() {
    rm -rf "$BUILD/test"
    mkdir -p "$BUILD/test"
    jc "${PC_FLAGS[@]}" -cp "$BUILD/core:$BUILD/pc" -d "$BUILD/test" $(test_sources) \
        || { echo "Test-Kompilierung fehlgeschlagen" >&2; exit 1; }
}

compile_all() {
    compile_core
    compile_pc
    compile_test
    echo "Kompiliert: Kern ($(core_sources | wc -l) Dateien), PC ($(pc_sources | wc -l)), Tests ($(test_sources | wc -l))"
}

CP="$BUILD/core:$BUILD/pc"
JAVA_OPTS=(-Xmx2g -Dscangolf.root="$ROOT" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8)

run_java() {
    java "${JAVA_OPTS[@]}" "$@" 2> >(quiet_java_filter >&2)
}

ensure_testimages() {
    if [[ ! -f testbilder/out/manifest.json || testbilder/generate_testbilder.py -nt testbilder/out/manifest.json \
          || vorlage/generate.py -nt testbilder/out/manifest.json ]]; then
        echo "Erzeuge Testbilder ..."
        python3 testbilder/generate_testbilder.py
    fi
}

cmd="${1:-all}"
shift || true
case "$cmd" in
    compile)
        compile_all
        ;;
    test)
        compile_all
        ensure_testimages
        classes=$(cd "$BUILD/test" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort)
        # Fenstertest braucht ein Display: ohne DISPLAY virtuell per xvfb-run, sonst SKIP.
        if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run >/dev/null 2>&1; then
            xvfb-run -a -s "-screen 0 1280x800x24" java "${JAVA_OPTS[@]}" -cp "$CP:$BUILD/test" \
                de.scangolf.test.TestRunner $classes 2> >(quiet_java_filter >&2)
        else
            run_java -cp "$CP:$BUILD/test" de.scangolf.test.TestRunner $classes
        fi
        ;;
    run)
        [[ $# -ge 1 ]] || { echo "Aufruf: ./build.sh run <scan.png|level.json>" >&2; exit 2; }
        compile_core; compile_pc
        run_java -cp "$CP" de.scangolf.pc.Main run "$@"
        ;;
    analyze)
        [[ $# -ge 1 ]] || { echo "Aufruf: ./build.sh analyze <scan.png> [ausgabeordner]" >&2; exit 2; }
        compile_core; compile_pc
        run_java -cp "$CP" de.scangolf.pc.Main analyze "$@"
        ;;
    batch)
        [[ $# -ge 1 ]] || { echo "Aufruf: ./build.sh batch <ordner> [--ohne-debug]" >&2; exit 2; }
        compile_core; compile_pc
        run_java -Djava.awt.headless=true -cp "$CP" de.scangolf.pc.Main batch "$@"
        ;;
    grenzen)
        ensure_testimages
        python3 testbilder/generate_grenzen.py
        compile_core; compile_pc
        run_java -Djava.awt.headless=true -cp "$CP" de.scangolf.pc.Main batch testbilder/out/grenzen
        ;;
    visual)
        compile_core; compile_pc
        ensure_testimages
        run_java -Djava.awt.headless=true -cp "$CP" de.scangolf.pc.Main visual "$BUILD/visual" "$@"
        ;;
    testimages)
        python3 testbilder/generate_testbilder.py
        ;;
    template)
        python3 vorlage/generate.py
        ;;
    jar)
        compile_core; compile_pc
        jar cf "$BUILD/scangolf-core.jar" -C "$BUILD/core" .
        # Class-Path im Manifest: "java -jar scangolf-pc.jar" findet den Kern daneben.
        printf 'Main-Class: de.scangolf.pc.Main\nClass-Path: scangolf-core.jar\n' > "$BUILD/pc-manifest.txt"
        jar cfm "$BUILD/scangolf-pc.jar" "$BUILD/pc-manifest.txt" -C "$BUILD/pc" .
        echo "Jars: $BUILD/scangolf-core.jar $BUILD/scangolf-pc.jar"
        ;;
    dist)
        # Lauffähiges PC-Paket + Quellcode als ZIP nach build/dist
        "$0" jar
        D="$BUILD/dist/scangolf-pc"
        rm -rf "$BUILD/dist"
        mkdir -p "$D/vorlage" "$D/levels"
        cp "$BUILD/scangolf-core.jar" "$BUILD/scangolf-pc.jar" "$D/"
        cp vorlage/out/scangolf-vorlage.pdf vorlage/out/scangolf-beispiel.pdf \
           vorlage/out/beispiel-scan-300dpi.png "$D/vorlage/"
        cp test/levels/*.json "$D/levels/"
        cp dist/LIESMICH.txt dist/scangolf.sh dist/scangolf.bat "$D/"
        chmod +x "$D/scangolf.sh"
        (cd "$BUILD/dist" && zip -qr scangolf-pc.zip scangolf-pc)
        git archive --format=zip --prefix=scangolf/ -o "$BUILD/dist/scangolf-quellcode.zip" HEAD
        ls -la "$BUILD/dist"
        ;;
    clean)
        rm -rf "$BUILD"
        ;;
    all)
        rm -rf "$BUILD"
        "$0" test
        "$0" jar
        ;;
    *)
        sed -n '2,17p' "$0"
        exit 2
        ;;
esac

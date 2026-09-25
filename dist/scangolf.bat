@echo off
rem ScanGolf - PC-Testumgebung starten (Java 11 oder neuer noetig).
rem   scangolf.bat run vorlage\beispiel-scan-300dpi.png
rem   scangolf.bat run levels\gewunden.json
rem   scangolf.bat analyze scan.png
set DIR=%~dp0
java -Xmx2g -Dscangolf.root="%DIR%." -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%DIR%scangolf-pc.jar" %*

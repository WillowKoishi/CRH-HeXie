@echo off
REM Hexie Physics Train - type check only (no jar)
setlocal
set "JAVA_HOME=C:\Users\PC\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
call "%~dp0.tools\gradle-8.10.2\bin\gradle.bat" compileJava --offline --console=plain --no-daemon %*
endlocal
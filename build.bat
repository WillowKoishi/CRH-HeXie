@echo off
REM Hexie Physics Train - offline build (compiles against the local PCL instance)
REM Usage:  build.bat build --offline
setlocal
set "JAVA_HOME=C:\Users\PC\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
call "%~dp0.tools\gradle-8.10.2\bin\gradle.bat" %* --console=plain
endlocal
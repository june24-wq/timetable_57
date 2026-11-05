@echo off
rem ============================================================
rem  Timetable CLI - build and run (one click)
rem
rem  Usage:
rem    run.bat              compile, then show current week timetable
rem    run.bat all 9        compile, then show week 9 timetable
rem    run.bat day 1        compile, then show Monday timetable
rem
rem  Notes:
rem    1. The script switches to its own directory, so it can be
rem       double-clicked from anywhere.
rem    2. "chcp 65001" makes the console use UTF-8 so that the
rem       Chinese output of the Java program is printed correctly.
rem    3. -sourcepath lets javac resolve dependencies by itself,
rem       which avoids an argument file with non-ASCII paths.
rem    4. This file is intentionally ASCII-only: mixing non-ASCII
rem       characters with "chcp" inside a .bat may corrupt the
rem       remaining lines when cmd.exe re-reads the file.
rem ============================================================
setlocal
cd /d "%~dp0"
chcp 65001 >nul

if not exist "out" mkdir "out"

echo [1/2] Compiling sources under src ...
javac -encoding UTF-8 -sourcepath "src" -d "out" "src\timetable\Main.java"
if errorlevel 1 (
    echo.
    echo [FAILED] Compilation error, please check the messages above.
    exit /b 1
)
echo [2/2] Compile OK, running ...
echo.

if "%~1"=="" (
    java -Dfile.encoding=UTF-8 -cp "out" timetable.Main current
) else (
    java -Dfile.encoding=UTF-8 -cp "out" timetable.Main %*
)

endlocal

@echo off
rem ============================================================
rem  Timetable CLI - compile only (no run)
rem  Output class files go to the "out" directory.
rem ============================================================
setlocal
cd /d "%~dp0"
chcp 65001 >nul

if not exist "out" mkdir "out"

echo Compiling sources under src ...
javac -encoding UTF-8 -sourcepath "src" -d "out" "src\timetable\Main.java"
if errorlevel 1 (
    echo.
    echo [FAILED] Compilation error.
    exit /b 1
)
echo [OK] Compile finished, class files are in the "out" directory.
echo Example: java -cp out timetable.Main all 9

endlocal

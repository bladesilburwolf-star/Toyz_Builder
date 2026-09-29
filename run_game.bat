@echo off
cd /d "%~dp0"

REM Kill leftover dumps that can eat hundreds of MB
if exist "*.hprof" (
    echo Removing old Java heap dumps...
    del /q "*.hprof" 2>nul
)

echo Building ToyzBuilderWorldGen...
call gradlew.bat build --quiet
if %errorlevel% neq 0 (
    echo.
    echo Build FAILED. Re-run with: gradlew.bat build
    echo to see the full error output.
    pause
    exit /b %errorlevel%
)

echo.
echo Running ToyzBuilderWorldGen  (heap capped at 512MB, no hprof dumps)
echo Close the game window to return here.
echo.

REM No console redirect — nothing writes run.log
set JAVA_TOOL_OPTIONS=
call gradlew.bat run

echo.
echo Game exited with code %errorlevel%.
pause

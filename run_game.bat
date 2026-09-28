@echo off
cd /d "%~dp0"

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
echo Running ToyzBuilderWorldGen...
echo Close the game window to return here.
echo.

REM No redirect — console stays live; nothing writes run.log
call gradlew.bat run

echo.
echo Game exited with code %errorlevel%.
pause

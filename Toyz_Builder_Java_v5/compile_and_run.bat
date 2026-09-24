@echo off
setlocal
REM ============================================================
REM  Toyz Builder Engine (Java) - compile, run, and error log
REM  Usage:  compile_and_run.bat        (compile + run)
REM          compile_and_run.bat build  (compile only)
REM
REM  Requires a Gradle wrapper (gradlew.bat) or Gradle installed
REM  on PATH. To generate the wrapper once, run:
REM      gradle wrapper
REM
REM  Logs:   build_log.txt    (full gradle output)
REM          build_errors.txt (compile errors only, if any)
REM ============================================================

cd /d "%~dp0"
set LOG=build_log.txt
set ERRLOG=build_errors.txt

echo [%date% %time%] Build started > %LOG%

REM --- pick the Gradle command: wrapper first, then PATH install ---
set GRADLE_CMD=
if exist "gradlew.bat" (
    set GRADLE_CMD=gradlew.bat
) else (
    where gradle >nul 2>&1
    if not errorlevel 1 set GRADLE_CMD=gradle
)

if "%GRADLE_CMD%"=="" (
    echo [ERROR] No Gradle found. > %LOG%
    echo.
    echo [ERROR] No Gradle found.
    echo.
    echo Fix it ONE of these ways:
    echo   1. Install Gradle and make sure "gradle" is on your PATH,
    echo      then run this bat again.
    echo   2. Better: with Gradle installed, run "gradle wrapper" once in
    echo      this folder. That creates gradlew.bat so the project is
    echo      self-contained (no Gradle install needed afterwards).
    echo.
    pause
    exit /b 1
)

echo Using Gradle command: %GRADLE_CMD% >> %LOG%

if "%~1"=="build" (
    echo === Compiling (compileJava) ===
    call %GRADLE_CMD% --console=plain --no-daemon compileJava >> %LOG% 2>&1
) else (
    echo === Compiling and running ===
    call %GRADLE_CMD% --console=plain --no-daemon run >> %LOG% 2>&1
)

if errorlevel 1 (
    echo.
    echo *** BUILD FAILED - extracting errors to %ERRLOG% ***
    REM Pull out the "error:"/"warning:" lines plus their file:line headers
    findstr /C:"error:" /C:"warning:" /C:".java:" %LOG% > %ERRLOG%
    type %ERRLOG%
    echo.
    echo Full log saved to %LOG%. Fix the errors and re-run.
    pause
    exit /b 1
)

if "%~1"=="build" (
    echo Build succeeded. Run without the "build" argument to start the game.
) else (
    echo Game exited normally.
)
echo Full output saved to %LOG%.
pause
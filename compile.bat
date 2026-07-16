@echo off
set "JAVA_HOME="
echo ===================================================
echo   Compiling CloudStream Desktop (Standalone EXE)
echo ===================================================
echo.

IF NOT EXIST "android-reference\settings.gradle.kts" (
    echo [INFO] android-reference submodule is empty. Attempting to fetch automatically...
    git submodule update --init --recursive
    IF NOT EXIST "android-reference\settings.gradle.kts" (
        echo.
        echo [FATAL ERROR] The android-reference folder is STILL empty!
        echo This happens because you downloaded this repository as a ZIP file from GitHub.
        echo GitHub ZIP downloads DO NOT include submodules.
        echo.
        echo PLEASE DELETE THIS FOLDER AND USE THIS EXACT COMMAND IN YOUR TERMINAL:
        echo git clone --recursive https://github.com/YourUsername/cloudstream-windows.git
        echo.
        pause
        exit /b 1
    )
)

call gradlew :desktop-app:createDistributable

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Compilation complete.
echo.
echo   launch.bat         - dev mode with console logs (recommended while developing)
echo   launch-release.bat - packaged .exe (no console)
pause

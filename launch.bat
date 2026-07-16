@echo off
set "JAVA_HOME="
cd /d "%~dp0"
echo ===================================================
echo   CloudStream Desktop (DEV - console logs visible)
echo ===================================================
echo.
echo Running via Gradle. Logs print in this window.
echo For packaged .exe without console, use launch-release.bat
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

call gradlew :desktop-app:run
echo.
echo App exited with code %errorlevel%
pause

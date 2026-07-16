@echo off
set "JAVA_HOME="
cd /d "%~dp0"
echo ===================================================
echo   Launching CloudStream Desktop (release .exe)
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

set EXE_PATH=desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\CloudStream-Desktop.exe

if not exist "%EXE_PATH%" (
    echo [ERROR] Executable not found at %EXE_PATH%
    echo Please run compile.bat first to build the application.
    pause
    exit /b 1
)

echo Starting CloudStream-Desktop.exe...
start "" "%EXE_PATH%"

@echo off
setlocal enabledelayedexpansion
set "JAVA_HOME="
cd /d "%~dp0"

:: ── CLI Argument Dispatch ───────────────────────────────────────
if /i "%~1"=="dev" goto :run_dev
if /i "%~1"=="release" goto :run_release
if /i "%~1"=="build" goto :run_build
if /i "%~1"=="compile" goto :run_build
if /i "%~1"=="test" goto :run_test
if /i "%~1"=="start" goto :run_normal

:menu
cls
echo ===================================================
echo           CloudStream Desktop Launcher
echo ===================================================
echo.
echo   [1] Start CloudStream (Default Dev Mode)
echo   [2] Start with Dev Studio ^& Live LogCat (--dev)
echo   [3] Launch Packaged Release EXE
echo   [4] Build / Compile Standalone EXE
echo   [5] Run All Tests ^& Health Checks
echo.
echo   [Q] Exit
echo.
echo ===================================================
set /p "CHOICE=Select option [1-5] (Default is 1): "

if "%CHOICE%"=="" goto :run_normal
if /i "%CHOICE%"=="1" goto :run_normal
if /i "%CHOICE%"=="2" goto :run_dev
if /i "%CHOICE%"=="3" goto :run_release
if /i "%CHOICE%"=="4" goto :run_build
if /i "%CHOICE%"=="5" goto :run_test
if /i "%CHOICE%"=="q" exit /b 0

echo Invalid selection. Please try again.
timeout /t 2 >nul
goto :menu

:: ── Shared Submodule Verification ─────────────────────────────
:check_submodules
if not exist "android-reference\settings.gradle.kts" (
    echo [INFO] android-reference submodule is empty. Attempting to fetch automatically...
    git submodule update --init --recursive
    if not exist "android-reference\settings.gradle.kts" (
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
exit /b 0

:: ── Option 1: Normal Launch ──────────────────────────────────
:run_normal
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo [INFO] Starting CloudStream Desktop Client...
echo [TIP] Press F12 in-app anytime to open the Dev Studio LogCat.
echo.
call gradlew :desktop-app:run
goto :after_run

:: ── Option 2: Dev Studio Launch ──────────────────────────────
:run_dev
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo [INFO] Starting CloudStream Desktop with Dev Studio ^& Live LogCat...
echo.
call gradlew :desktop-app:run --args="--dev"
goto :after_run

:: ── Option 3: Release EXE Launch ─────────────────────────────
:run_release
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
set "EXE_PATH=desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\CloudStream-Desktop.exe"

if not exist "%EXE_PATH%" (
    echo.
    echo [WARN] Release executable not found at:
    echo        %EXE_PATH%
    echo.
    set /p "BUILD_NOW=Would you like to compile it now? (Y/N): "
    if /i "!BUILD_NOW!"=="Y" (
        call :run_build
        if not exist "%EXE_PATH%" goto :after_run
    ) else (
        goto :after_run
    )
)

echo.
echo [INFO] Starting CloudStream-Desktop.exe...
start "" "%EXE_PATH%"
exit /b 0

:: ── Option 4: Build Release EXE ──────────────────────────────
:run_build
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo ===================================================
echo   Compiling CloudStream Desktop (Standalone EXE)
echo ===================================================
echo.
call gradlew clean :desktop-app:createDistributable
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed with error code %errorlevel%
    pause
    exit /b %errorlevel%
)
echo.
echo [SUCCESS] Standalone EXE compilation complete!
echo Executable located at:
echo desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\CloudStream-Desktop.exe
echo.
pause
exit /b 0

:: ── Option 5: Run Tests ──────────────────────────────────────
:run_test
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo [INFO] Running all unit test suites and verifications...
echo.
call gradlew :common:test :plugin-runtime:test :desktop-app:compileKotlin
if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] All test suites and builds passed!
) else (
    echo.
    echo [ERROR] Test or compilation failures detected.
)
echo.
pause
exit /b 0

:after_run
echo.
echo App exited with code %errorlevel%
pause

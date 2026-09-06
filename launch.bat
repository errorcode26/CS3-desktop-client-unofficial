@echo off
setlocal enabledelayedexpansion
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

:: ── Shared Pre-Flight Verification ─────────────────────────────
:check_prerequisites
call :check_java
if %errorlevel% neq 0 exit /b %errorlevel%
call :check_submodules
if %errorlevel% neq 0 exit /b %errorlevel%
call :check_native_binaries
if %errorlevel% neq 0 exit /b %errorlevel%
exit /b 0

:: ── Java Environment Verification ──────────────────────────────
:check_java
where java >nul 2>&1
if %errorlevel% neq 0 (
    if "%JAVA_HOME%"=="" (
        echo.
        echo ===================================================
        echo   [FATAL ERROR] Java is not installed or not in PATH
        echo ===================================================
        echo   CloudStream Desktop requires JDK 21 or higher.
        echo   Please install Eclipse Adoptium Temurin 21:
        echo     https://adoptium.net/temurin/releases/?version=21
        echo   and ensure 'java' is in your PATH or JAVA_HOME is set.
        echo ===================================================
        echo.
        pause
        exit /b 1
    )
)
exit /b 0

:: ── Shared Submodule Verification ─────────────────────────────
:check_submodules
if not exist "android-reference\settings.gradle.kts" (
    echo [INFO] android-reference submodule is empty. Attempting to fetch automatically...
    git submodule update --init --recursive
    if not exist "android-reference\settings.gradle.kts" (
        echo.
        echo ===================================================
        echo   [FATAL ERROR] The android-reference folder is empty!
        echo ===================================================
        echo   This usually happens if the repository was downloaded
        echo   as a ZIP file instead of cloned with git.
        echo.
        echo   Please run this command in your terminal:
        echo     git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
        echo ===================================================
        echo.
        pause
        exit /b 1
    )
)
exit /b 0

:: ── Shared Native Binaries Verification ───────────────────────
:check_native_binaries
set "MPV_DLL=desktop-app\appResources\windows\mpv\libmpv-2.dll"
set "BRIDGE_DLL=desktop-app\appResources\windows\jni\player_bridge.dll"
set "WEBVIEW_DLL=desktop-app\appResources\windows\jni\WebView2Loader.dll"

if not exist "%MPV_DLL%" (
    echo.
    echo ===============================================================================
    echo   [MISSING DEPENDENCY] libmpv-2.dll Not Found
    echo ===============================================================================
    echo   Because libmpv-2.dll exceeds GitHub's 100MB file size limit (~112MB),
    echo   it cannot be bundled directly into the Git repository.
    echo.
    echo   Expected Location:
    echo     %MPV_DLL%
    echo.
    echo   How to fix:
    echo     1. Download 'mpv-dev-x86_64-*.7z' from shinchiro's MPV builds:
    echo        https://github.com/shinchiro/mpv-winbuild-cmake/releases
    echo     2. Extract 'libmpv-2.dll' and place it into:
    echo        desktop-app\appResources\windows\mpv\
    echo ===============================================================================
    echo.
    pause
    exit /b 1
)

if not exist "%BRIDGE_DLL%" (
    echo.
    echo [ERROR] Missing native bridge library: %BRIDGE_DLL%
    echo Please verify your repository clone or run desktop-app\src\main\cpp\build_jni.ps1
    echo.
    pause
    exit /b 1
)

if not exist "%WEBVIEW_DLL%" (
    echo.
    echo [ERROR] Missing native WebView2 loader: %WEBVIEW_DLL%
    echo.
    pause
    exit /b 1
)

exit /b 0

:: ── Option 1: Normal Launch ──────────────────────────────────
:run_normal
call :check_prerequisites
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo [INFO] Starting CloudStream Desktop Client...
echo [TIP] Press F12 in-app anytime to open the Dev Studio LogCat.
echo.
call gradlew :desktop-app:run
goto :after_run

:: ── Option 2: Dev Studio Launch ──────────────────────────────
:run_dev
call :check_prerequisites
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo [INFO] Starting CloudStream Desktop with Dev Studio ^& Live LogCat...
echo.
call gradlew :desktop-app:run --args="--dev"
goto :after_run

:: ── Option 3: Release EXE Launch ─────────────────────────────
:run_release
call :check_prerequisites
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
call :check_prerequisites
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
call :check_prerequisites
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

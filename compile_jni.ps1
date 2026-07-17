$ErrorActionPreference = "Stop"

$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrEmpty($javaHome)) {
    # Try to find it from java.exe
    $javaExe = (Get-Command java.exe -ErrorAction SilentlyContinue).Path
    if ($javaExe) {
        $javaHome = (Get-Item $javaExe).Directory.Parent.FullName
    } else {
        Write-Error "JAVA_HOME is not set and java.exe is not in PATH."
    }
}

$jniInclude = Join-Path $javaHome "include"
$jniWin32 = Join-Path $jniInclude "win32"

if (!(Test-Path (Join-Path $jniInclude "jni.h"))) {
    Write-Error "Could not find jni.h in $jniInclude.`nPlease install a JDK (not a JRE) and ensure the JAVA_HOME environment variable is set correctly (e.g., C:\Program Files\Java\jdk-21)."
}

$cppDir = "desktop-app\src\main\cpp"
$outDir = "desktop-app\appResources\windows\jni"
$webview2Dir = Join-Path $cppDir "webview2\build\native"
$webview2Include = Join-Path $webview2Dir "include"
$webview2Dll = Join-Path $webview2Dir "x64\WebView2Loader.dll"

if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

# Copy WebView2Loader.dll to the output directory so it can be loaded at runtime
if (Test-Path $webview2Dll) {
    Copy-Item $webview2Dll -Destination $outDir -Force
}

$outFile = Join-Path $outDir "player_bridge.dll"

Write-Host "Compiling JNI bridge with g++..."
# Compile using MinGW g++
$gppArgs = @(
    "-shared",
    "-m64",
    "-o", $outFile,
    (Join-Path $cppDir "player_bridge.cpp"),
    "-I`"$jniInclude`"",
    "-I`"$jniWin32`"",
    "-I`"$webview2Include`"",
    "-static-libgcc",
    "-static-libstdc++",
    "-Wl,--kill-at",
    "-lole32",
    "-luuid",
    "-lgdi32",
    "-ldwmapi"
)

$process = Start-Process -FilePath "g++" -ArgumentList $gppArgs -Wait -NoNewWindow -PassThru
if ($process.ExitCode -ne 0) {
    Write-Error "g++ compilation failed with exit code $($process.ExitCode)."
} else {
    Write-Host "Successfully compiled $outFile"
}

# A PowerShell script to compile the JNI native bridge for CloudStream Desktop.
# Requirements: MinGW-w64 (g++) in your PATH, and JAVA_HOME environment variable set.

$DllOutput = "..\..\..\appResources\windows\jni\player_bridge.dll"
$Source = "player_bridge.cpp"
$WebviewInclude = "webview2\build\native\include"

if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME environment variable is not set. Please set it to your JDK path."
    exit 1
}

$JavaInclude = "$env:JAVA_HOME\include"
$JavaIncludeWin32 = "$env:JAVA_HOME\include\win32"

Write-Host "Compiling player_bridge.dll..."

# -shared: Create a DLL
# -static: Statically link libstdc++, libgcc, and libwinpthread so the DLL doesn't require MinGW runtime on user PCs
# -lole32 -luser32 -ldwmapi -ladvapi32 -lgdi32 -luuid: Required Windows system libraries for WebView2 and DWM chrome
g++ -shared -static -o "$DllOutput" "$Source" -I"$JavaInclude" -I"$JavaIncludeWin32" -I"$WebviewInclude" -lole32 -luser32 -ldwmapi -ladvapi32 -lgdi32 -luuid

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful! DLL output to: $DllOutput" -ForegroundColor Green
} else {
    Write-Error "Compilation failed."
}

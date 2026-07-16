$ErrorActionPreference = "Stop"

Write-Host "--- CloudStream Batch Plugin Tester ---"
Write-Host "Finding all .cs3 plugins..."

$plugins = Get-ChildItem -Path "test_plugins" -Recurse -Filter "*.cs3"
$total = $plugins.Count

if ($total -eq 0) {
    Write-Host "No .cs3 files found in test_plugins/"
    exit
}

Write-Host "Found $total plugins. Starting batch testing..."
Write-Host "----------------------------------------------"

$count = 1
foreach ($plugin in $plugins) {
    Write-Host "[$count/$total] Testing $($plugin.Name)..."
    
    # We use a relative path from the plugin-sandbox root for gradle args
    $relativePath = $plugin.FullName.Substring((Get-Location).Path.Length + 1).Replace('\', '/')
    
    # Run the gradle task
    .\..\gradlew.bat :sandbox:run --args="$relativePath" | Out-Default
    
    $count++
}

Write-Host "----------------------------------------------"
Write-Host "Batch testing complete! Check the 'reports' folders inside each repository folder."

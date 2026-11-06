# Builds Animated GUI for every supported Minecraft version and collects the jars into dist\
# Usage:  .\build-all.ps1
$ErrorActionPreference = "Stop"

# ALWAYS force the ATLauncher JDK 25: the system-wide JAVA_HOME may be an older JDK that cannot compile 26.x targets.
$env:JAVA_HOME = "C:\Users\Windows 11\AppData\Roaming\ATLauncher\runtimes\minecraft\java-runtime-epsilon\windows-x64\java-runtime-epsilon"

$targets = @('26.2', '26.3')
$failed = @()
New-Item -ItemType Directory -Force dist | Out-Null

foreach ($t in $targets) {
    Write-Host "=== Building target $t ===" -ForegroundColor Cyan
    & .\gradlew.bat --no-daemon clean build "-Ptarget=$t" --console=plain -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $t" -ForegroundColor Red
        $failed += $t
        continue
    }
    # 'clean' per target keeps build\libs holding exactly this target's jars
    Copy-Item "build\libs\animated-gui-*+mc$t.jar" dist\ -Force
}

Write-Host "`nJars in dist\:" -ForegroundColor Green
Get-ChildItem dist\*.jar | Select-Object -ExpandProperty Name

if ($failed.Count -gt 0) {
    Write-Host "`nFailed targets: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}

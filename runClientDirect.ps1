# Load Classpath from file
$cp = [System.IO.File]::ReadAllText("run/classpath.txt").Trim()

# Find JDK 21
$javaExe = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

Write-Host "Launching Minecraft client via direct classpath..." -ForegroundColor Green
Write-Host "Java executable: $javaExe" -ForegroundColor Cyan

# Prepare arguments as an array to prevent PowerShell from breaking paths with spaces/non-ASCII characters
$launchArgs = @(
    "-Dfabric.dli.config=C:\Users\kanay\kanay\新しいフォルダー\antigravity_template-mod-template-1.21.11\.gradle\loom-cache\launch.cfg",
    "-Dfabric.dli.env=client",
    "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient",
    "-Dfile.encoding=UTF-8",
    "-cp", $cp,
    "net.fabricmc.devlaunchinjector.Main"
)

# Start process using Start-Process to separate it from the current PowerShell session
Start-Process -FilePath $javaExe -ArgumentList $launchArgs -WorkingDirectory "run" -NoNewWindow -Wait

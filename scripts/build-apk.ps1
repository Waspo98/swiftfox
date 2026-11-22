[CmdletBinding()]
param(
    [switch]$SkipUpload = $false
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
if (-not $env:ANDROID_HOME -and $env:LOCALAPPDATA) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

Push-Location $repoRoot
try {
    Write-Host "==> Running unit tests via Gradle..." -ForegroundColor Cyan
    .\gradlew.bat :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Unit tests failed" }

    Write-Host "==> Compiling Android debug APK via Gradle..." -ForegroundColor Cyan
    .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle APK build failed" }
}
finally {
    Pop-Location
}

$branch = (git branch --show-current).Trim()
$isBeta = $branch.ToLower() -eq "beta"
$apkName = if ($isBeta) { "SwiftfoxBeta-debug.apk" } else { "Swiftfox-debug.apk" }
$tag = if ($isBeta) { "beta-latest" } else { "debug-latest" }
$apkPath = Join-Path $repoRoot $apkName

if (-not (Test-Path $apkPath)) {
    throw "Expected APK file not found at $apkPath"
}

$apkSizeMB = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
Write-Host "==> Successfully created $apkName ($apkSizeMB MB)" -ForegroundColor Green

if (-not $SkipUpload) {
    $releaseExists = $false
    try {
        $viewResult = gh release view $tag --json tagName 2>$null
        if ($LASTEXITCODE -eq 0 -and $viewResult) { $releaseExists = $true }
    } catch {}

    if (-not $releaseExists) {
        Write-Host "==> Release $tag does not exist. Creating pre-release..." -ForegroundColor Cyan
        $releaseTitle = if ($isBeta) { "Beta Builds (Latest)" } else { "Debug Builds (Latest)" }
        gh release create $tag $apkPath --title $releaseTitle --notes "Automated rolling debug builds for Swiftfox Android ($tag)." --prerelease
        if ($LASTEXITCODE -ne 0) { throw "GitHub release creation failed" }
    } else {
        gh release upload $tag $apkPath --clobber
        if ($LASTEXITCODE -ne 0) { throw "GitHub release upload failed" }
    }

    $downloadUrl = "https://github.com/Waspo98/swiftfox/releases/download/$tag/$apkName"
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "APK Uploaded Successfully!" -ForegroundColor Green
    Write-Host "Download URL: $downloadUrl" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Green
}

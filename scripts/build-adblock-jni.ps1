[CmdletBinding()]
param(
    [switch]$UpdateLock = $false
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$nativeDir = Join-Path $repoRoot "native\adblock-jni"
$jniLibsDir = Join-Path $repoRoot "app\src\main\jniLibs"
$imageName = "swiftfox-adblock-ndk"

New-Item -ItemType Directory -Force -Path $jniLibsDir | Out-Null

Write-Host "==> Building Docker image $imageName..." -ForegroundColor Cyan
docker build -t $imageName $nativeDir
if ($LASTEXITCODE -ne 0) { throw "Docker build failed" }

$hasLock = Test-Path (Join-Path $nativeDir "Cargo.lock")
$lockedFlag = if ($hasLock -and -not $UpdateLock) { "--locked" } else { "" }

Write-Host "==> Compiling libswiftfox_adblock.so for arm64-v8a and x86_64..." -ForegroundColor Cyan
$containerCmd = @"
set -euo pipefail
rm -rf /tmp/build && mkdir -p /tmp/build
cp -r /src/Cargo.toml /src/src /tmp/build/
if [ -f /src/Cargo.lock ]; then cp /src/Cargo.lock /tmp/build/; fi
cd /tmp/build
cargo ndk -t arm64-v8a -t x86_64 -p 31 -o /out build --release $lockedFlag
if [ ! -f /src/Cargo.lock ] || [ "$($UpdateLock.IsPresent)" = "True" ]; then
    cp /tmp/build/Cargo.lock /src/Cargo.lock
fi
"@ -replace "`r`n", "`n"

$srcMount = if (-not $hasLock -or $UpdateLock) { "${nativeDir}:/src" } else { "${nativeDir}:/src:ro" }

docker run --rm `
    -e "CARGO_TARGET_DIR=/tmp/target" `
    -e "CARGO_RESOLVER_INCOMPATIBLE_RUST_VERSIONS=fallback" `
    -e "RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384" `
    -v $srcMount `
    -v "${jniLibsDir}:/out" `
    $imageName `
    bash -c $containerCmd
if ($LASTEXITCODE -ne 0) { throw "cargo-ndk build failed" }

$armSo = Join-Path $jniLibsDir "arm64-v8a\libswiftfox_adblock.so"
$x86So = Join-Path $jniLibsDir "x86_64\libswiftfox_adblock.so"

foreach ($so in @($armSo, $x86So)) {
    if (-not (Test-Path $so) -or (Get-Item $so).Length -le 0) {
        throw "Expected non-empty .so binary not found at $so"
    }
    $sizeKB = [math]::Round((Get-Item $so).Length / 1KB, 1)
    Write-Host "==> Built $so ($sizeKB KB)" -ForegroundColor Green
}

param(
    [string]$Version = "v1.9.1"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $projectRoot "third_party\whisper.cpp"

if (Test-Path -LiteralPath (Join-Path $target "CMakeLists.txt")) {
    $currentVersion = git -c "safe.directory=$target" -C $target `
        describe --tags --exact-match 2>$null
    if ($LASTEXITCODE -eq 0 -and $currentVersion -eq $Version) {
        Write-Host "whisper.cpp $Version is already present at $target"
        exit 0
    }
    throw "whisper.cpp exists at $target but is not the requested $Version."
}

$parent = Split-Path -Parent $target
New-Item -ItemType Directory -Force -Path $parent | Out-Null
if (Test-Path -LiteralPath $target) {
    throw "Incomplete whisper.cpp directory already exists at $target. Remove it before retrying."
}

git clone `
    --depth 1 `
    --branch $Version `
    https://github.com/ggml-org/whisper.cpp.git `
    $target
if ($LASTEXITCODE -ne 0) {
    throw "git clone failed with exit code $LASTEXITCODE"
}

Write-Host "Installed whisper.cpp $Version at $target"
Write-Host "Build with: .\gradlew.bat assembleDebug -PenableWhisperNative=true"

param(
    [string]$Version = "2.0.2",
    [string]$StageDir = "dist/release-2.0.2/Numa",
    [string]$OutputFile = "dist/release-2.0.2/Numa-Setup-2.0.2.exe"
)
$ErrorActionPreference = "Stop"
if ($Version -notmatch '^\d+\.\d+(\.\d+){0,2}$') { throw "Invalid version: $Version" }
$parts = @($Version.Split('.'))
while ($parts.Count -lt 4) { $parts += '0' }
$fileVersion = $parts -join '.'
$stage = (Resolve-Path -LiteralPath $StageDir).Path
$output = [System.IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $output) { throw "Output already exists: $output" }
$script = (Resolve-Path "packaging\NumaInstaller.nsi").Path
& "C:\Program Files (x86)\NSIS\Bin\makensis.exe" /V3 "-DNUMA_VERSION=$Version" "-DNUMA_FILE_VERSION=$fileVersion" "-DSTAGE_DIR=$stage" "-DOUTPUT_FILE=$output" "$script"
if ($LASTEXITCODE -ne 0) { throw "NSIS failed with exit code $LASTEXITCODE" }

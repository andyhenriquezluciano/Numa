param([switch]$Apply)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apkDirectory = Join-Path $workspace 'outputs\mobile'
$currentApk = Get-ChildItem -LiteralPath $apkDirectory -File -Filter '*.apk' |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (!$currentApk) { throw 'The current APK must exist before cleanup.' }
$latestApk = $currentApk.FullName
if (!(Test-Path -LiteralPath (Join-Path $workspace 'dist\Numa\numa.exe') -PathType Leaf)) {
    throw 'The current desktop application must exist before cleanup.'
}

$candidates = @(
    Get-ChildItem -LiteralPath (Join-Path $workspace 'outputs\mobile') -File -Filter '*.apk' |
        Where-Object { $_.FullName -ne $latestApk -and $_.LastWriteTimeUtc -lt $currentApk.LastWriteTimeUtc }
    Get-ChildItem -LiteralPath (Join-Path $workspace 'dist') -File |
        Where-Object { $_.Name -in @('Numa Setup.exe', 'NumaSetup.exe', 'Numa-Setup-v1.0.0.exe',
            'Numa-Setup-v1.0.2.exe', 'Numa-Setup-Privado-v1.0.0.exe', 'Numa-Windows-x86_64.7z') }
    Get-ChildItem -LiteralPath (Join-Path $workspace 'dist\Numa') -File -Filter 'numa-before-*.exe'
    $backupDirectory = Join-Path $workspace 'build\desktop\local-runtime-backups'
    if (Test-Path -LiteralPath $backupDirectory) {
        Get-ChildItem -LiteralPath $backupDirectory -Recurse -File
    }
    $debugApk = Join-Path $workspace 'mobile\android\app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path -LiteralPath $debugApk -PathType Leaf) { Get-Item -LiteralPath $debugApk }
)

$entries = @($candidates | Sort-Object FullName -Unique | ForEach-Object {
    $absolute = [IO.Path]::GetFullPath($_.FullName)
    if (!$absolute.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Cleanup target is outside the workspace: $absolute"
    }
    if ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked file: $absolute" }
    [pscustomobject]@{ Path = $absolute; Bytes = $_.Length }
})

$entries | ForEach-Object { '{0} | {1:N0} bytes' -f $_.Path.Substring($workspace.Length + 1), $_.Bytes }
$total = ($entries | Measure-Object Bytes -Sum).Sum
'Files: {0}; total: {1:N2} GiB; apply: {2}' -f $entries.Count, ($total / 1GB), [bool]$Apply

if ($Apply -and $entries.Count -gt 0) {
    Add-Type -AssemblyName Microsoft.VisualBasic
    $reportDirectory = Join-Path $workspace 'outputs\cleanup'
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    $report = Join-Path $reportDirectory ('artifacts-{0}.json' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    $entries | ConvertTo-Json | Set-Content -LiteralPath $report -Encoding UTF8
    foreach ($entry in $entries) {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($entry.Path,
            [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,
            [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
    }
    "Moved to Recycle Bin. Inventory: $report"
}

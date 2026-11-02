param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dist = Join-Path $workspace 'dist'
# Keep the local runtime, current packaging stage and last published Android APK.
$keep = @('Numa', 'release-dawn-controls', 'release-build20054')
$candidates = @(
    Get-ChildItem -LiteralPath $dist -Directory | Where-Object { $_.Name -notin $keep -and ($_.Name -like 'release-*' -or $_.Name -like 'dawn-*-qa' -or $_.Name -in @('dawn-qa','dawn-qa-gpu')) }
    Get-ChildItem -LiteralPath $dist -File | Where-Object { $_.Name -like 'Numa-Setup-*-dawn-*.exe' -or $_.Name -like '*-release-id.txt' -or $_.Name -like '*-test.txt' -or $_.Name -like '*-preview.*' -or $_.Name -eq 'icon-source.txt' }
)
$entries = @($candidates | ForEach-Object {
    $absolute = [IO.Path]::GetFullPath($_.FullName)
    if (!$absolute.StartsWith($dist + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Outside dist: $absolute" }
    $files = if ($_.PSIsContainer) { @(Get-ChildItem -LiteralPath $absolute -Force -Recurse) } else { @($_) }
    if (@($files | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -gt 0 -or ($_.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Linked target: $absolute" }
    [pscustomobject]@{ Path=$absolute; Bytes=($files | Where-Object { !$_.PSIsContainer } | Measure-Object Length -Sum).Sum; Directory=$_.PSIsContainer }
})
$entries | Select-Object @{n='Path';e={$_.Path.Substring($workspace.Length+1)}},Bytes
'Targets: {0}; GiB: {1:N2}; apply: {2}' -f $entries.Count,(($entries | Measure-Object Bytes -Sum).Sum / 1GB),[bool]$Apply
if ($Apply) {
    if (!(Test-Path -LiteralPath (Join-Path $dist 'Numa-Setup-2.1.0.exe') -PathType Leaf)) { throw 'Build the new installer before removing previous packages.' }
    foreach ($entry in $entries) {
        if ($entry.Directory) { Remove-Item -LiteralPath $entry.Path -Recurse -Force } else { Remove-Item -LiteralPath $entry.Path -Force }
    }
}

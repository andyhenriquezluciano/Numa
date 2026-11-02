param(
    [ValidateSet('desktop','android','services','build')][string]$Area='desktop',
    [string]$Pattern,
    [ValidateRange(1,200)][int]$Limit=60
)
$ErrorActionPreference='Stop'
$roots = @(switch ($Area) {
    desktop { @('app/qml','app/src') }
    android { @('mobile/android/app/src') }
    services { @('services') }
    build { @('scripts','meson','packaging','.github/workflows') }
})
Push-Location (Join-Path $PSScriptRoot '..')
try {
    if ($Pattern) {
        # Keep search output bounded; return file names first, then read relevant source.
        $matches = @(& rg -l -- $Pattern @roots)
        if ($LASTEXITCODE -gt 1) { throw 'Search failed' }
        $matches | Select-Object -First $Limit
        'Matched files: {0}; shown: {1}' -f $matches.Count,[Math]::Min($matches.Count,$Limit)
    } else {
        $files = @(& git ls-files -- @roots)
        if ($LASTEXITCODE -ne 0) { throw 'File inventory failed' }
        $files | Select-Object -First $Limit
        'Tracked files: {0}; shown: {1}' -f $files.Count,[Math]::Min($files.Count,$Limit)
    }
} finally { Pop-Location }

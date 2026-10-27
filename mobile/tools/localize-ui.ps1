$ErrorActionPreference = 'Stop'
$numaRoot = Join-Path $PSScriptRoot '../android/app/src/main/java/dev/sfg/numa/mobile/ui'
$numaTranslations = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-translations.tsv') | ForEach-Object {
    $numaPair = $_ -split "`t", 2
    if ($numaPair.Count -eq 2 -and $numaPair[0] -cne $numaPair[1]) { $numaTranslations[$numaPair[0]] = $numaPair[1] }
}
$numaPattern = [regex]'"(?:\\.|[^"\\])*"'
Get-ChildItem -LiteralPath $numaRoot -Recurse -Filter '*.kt' | Where-Object { $_.Name -notin @('UiText.kt', 'CatalogCaption.kt') } | ForEach-Object {
    $numaFile = $_.FullName
    $numaOriginal = [IO.File]::ReadAllText($numaFile)
    $numaChanged = $false
    $numaLines = foreach ($numaLine in ($numaOriginal -split '\r?\n')) {
        if ($numaLine -match '^\s*(//|\*)|\bconst val\b') { $numaLine; continue }
        $numaPattern.Replace($numaLine, [System.Text.RegularExpressions.MatchEvaluator]{ param($numaMatch)
            if ($numaMatch.Value.Length -le 2 -or $numaMatch.Value.Contains('$')) { return $numaMatch.Value }
            if ($numaLine.Substring(0, $numaMatch.Index).EndsWith('uiText(')) { return $numaMatch.Value }
            try { $numaEnglish = $numaMatch.Value | ConvertFrom-Json } catch { return $numaMatch.Value }
            if (!$numaTranslations.ContainsKey($numaEnglish)) { return $numaMatch.Value }
            $script:numaChanged = $true
            $numaSpanish = $numaTranslations[$numaEnglish] | ConvertTo-Json -Compress
            return "uiText($($numaMatch.Value), $numaSpanish)"
        })
    }
    $numaText = $numaLines -join "`n"
    Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-dynamic-translations.tsv') | ForEach-Object {
        $numaPair = $_ -split "`t", 2
        if ($numaPair.Count -ne 2) { return }
        $numaToken = '"' + $numaPair[0] + '"'
        if ($numaText.Contains($numaToken) -and !$numaText.Contains('uiText(' + $numaToken + ',')) {
            $numaText = $numaText.Replace($numaToken, 'uiText(' + $numaToken + ', "' + $numaPair[1] + '")')
            $numaChanged = $true
        }
    }
    if ($numaChanged) {
        if (!$numaText.Contains('import dev.sfg.numa.mobile.ui.text.uiText')) {
            $numaText = [regex]::Replace($numaText, '(?m)^(package [^\r\n]+)', '$1' + "`n`nimport dev.sfg.numa.mobile.ui.text.uiText", 1)
        }
        [IO.File]::WriteAllText($numaFile, $numaText, [Text.UTF8Encoding]::new($false))
        Write-Output $numaFile
    }
}

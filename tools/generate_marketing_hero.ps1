[CmdletBinding()]
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command magick -ErrorAction SilentlyContinue)) {
    throw "ImageMagick is required to generate the marketing hero."
}

$repoPath = (Resolve-Path -LiteralPath $RepoRoot).Path
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repoPath "docs\screenshots\zeuswatch-hero-v1.29.3.png"
}

$homePath = Join-Path $repoPath "docs\screenshots\phone-home.png"
$comparePath = Join-Path $repoPath "docs\screenshots\phone-compare.png"
$logoPath = Join-Path $repoPath "icon.png"
$boldFont = "C:/Windows/Fonts/seguisb.ttf"
$regularFont = "C:/Windows/Fonts/segoeui.ttf"

foreach ($path in @($homePath, $comparePath, $logoPath, $boldFont, $regularFont)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required hero input is missing: $path"
    }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("ZeusWatchHero-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null

function Invoke-Magick {
    param([string[]]$Arguments)

    & magick @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ImageMagick failed with exit code $LASTEXITCODE."
    }
}

function New-PhoneCard {
    param(
        [string]$InputPath,
        [string]$Name
    )

    $roundedPath = Join-Path $tempRoot "$Name-rounded.png"
    $framedPath = Join-Path $tempRoot "$Name-framed.png"
    $shadowPath = Join-Path $tempRoot "$Name-shadow.png"

    Invoke-Magick @(
        $InputPath,
        "-resize", "315x700!",
        "(", "-size", "315x700", "xc:none", "-fill", "white",
        "-draw", "roundrectangle 0,0 314,699 30,30", ")",
        "-alpha", "set", "-compose", "DstIn", "-composite",
        $roundedPath
    )

    Invoke-Magick @(
        "-size", "319x704", "xc:none", "-fill", "#2268DD",
        "-draw", "roundrectangle 0,0 318,703 32,32",
        $roundedPath, "-geometry", "+2+2", "-compose", "Over", "-composite",
        $framedPath
    )

    Invoke-Magick @(
        $framedPath,
        "(", "+clone", "-background", "#000000", "-shadow", "55x18+0+14", ")",
        "+swap", "-background", "none", "-layers", "merge", "+repage",
        $shadowPath
    )

    return $shadowPath
}

try {
    $homeCard = New-PhoneCard -InputPath $homePath -Name "home"
    $compareCard = New-PhoneCard -InputPath $comparePath -Name "compare"
    $logoForHero = Join-Path $tempRoot "logo.png"

    Invoke-Magick @(
        $logoPath, "-resize", "96x96", $logoForHero
    )

    Invoke-Magick @(
        "-size", "1600x900", "xc:#06111C",
        "-fill", "#091A2A", "-draw", "roundrectangle 820,36 1564,864 48,48",
        "-fill", "#102C47", "-draw", "roundrectangle 842,58 1542,842 38,38",
        $homeCard, "-geometry", "+866+64", "-compose", "Over", "-composite",
        $compareCard, "-geometry", "+1210+120", "-compose", "Over", "-composite",
        $logoForHero, "-geometry", "+96+64", "-compose", "Over", "-composite",
        "-gravity", "NorthWest",
        "-font", $boldFont, "-fill", "#EAF3FF", "-pointsize", "30",
        "-annotate", "+214+91", "ZEUSWATCH",
        "-font", $boldFont, "-fill", "#F6FAFF", "-pointsize", "72",
        "-annotate", "+96+214", "Weather, with a",
        "-annotate", "+96+298", "second opinion.",
        "-font", $regularFont, "-fill", "#AFC2D6", "-pointsize", "30",
        "-annotate", "+100+414", "Compare models. Read risk.",
        "-annotate", "+100+458", "Keep control.",
        "-fill", "#FFB30F", "-draw", "circle 108,564 108,558",
        "-fill", "#2BA5FC", "-draw", "circle 414,564 414,558",
        "-fill", "#2BA5FC", "-draw", "circle 108,632 108,626",
        "-fill", "#FFB30F", "-draw", "circle 414,632 414,626",
        "-font", $regularFont, "-fill", "#DCE8F5", "-pointsize", "24",
        "-annotate", "+126+543", "See when models agree",
        "-annotate", "+432+543", "37 configurable cards",
        "-annotate", "+126+611", "Five radar choices",
        "-annotate", "+432+611", "No account required",
        "-stroke", "#1A4266", "-strokewidth", "2", "-draw", "line 100,742 706,742",
        "-stroke", "none", "-font", $boldFont, "-fill", "#68B8FF", "-pointsize", "21",
        "-annotate", "+100+780", "OPEN SOURCE  •  ANDROID 8.0+",
        "-strip", "-define", "png:compression-level=9",
        $OutputPath
    )
}
finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemp.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedTemp).StartsWith("ZeusWatchHero-")) {
        Remove-Item -Recurse -Force -LiteralPath $resolvedTemp
    }
}

Write-Host "Created $OutputPath"

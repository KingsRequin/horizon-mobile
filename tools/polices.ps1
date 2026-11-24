# Fabrique les coupes figées de Roboto Flex embarquées par l'appli (core/design/src/main/res/font).
# Des coupes figées plutôt que le fichier variable : certains Android (HyperOS) ignorent les axes
# des polices variables. Demande Python 3 ; fonttools est installé dans un dossier de travail.
#   .\tools\polices.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$work = Join-Path $root 'build/polices'
$out = Join-Path $root 'core/design/src/main/res/font'
New-Item -ItemType Directory -Force $work, $out | Out-Null

# Version précise du dépôt google/fonts : le même fichier à chaque fabrication.
$commit = '2888baa32fbf16f242c684fbd6ab2daecbeb82fe'
$file = 'RobotoFlex%5BGRAD%2CXOPQ%2CXTRA%2CYOPQ%2CYTAS%2CYTDE%2CYTFI%2CYTLC%2CYTUC%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf'
$source = Join-Path $work 'RobotoFlex-variable.ttf'
if (-not (Test-Path $source)) {
  Invoke-WebRequest "https://raw.githubusercontent.com/google/fonts/$commit/ofl/robotoflex/$file" -OutFile $source
}
"police d'origine : {0:N0} Ko, SHA-256 {1}" -f ((Get-Item $source).Length / 1KB), (Get-FileHash $source -Algorithm SHA256).Hash.ToLower()

$venv = Join-Path $work 'venv'
$python = if ($IsWindows -or $env:OS -eq 'Windows_NT') { Join-Path $venv 'Scripts/python.exe' } else { Join-Path $venv 'bin/python' }
if (-not (Test-Path $python)) {
  & python -m venv $venv
  & $python -m pip install --quiet 'fonttools==4.68.0'
  if ($LASTEXITCODE) { throw 'installation de fonttools impossible' }
}

# Latin de base, Latin-1, Latin étendu A, et la ponctuation dont les textes se servent
# (espaces fines et insécables, tirets, guillemets, points de suspension, point médian, flèches).
$unicodes = 'U+0020-007E,U+00A0-00FF,U+0100-017F,U+2009,U+200A,U+2011,U+2013,U+2014,U+2018-201A,U+201C-201E,U+2022,U+2026,U+202F,U+2032,U+2033,U+2039,U+203A,U+20AC,U+2190-2193,U+2212'
# Graisse, largeur : les sept coupes de la charte.
$cuts = @(@(400, 100), @(600, 100), @(650, 100), @(700, 100), @(750, 112), @(750, 114), @(750, 118))
Get-ChildItem $out -Filter 'roboto_flex_*.ttf' | Remove-Item
$total = 0
foreach ($cut in $cuts) {
  $weight, $width = $cut
  $name = if ($width -eq 100) { "roboto_flex_w$weight" } else { "roboto_flex_w${weight}_wd$width" }
  $static = Join-Path $work "$name-entier.ttf"
  & $python -m fontTools varLib.instancer $source "wght=$weight" "wdth=$width" --static --quiet -o $static
  if ($LASTEXITCODE) { throw "coupe $name : échec de l'instanciation" }
  $target = Join-Path $out "$name.ttf"
  & $python -m fontTools subset $static "--unicodes=$unicodes" '--layout-features=*' '--no-hinting' '--desubroutinize' "--output-file=$target"
  if ($LASTEXITCODE) { throw "coupe $name : échec du sous-ensemble" }
  $size = (Get-Item $target).Length
  $total += $size
  "{0} : {1:N0} Ko" -f "$name.ttf", ($size / 1KB)
}
"total : {0:N0} Ko" -f ($total / 1KB)
if ($total -gt 700KB) { throw 'les sept coupes dépassent 700 Ko' }

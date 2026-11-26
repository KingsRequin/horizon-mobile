# Convertit les icônes Phosphor (licence MIT) de l'appli en dessins vectoriels Android
# (core/design/src/main/res/drawable/ic_*.xml). Ce sont les pictogrammes de la barre d'Horizon,
# pour les mêmes choses. Pour en ajouter une : son nom dans la liste, puis relancer. Un nom
# suivi de « -fill » prend la variante pleine (commandes du lecteur, comme dans la charte).
#   .\tools\icones.ps1
$ErrorActionPreference = 'Stop'
# Sans barre de progression : sous Windows PowerShell 5.1, elle rend chaque téléchargement très lent.
$ProgressPreference = 'SilentlyContinue'
$out = Join-Path (Split-Path $PSScriptRoot) 'core/design/src/main/res/drawable'
New-Item -ItemType Directory -Force $out | Out-Null
# Version précise du dépôt phosphor-icons/core : les mêmes tracés à chaque fabrication.
$commit = '2b75f3ad12b420c9504ef05df8d2564a28f8500e'
$names = @('device-mobile', 'watch', 'headset', 'check', 'timer', 'gear-six', 'warning', 'caret-down', 'play', 'x', 'house', 'desktop', 'lock', 'qr-code', 'copy',
  'play-fill', 'pause-fill', 'skip-back-fill', 'skip-forward-fill', 'speaker-high', 'speaker-slash', 'list')
foreach ($name in $names) {
  $weight, $label = if ($name -like '*-fill') { 'fill', 'Fill' } else { 'regular', 'Regular' }
  $svg = (Invoke-WebRequest "https://raw.githubusercontent.com/phosphor-icons/core/$commit/assets/$weight/$name.svg").Content
  if ($svg -notmatch 'viewBox="0 0 256 256"') { throw "$name : cadre inattendu" }
  $paths = @([regex]::Matches($svg, '<path[^>]*\sd="([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
  if (-not $paths.Count -or $svg -match '<(circle|rect|line|polyline|polygon|ellipse)\b') { throw "$name : tracé que ce script ne sait pas convertir" }
  $lines = @('<?xml version="1.0" encoding="utf-8"?>', "<!-- Phosphor $label « $name » (MIT). Fabriqué par tools/icones.ps1 : ne pas modifier ici. -->", '<vector xmlns:android="http://schemas.android.com/apk/res/android"', '    android:width="24dp"', '    android:height="24dp"', '    android:viewportWidth="256"', '    android:viewportHeight="256">')
  foreach ($d in $paths) { $lines += @('    <path', '        android:fillColor="#FFFFFFFF"', "        android:pathData=`"$d`" />") }
  $lines += '</vector>'
  $file = "ic_$($name -replace '-', '_').xml"
  [IO.File]::WriteAllText((Join-Path $out $file), (($lines -join "`n") + "`n"))
  $file
}

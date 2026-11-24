# L'appli doit rester un seul APK, le même pour tous les processeurs. Échoue si un APK contient
# une bibliothèque native (.so) hors de la liste connue, ou si une bibliothèque connue manque
# pour l'un des quatre processeurs d'Android.
#   .\tools\sans-natif.ps1 <apk> [<apk>…]
param([Parameter(Mandatory, ValueFromRemainingArguments)][string[]]$Apk)
$ErrorActionPreference = 'Stop'
# Ce que Compose apporte lui-même (parcours des tracés, androidx.graphics:graphics-path),
# publié pour les quatre processeurs. Toute autre bibliothèque native se discute avant d'entrer.
$known = @('libandroidx.graphics.path.so')
$abis = @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$failed = $false
foreach ($path in $Apk) {
  $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $path).Path)
  try { $natives = @($zip.Entries | Where-Object { $_.FullName -like '*.so' } | ForEach-Object { $_.FullName }) }
  finally { $zip.Dispose() }
  $problems = @()
  $problems += @($natives | Where-Object { $known -notcontains ($_ -split '/')[-1] } | ForEach-Object { "inattendue : $_" })
  foreach ($lib in $known) {
    $present = @($natives | Where-Object { ($_ -split '/')[-1] -eq $lib } | ForEach-Object { ($_ -split '/')[-2] })
    if ($present.Count) {
      $problems += @($abis | Where-Object { $present -notcontains $_ } | ForEach-Object { "$lib manque pour $_" })
    }
  }
  if ($problems.Count) {
    "$path : $($problems.Count) problème(s) de bibliothèque native"
    $problems | ForEach-Object { "  $_" }
    $failed = $true
  } else {
    "$path : un seul APK pour tous les processeurs ($($natives.Count) bibliothèque(s) native(s), toutes connues)"
  }
}
if ($failed) { exit 1 }

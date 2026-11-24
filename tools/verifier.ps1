# Vérification avant de dire « terminé » : tests, lint (en erreurs), appels de Java absents
# d'Android 7 dans les modules Kotlin purs (animalsnifferMain), les deux APK, puis le contrôle
# « un seul APK pour tous les processeurs ». La CI lance les mêmes étapes.
#   .\tools\verifier.ps1
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot)
try {
  $gradle = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
  & $gradle test lint animalsnifferMain :app:assembleDebug :app:assembleRelease --console=plain
  if ($LASTEXITCODE) { throw "échec : gradle (code $LASTEXITCODE)" }
  $apks = @(Get-ChildItem app/build/outputs/apk -Recurse -Filter *.apk | ForEach-Object { $_.FullName })
  if ($apks.Count -lt 2) { throw "APK attendus : debug et release ; trouvés : $($apks.Count)" }
  & (Join-Path $PSScriptRoot 'sans-natif.ps1') @apks
  if ($LASTEXITCODE) { throw 'échec : bibliothèque native inattendue ou incomplète dans un APK' }
} finally {
  Pop-Location
}

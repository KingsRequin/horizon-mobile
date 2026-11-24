# Copie les messages d'exemple du protocole depuis le dépôt d'Horizon : les deux côtés se
# testent contre les mêmes fichiers. À relancer quand le protocole change.
#   .\tools\exemples.ps1 <dossier du dépôt d'Horizon>
param([Parameter(Mandatory)][string]$Horizon)
$ErrorActionPreference = 'Stop'
$source = Join-Path $Horizon 'crates/horizon-core/tests/fixtures/phone'
$target = Join-Path (Split-Path $PSScriptRoot) 'core/protocol/src/test/resources/phone'
if (-not (Test-Path $source)) { throw "exemples introuvables : $source" }
New-Item -ItemType Directory -Force $target | Out-Null
Get-ChildItem $target -Filter *.json | Remove-Item
$files = @(Get-ChildItem $source -Filter *.json)
$files | Copy-Item -Destination $target
$commit = (& git -C $Horizon rev-parse HEAD).Trim()
if ($LASTEXITCODE) { throw "commit d'origine illisible dans $Horizon" }
$lines = @("Copie de crates/horizon-core/tests/fixtures/phone du dépôt KingsRequin/horizon.", "Commit d'origine : $commit", "Ne pas modifier ici : relancer tools/exemples.ps1.")
[IO.File]::WriteAllText((Join-Path $target 'ORIGINE.txt'), (($lines -join "`n") + "`n"))
"$($files.Count) exemples copiés (commit $($commit.Substring(0, 7)))"

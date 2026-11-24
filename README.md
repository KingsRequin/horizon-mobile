# Horizon Mobile

L'appli Android compagnon d'[Horizon](https://github.com/KingsRequin/horizon), la barre d'outils
pour Windows. Elle s'appaire au PC par un QR code et lui envoie la batterie du téléphone, qui
s'affiche sur la barre. Tout se passe sur le réseau local : rien ne passe par Internet, aucun
compte.

Android 7.0 et plus. Un seul APK, le même pour tous les téléphones.

## Installer

1. Dans Horizon : *Réglages d'Horizon › Plugins › Modules intégrés*, activer **Téléphone**. La
   page qui s'ouvre montre un QR code vers la dernière version de l'appli.
2. Installer l'APK, ouvrir l'appli, viser le QR code d'appairage affiché par Horizon.

Le téléphone et le PC doivent être sur le même réseau.

## Compiler

JDK 17 et le SDK Android (plateforme 37).

```
.\gradlew.bat :app:assembleDebug     l'APK d'essai (paquet fr.kingsrequin.horizon.dev,
                                     installable à côté de la version publiée)
.\tools\verifier.ps1                 tests, lint (en erreurs), les deux APK, un seul APK pour
                                     tous les processeurs : à lancer avant de dire « terminé »
```

La version publiée est signée par les variables `HM_KEYSTORE_PATH`, `HM_KEYSTORE_PASSWORD`,
`HM_KEY_ALIAS` et `HM_KEY_PASSWORD` ; sans elles l'APK sort non signé. La version est dans
`version.properties`.

## Organisation

```
core/protocol     Kotlin pur : messages échangés avec Horizon, lecture du QR d'appairage
core/design       la charte : palette tirée d'une couleur, typographie, formes, composants
core/connection   liaison avec le PC : TLS épinglé, appairage, reprise, service
app               les écrans
tools             vérification et contrôles
```

Le protocole est décrit dans le dépôt d'Horizon (`docs/PHONE.md`). Les messages d'exemple de
`core/protocol/src/test/resources/phone/` en sont une copie (`tools\exemples.ps1`) : les deux
côtés se testent contre les mêmes fichiers.

## Licence

MIT.

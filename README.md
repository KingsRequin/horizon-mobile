# Horizon Mobile

L'appli Android compagnon d'[Horizon](https://github.com/KingsRequin/horizon), la barre d'outils
pour Windows. Elle s'appaire au PC par un QR code et lui envoie la batterie du téléphone, qui
s'affiche sur la barre. Tout se passe sur le réseau local : rien ne passe par Internet, aucun
compte, aucune publicité, aucune mesure d'audience.

Android 7.0 et plus. Un seul APK, le même pour tous les téléphones.

## Installer

1. Dans Horizon : *Réglages d'Horizon › Plugins › Modules intégrés*, activer **Téléphone**. La
   page qui s'ouvre demande d'autoriser Horizon sur le réseau local (une règle du pare-feu de
   Windows), puis montre un QR code vers la dernière version de l'appli.
2. Installer l'APK (Android demande d'autoriser l'installation depuis le navigateur), ouvrir
   l'appli, puis viser le QR code d'appairage affiché par Horizon (*Afficher le code*).
3. Choisir quand garder la liaison : **en continu** (la barre suit la batterie même appli
   fermée, avec une notification discrète) ou **seulement quand l'appli est ouverte**.

Le téléphone et le PC doivent être sur le même réseau. L'appli s'installe aussi par
[Obtainium](https://github.com/ImranR98/Obtainium), qui suit les versions de ce dépôt.

## Ce que fait l'appli, et ce qu'elle ne fait pas

- Elle envoie la batterie du téléphone (pourcentage, en charge ou non) quand elle change, jamais
  à heure fixe. Elle n'affiche pas ce pourcentage : la barre d'Horizon est là pour ça.
- Elle ne parle qu'au PC appairé : la connexion est chiffrée et n'est acceptée que si le PC
  présente la clé lue dans le QR code. Elle ne joint que des adresses du réseau local.
- Elle ne se réveille pas d'elle-même : elle réagit au retour du Wi-Fi, à un changement de
  batterie et à son ouverture. Loin du PC, elle attend.
- Elle regarde une fois par jour, à l'ouverture, si une version plus récente existe sur GitHub,
  et propose de la télécharger. Elle n'installe rien seule.

## Téléphones

L'appli n'emploie aucun service de Google : elle marche sur les téléphones qui n'en ont pas.
Elle ne marche pas sur iOS, ni sur HarmonyOS 5 et plus (qui n'exécute plus les APK).

Certaines marques arrêtent les applis en arrière-plan plus durement que d'autres. Si la barre
ne suit plus ta batterie écran éteint, l'accueil de l'appli propose de l'autoriser à tourner en
arrière-plan ; le mode « seulement quand l'appli est ouverte » évite la question.

## Signaler un problème

*Réglages › Copier le rapport de diagnostic*, puis coller le texte dans une
[issue](https://github.com/KingsRequin/horizon-mobile/issues). Le rapport dit le modèle du
téléphone, la version d'Android, le mode de liaison, la raison du dernier arrêt de l'appli et
les derniers événements de la liaison. Il ne contient ni le nom du PC, ni adresse, ni jeton.

## Compiler

JDK 17 et le SDK Android (plateforme 37.2).

```
.\gradlew.bat :app:assembleDebug     l'APK d'essai (paquet fr.kingsrequin.horizon.dev,
                                     installable à côté de la version publiée)
.\tools\verifier.ps1                 tests, lint (en erreurs), appels de Java absents
                                     d'Android 7, les deux APK, un seul APK pour tous les
                                     processeurs : à lancer avant de dire « terminé »
```

La version publiée est signée par les variables `HM_KEYSTORE_PATH`, `HM_KEYSTORE_PASSWORD`,
`HM_KEY_ALIAS` et `HM_KEY_PASSWORD` ; sans elles l'APK sort non signé. La version est dans
`version.properties`. Un écran Compose se rend en image sans téléphone : depuis le dossier de
son module, `android preview render --fqn=<aperçu> --output=build/apercus`.

## Organisation

```
core/protocol     Kotlin pur : messages échangés avec Horizon, lecture du QR d'appairage
core/link         Kotlin pur : session TLS épinglée sur la clé du PC, reprise de la liaison
core/design       la charte : palette tirée d'une couleur, typographie, formes, composants
core/connection   ce que la liaison demande à Android : coffre de clés, service, réseau
app               les écrans : appairage, accueil, réglages
tools             vérification, polices (polices.ps1), icônes (icones.ps1), exemples du protocole
docs              le protocole (copie de docs/PHONE.md du dépôt d'Horizon)
```

Les modules purs se testent sur la JVM, contre un faux PC. Les messages d'exemple de
`core/protocol/src/test/resources/phone/` sont une copie de ceux d'Horizon
(`tools\exemples.ps1`) : les deux côtés se testent contre les mêmes fichiers.

L'appli suit la couleur du PC : toute sa palette est tirée de la couleur d'accent que la barre
envoie (pochette pendant la lecture, sinon fond d'écran, sinon l'orange d'Horizon).

## Licence

MIT. Polices et icônes : [LICENCES-TIERCES.md](LICENCES-TIERCES.md).

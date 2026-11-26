# Horizon Mobile

L'appli Android compagnon d'[Horizon](https://github.com/KingsRequin/horizon), la barre d'outils
pour Windows. Elle s'appaire au PC par un QR code, lui envoie la batterie du téléphone, qui
s'affiche sur la barre, et sert de télécommande au PC : lecture, morceau, position, volume, avec
la pochette, depuis l'appli, le volet ou l'écran verrouillé, avec le volume de chaque appli, la
sortie audio et quelques actions (verrouiller, ne pas déranger, minuteur). Tout se passe sur le réseau local :
rien ne passe par Internet, aucun compte, aucune publicité, aucune mesure d'audience.

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
- Télécommande (Horizon 0.9 et plus) : l'onglet *Télécommande* montre ce que joue le PC
  (pochette, titre, artiste, position) et le pilote : lecture et pause, morceau précédent ou
  suivant, position, choix du lecteur quand il y en a plusieurs, volume général et muet. Sur cet
  écran, les touches de volume du téléphone règlent le PC ; ailleurs, celui du téléphone. Une
  carte de lecture reprend l'essentiel sur l'accueil.
- Mixeur et actions (Horizon 0.10 et plus) : sur la télécommande, la sortie audio du PC (passer
  à la suivante ou la choisir) et le volume et le muet de chaque appli ; sur l'accueil,
  verrouiller le PC, ne pas déranger, le minuteur (durées d'Horizon, pause, arrêt) et la batterie
  du casque du PC. Droit *Son et actions* dans Horizon.
- Pendant que le PC joue, ses commandes apparaissent dans le volet, sur l'écran verrouillé et
  sur un casque Bluetooth (*Réglages › Commandes du PC dans le volet* pour les retirer).
- Chaque téléphone a ses droits dans Horizon (*Réglages › Téléphone*, case *Télécommande* sous
  son nom) ; sans le droit, l'onglet dit où l'accorder.
- Elle ne parle qu'au PC appairé : la connexion est chiffrée et n'est acceptée que si le PC
  présente la clé lue dans le QR code. Elle ne joint que des adresses du réseau local.
- Elle ne se réveille pas d'elle-même : elle réagit au retour du Wi-Fi, à un changement de
  batterie et à son ouverture. Loin du PC, elle attend. Le PC ne lui envoie que les changements
  de la lecture (et une pochette par morceau), le volume et le mixeur seulement pendant que
  l'écran Télécommande est ouvert, les actions pendant que l'accueil l'est.
- Elle regarde, à l'ouverture et au plus toutes les 6 heures, si une version plus récente existe
  sur GitHub. Si oui, l'accueil et les réglages proposent « Mettre à jour » : l'appli télécharge
  l'APK, vérifie sa somme SHA-256 contre le `SHA256SUMS` de la même version, puis ouvre l'écran
  d'installation d'Android, qui demande toujours ta confirmation (et, la première fois,
  d'autoriser l'appli à installer des applis). Rien ne s'installe sans toi.

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
core/link         Kotlin pur : session TLS épinglée sur la clé du PC, reprise de la liaison,
                  état de la télécommande (Remote)
core/design       la charte : palette tirée d'une couleur, typographie, formes, composants
core/connection   ce que la liaison demande à Android : coffre de clés, service, réseau,
                  session multimédia du PC (Media3) pour le volet et l'écran verrouillé
app               les écrans : appairage, accueil, télécommande, réglages
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

# Liaison avec l'appli Horizon Mobile : protocole, version 1

Référence pour qui écrit l'appli (dépôt `KingsRequin/horizon-mobile`) ou le module « Téléphone »
d'Horizon (`natif/src/widgets/phone/`, logique pure dans `crates/horizon-core/src/phone/`).
Un message d'exemple par type se trouve dans `crates/horizon-core/tests/fixtures/phone/` :
Horizon et l'appli testent tous deux contre ces fichiers.

## Transport

- Le PC écoute, le téléphone ouvre la connexion. Jamais l'inverse.
- TLS (1.3, ou 1.2 pour Android 7 à 9), puis WebSocket sur `wss://<adresse>:<port>/`.
- Port 28680 par défaut ; s'il est pris, le suivant libre jusqu'à 28689. Le port réel est dans
  le QR.
- Le certificat du PC est auto-signé. Le téléphone ne vérifie ni autorité ni nom d'hôte : il
  compare l'**empreinte de la clé publique** du certificat à celle du QR. L'empreinte est le
  SHA-256 du SPKI (DER), en base64url sans remplissage (43 caractères). Le certificat peut être
  renouvelé sans réappairer : la clé, elle, ne change pas.
- Horizon n'accepte que les adresses privées (10/8, 172.16/12, 192.168/16, 169.254/16, 127/8).
- Messages : trames texte, un objet JSON par trame, champ `type`. 64 Kio au plus par trame :
  au-delà, la connexion est fermée.
- Pas de ping applicatif. Chaque côté envoie seulement ce qui a changé. Le PC active les sondes
  TCP (« keepalive » : après 2 minutes de silence, puis toutes les 10 secondes) pour remarquer
  un téléphone parti sans fermer sa connexion : elles se règlent entre les deux systèmes, sans
  réveiller l'appli.

## Appairage

Horizon affiche un QR code qui contient :

```
horizon://pair?v=1&h=<ipv4,ipv4…>&n=<nom du PC>&p=<port>&fp=<empreinte>&t=<jeton>
```

| Champ | Sens |
| --- | --- |
| `v` | Version du lien. Une autre valeur que `1` : l'appli demande une mise à jour. |
| `h` | Adresses IPv4 privées du PC (huit au plus), séparées par des virgules, à essayer dans l'ordre. |
| `n` | Nom du PC, encodé en pourcent (64 caractères au plus). Sert aussi de nom d'hôte : `<n>.local`. |
| `p` | Port. |
| `fp` | Empreinte de la clé publique du PC. |
| `t` | Jeton d'appairage (16 octets aléatoires) : usage unique, valable 5 minutes. Les essais faux ne le font pas tomber : sinon n'importe quel appareil du réseau pourrait empêcher l'appairage. |

L'appli refuse un lien dont une adresse n'est pas une IPv4 d'un réseau local (10/8, 172.16/12,
192.168/16, 169.254/16), qui en porte plus de huit, ou dont le nom est vide, trop long ou
contient un caractère invisible. Un lien ouvert autrement que par son viseur (page web, autre
appli) n'appaire qu'après un accord qui montre le PC, ses adresses et son port. Pendant
l'appairage elle n'essaie que ces adresses ; `<n>.local` ne sert qu'aux reconnexions.

Le téléphone se connecte et envoie `pair.request`. Horizon répond `pair.accepted`, avec un
jeton permanent propre à ce téléphone, ou `pair.rejected`. Horizon ne garde du jeton permanent
que son SHA-256 ; le téléphone le garde chiffré.

Pour retrouver le PC ensuite, le téléphone essaie la dernière adresse qui a marché, puis celles
du QR, puis `<n>.local`.

## Session

La première trame arrive dans les 10 secondes et vaut `pair.request` ou `hello` ; sinon la
connexion est fermée. Après `pair.accepted` ou `hello.ok`, la session est ouverte.

### Du téléphone vers le PC

| `type` | Champs |
| --- | --- |
| `pair.request` | `token`, `deviceId` (UUID créé par l'appli à son installation), `deviceName`, `appVersion` |
| `hello` | `deviceId`, `deviceToken`, `protocol` (1), `appVersion` |
| `phone.battery` | `devices` : liste de `{ id, kind, name, percent, charging, atMs }` |
| `watch` | `topics` : ce que le téléphone veut recevoir, `media` et `volume` (remplace la liste précédente) |
| `media.command` | `action` : `play_pause`, `next`, `previous` |
| `media.seek` | `positionMs` : aller à cette position du morceau en cours |
| `media.select` | `appId` : suivre cette lecture plutôt que celle choisie par Windows |
| `volume.set` | `volume` : volume général, de 0 à 1 |
| `volume.mute` | `muted` |

`kind` vaut `phone`, `watch`, `earbuds` ou autre chose (traité comme « autre »). `percent` va
de 0 à 100. `atMs` est l'heure de la mesure (millisecondes Unix, horloge du téléphone) ; une
heure à plus de 5 minutes dans le futur est remplacée par l'heure de réception. Un envoi
remplace le précédent du même téléphone : il porte tous ses appareils, 8 au plus. Le téléphone
envoie à l'ouverture de la session, puis quand un pourcentage ou un état de charge change.

`watch` se renvoie à chaque session ouverte : sans lui, le PC n'envoie ni média ni volume. Le
téléphone regarde `media` tant que la liaison est ouverte (commandes du volet et de l'écran
verrouillé), `volume` seulement pendant que l'écran Télécommande est ouvert. Un sujet inconnu,
ou que le téléphone n'a pas le droit de voir, est ignoré ; le PC garde la demande et la
rétablit quand le droit revient (avec l'état du moment). Une session neuve repart sans
abonnement. Une commande sans le droit `media`, une action inconnue ou un volume illisible
sont ignorés ; le volume est ramené entre 0 et 1. Un saut de position est borné au morceau ;
un direct ne se déplace pas.

### Du PC vers le téléphone

| `type` | Champs |
| --- | --- |
| `pair.accepted` | `deviceToken`, `pcId`, `pcName` |
| `pair.rejected` | `reason` : `expired`, `used`, `unknown` |
| `hello.ok` | `pcId`, `pcName`, `protocol` (1), `features` (liste de chaînes), `granted` (liste de chaînes), `accent` (`#rrggbb`) |
| `hello.rejected` | `reason` : `unknown_device`, `bad_token`, `protocol` |
| `accent` | `color` (`#rrggbb`) : la couleur d'accent de la barre a changé |
| `rights` | `granted` : les droits de ce téléphone ont changé pendant la session |
| `media.state` | `media` : `{ appId, appName, title, artist, playing, positionMs, durationMs, artKey }` ou `null` (rien ne joue) ; `sessions` : liste de `{ appId, name }` |
| `media.art` | `artKey`, `jpeg` : pochette du morceau, JPEG carré de 300 px au plus et 47 Kio au plus, en base64 (la trame tient dans 64 Kio) |
| `volume.state` | `volume` (de 0 à 1), `muted` |

Après `unknown_device` ou `bad_token`, le téléphone ne réessaie pas : il propose un nouvel
appairage. `accent` est la couleur dont l'appli tire toute sa palette (pochette pendant la
lecture, sinon fond d'écran, sinon l'orange `#f97316`).

Le PC n'envoie que des changements : `media.state` à chaque morceau, lecture ou pause, durée,
lecteur, ou quand la position s'écarte de plus de 1,5 s de celle attendue ; `media.art` une fois
par morceau (`artKey` change avec le morceau), puis à chaque nouvel abonnement ; `volume.state`
quand le volume ou le muet change. `positionMs` vaut au moment de l'envoi : le téléphone la
fait avancer lui-même depuis la réception, jamais d'après une heure du PC (les horloges des deux
appareils diffèrent). `durationMs` vaut 0 pour une radio ou un direct.

## Compatibilité

- `features` dit ce que ce PC sait faire : `battery` (Horizon 0.8), `media` (0.9). L'appli ne
  montre que ce que le PC annonce ; le PC ignore ce qu'il n'a pas annoncé.
- `granted` dit ce que ce téléphone a le droit de faire : `battery`, `media`, `sound`, `input`.
  Par défaut : `battery`, `media`, `sound` ; `input` ne s'accorde que dans Horizon. Absent
  (Horizon 0.8) : les droits sont les `features`. Une fonction annoncée mais non accordée est
  montrée par l'appli avec l'explication « à autoriser dans Horizon › Téléphone ».
- Un type de message inconnu est ignoré, un champ inconnu aussi. Ajouter un champ ou un type ne
  change donc pas la version du protocole ; changer le sens d'un champ existant, si.

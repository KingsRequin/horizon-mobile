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

`kind` vaut `phone`, `watch`, `earbuds` ou autre chose (traité comme « autre »). `percent` va
de 0 à 100. `atMs` est l'heure de la mesure (millisecondes Unix, horloge du téléphone) ; une
heure à plus de 5 minutes dans le futur est remplacée par l'heure de réception. Un envoi
remplace le précédent du même téléphone : il porte tous ses appareils, 8 au plus. Le téléphone
envoie à l'ouverture de la session, puis quand un pourcentage ou un état de charge change.

### Du PC vers le téléphone

| `type` | Champs |
| --- | --- |
| `pair.accepted` | `deviceToken`, `pcId`, `pcName` |
| `pair.rejected` | `reason` : `expired`, `used`, `unknown` |
| `hello.ok` | `pcId`, `pcName`, `protocol` (1), `features` (liste de chaînes), `accent` (`#rrggbb`) |
| `hello.rejected` | `reason` : `unknown_device`, `bad_token`, `protocol` |
| `accent` | `color` (`#rrggbb`) : la couleur d'accent de la barre a changé |

Après `unknown_device` ou `bad_token`, le téléphone ne réessaie pas : il propose un nouvel
appairage. `accent` est la couleur dont l'appli tire toute sa palette (pochette pendant la
lecture, sinon fond d'écran, sinon l'orange `#f97316`).

## Compatibilité

- `features` dit ce que ce PC sait faire. En version 1 : `battery`. L'appli ne montre que ce
  que le PC annonce ; le PC ignore ce qu'il n'a pas annoncé.
- Un type de message inconnu est ignoré, un champ inconnu aussi. Ajouter un champ ou un type ne
  change donc pas la version du protocole ; changer le sens d'un champ existant, si.

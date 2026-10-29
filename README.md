# NeptunCore

Alle Neptun-Plugins in **einer** JAR (Paper 1.21.x, Java 21).

| Modul (`config.yml → modules`) | Ersetzt | Befehle |
|---|---|---|
| `neptun` | Neptun | `/spawn /setspawn /home /sethome /delhome /rtp` |
| `worth` | NeptunWorth | `/worth /sell /shop /neptunworth (/nw)` |
| `ah` | Neptun-AH | `/ah` |
| `trade` | NeptunTrade | `/trade`, Shift + Rechtsklick auf Spieler |
| `social` | NeptunSocial | `/friends /settings /friendsread /friendsgive` |
| `unknownkiller` | NeptunUnknownKiller | – (Todesnachricht) |
| `minisk` | MiniSk 1.1.0 | `/discord /dc /store /minisk reload` + `.sk`-Skripte |

Zusätzlich: `/neptun` (Modulstatus) und `/neptun reload` (lädt alle Modul-Configs neu).

## Installation
1. Die alten Einzel-Plugins aus `plugins/` **entfernen** (sonst doppelte Befehle).
2. `NeptunCore-1.0.0.jar` in `plugins/` legen, Server starten.
3. Beim ersten Start werden vorhandene Daten der alten Plugins (`plugins/NeptunAH/`, `plugins/NeptunWorth/` …)
   nach `plugins/NeptunCore/<modul>/` **kopiert** (Homes, Angebote, Preise, Freunde, Configs). Die alten Ordner bleiben unverändert.
   Abschalten mit `migrate-old-data: false`.

## Konfiguration
- `plugins/NeptunCore/config.yml` – Module einzeln an/aus (Neustart nötig).
- `plugins/NeptunCore/<modul>/config.yml` – wie bisher je Plugin. MiniSk-Skripte: `plugins/NeptunCore/minisk/scripts/`.

## Abhängigkeiten (optional, per Reflection)
Vault (Money) und PlayerPoints (Neptuns) – gemeinsam genutzt von Worth, AH und Trade (eine Klasse `EconomyBridge`).
LuckPerms für Rang-Permissions (`neptun.rank.*`, `neptunah.limit.*`).

## Bauen
`mvn package` → `target/NeptunCore-1.0.0.jar`. Auf GitHub baut `.github/workflows/build.yml` die JAR automatisch (Reiter *Actions → Artifacts*).

## Änderungen gegenüber den Einzel-Plugins
- Gemeinsame Basisklasse `Module`; jedes Modul hat eigenen Ordner und eigene `config.yml`.
- AH und Trade nutzen die Vault/PlayerPoints-Anbindung aus Worth (kein VaultAPI-Compile-Dependency mehr). Trade rollt bei fehlgeschlagener Zahlung jetzt sauber zurück.
- NeptunSocial: Compile-Fehler im Original behoben (`p` → `sender` in `openGiftDeposit`).
- MiniSk-Beispielskript: `/shop` → `/minishop`, weil `/shop` jetzt von Worth belegt ist.
- `/neptun reload` lädt alle Module neu (statt nur Neptun).

## Wichtig
Dieser Stand wurde **nicht gegen die Paper-API kompiliert und nicht auf einem Server getestet** (kein Zugriff auf das Paper-Repo in der Entwicklungsumgebung).
Erst per GitHub Actions bauen und auf einem Testserver prüfen.

---

## Änderungen (Version mit RTP/Trade/[i]/NCW/Messages)

**Neue Module** (alle in `config.yml` einzeln abschaltbar):
- `chatitem` – `[i]` / `[item]` im Chat zeigt das Item in der Hand: Name in Item-Farbe, Hover = komplette Inventar-Beschreibung inkl. Verzauberungen. Optional `inline-enchants: true`.
- `messages` – `/nm` (früher NeptuneMessages), jetzt Modul. Recht: `neptunemessages.use` (Standard: OP).
- `whitelist` – `/ncw` (früher NeptuneCommandWhitelist), braucht LuckPerms, wird sonst sauber übersprungen.

**RTP** (`plugins/NeptunCore/neptun/config.yml`, Abschnitt `rtp:`):
- `/rtp` teleportiert direkt in der Welt, in der man ist. Wartezeit `delay-seconds: 5` für alle Ränge, Abbruch bei Bewegung (`cancel-on-move`).
- Ränge = LuckPerms-Gruppen (`rtp.ranks.<gruppe>`): eigener `cooldown`, Radius, `priority`. Kleinste Zahl gewinnt; wer nur `member` erbt, bekommt die member-Werte.
- Nether/End: `rtp.worlds.<welt>.enabled: false` (Standard: aus). Optionales Menü: `rtp.menu.enabled: true` (Overworld in der Mitte).
- Home-Limits nutzen denselben Rang.

**Trade:** `/trade <Spieler>` sendet jetzt die Anfrage (mit klickbarem Annehmen/Ablehnen), Tab-Completion für Spielernamen.
**Social:** altes `[i]` entfernt (macht jetzt `chatitem`); `/settings` ist ein eigener Befehl.
**Worth:** Start-Log zeigt, ob Vault (Money) und PlayerPoints (Neptuns) gefunden wurden; Such-Map thread-sicher.

Bauen: `mvn package` oder GitHub Actions (Workflow liegt bei). Die fertige JAR liegt dann in `target/`.

# CopycatSign

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C3W0229LCP)

[🇩🇪 Deutsche Version weiter unten](#deutsch)

## English

Freely placeable picture signs that exist as a **block**, not an entity — so they get carried
correctly when built into a Create contraption (e.g. a train), instead of being destroyed/ejected
like entity-based picture mods (e.g. Immersive Paintings). Standalone NeoForge mod, Create is an
**optional** dependency (only needed for contraption compatibility, not for placing/using signs).

### What it does

- **Choice of frame material**: right-click a placed sign with any block item to set its back
  material, and with a second item in your off-hand to set the (separate) edge material — cycling
  through NBT-stored `BlockState`s the same way Create's own Copycat blocks work. Follows
  FramedBlocks' acceptance rules (recursion guard, blacklist tag, block-entity block gate with a
  whitelist tag, "solid render or frameable tag" check), not Copycat's simpler allow/deny pair.
- **4 thickness levels**, switchable per placed block (not separate items): very thin (1px), thin
  (2px, default), medium (4px), thick (6px).
- **5 depth positions**, cycled with an empty-hand right-click: back, back-middle, middle,
  front-middle, front — stored as a fraction so it adapts automatically to whichever thickness is
  set.
- **Custom image upload**: right-click with an empty hand (and no material in your off-hand) to
  open an in-game editor — pick a PNG/JPEG from your file system via a native file dialog, upload
  it (content-addressed by SHA-256, so re-uploading the same picture never duplicates storage), and
  adjust pan/zoom live with in-GUI sliders. Implemented end-to-end (upload pipeline, server storage,
  client texture cache, renderer, editor GUI) — **first real live test still pending**, see the
  Status section below.
- Two placeholder sign motifs ship as test content (`picture_hogwarts_express_schild`,
  `picture_5972`) plus a blank sign (`picture_blank`) meant as the actual starting point for your
  own custom image.

### Status

Feature-complete for material choice, thickness, and depth positioning (all live-tested and
working). The custom image upload feature compiles cleanly and every individual piece has been
reasoned through carefully, but has not yet been exercised end-to-end in an actual game session —
treat it as "should work" rather than "confirmed working" until that first test happens. See
[ROADMAP.md](ROADMAP.md) for the full development history, including two crash fixes already found
during the (interrupted) first test pass.

### Building from source

Create, Ponder, and Create Factory Abstractions are **not** committed to this repo (see
`.gitignore`) — they're only needed at compile time for contraption-compatibility testing. Drop
matching jars into `libs/` before building:

- `create-*.jar` (tested against Create 6.0.10 for NeoForge 1.21.1)
- `ponder*.jar`
- `create_factory_abstractions*.jar`

### Other CobbleCompanion-family projects

CopycatSign isn't part of the CobbleCompanion family itself (Create is optional, no Cobblemon
dependency at all), but it's made by the same author:

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) and its extensions/bundles
  ([CobbleDollars](https://github.com/siralusian/CobbleCompanion-CobbleDollars),
  [CobbleDollars/Create](https://github.com/siralusian/CobbleCompanion-CobbleDollars-Create),
  [CobbleDollars/CustomNPCs](https://github.com/siralusian/CobbleCompanion-CobbleDollars-CustomNPCs),
  [CobblemonWorker](https://github.com/siralusian/CobbleCompanion-CobblemonWorker),
  [Create/Let's Do](https://github.com/siralusian/CreateLetsDo),
  [AllInOne](https://github.com/siralusian/CobbleCompanion-AllInOne),
  [CobbleDollars-Bundle](https://github.com/siralusian/CobbleCompanion-CobbleDollarsBundle))
- [CreativeMenu](https://github.com/siralusian/CreativeMenu) — a fully customizable Creative
  inventory menu.

---

## Deutsch

Frei platzierbare Bild-Schilder, die als **Block** existieren, nicht als Entity — dadurch werden
sie beim Zusammenbauen einer Create-Kontraption (z.B. ein Zug) korrekt mitgenommen, statt wie
Entity-basierte Gemälde-Mods (z.B. Immersive Paintings) dabei zerstört/ausgestoßen zu werden.
Eigenständige NeoForge-Mod, Create ist eine **optionale** Abhängigkeit (nur für
Kontraption-Kompatibilität nötig, nicht zum Platzieren/Benutzen der Schilder).

### Was es macht

- **Wählbares Rahmenmaterial**: Rechtsklick auf ein platziertes Schild mit einem beliebigen
  Block-Item setzt das Rückseiten-Material, mit einem zweiten Item in der Nebenhand das (separate)
  Kanten-Material – gespeichert als `BlockState` in der BlockEntity, genau wie bei Creates eigenen
  Copycat-Blöcken. Folgt den Akzeptanz-Regeln von FramedBlocks (Rekursionsschutz,
  Blacklist-Tag, BlockEntity-Block-Sperre mit Whitelist-Tag, "solid render oder frameable Tag"
  -Prüfung), nicht Copycats einfacherem Erlauben/Verbieten-Paar.
- **4 Dicke-Stufen**, umschaltbar am platzierten Block (keine separaten Items): sehr dünn (1px),
  dünn (2px, Standard), mittel (4px), dick (6px).
- **5 Tiefenpositionen**, per Rechtsklick mit leerer Hand durchgeschaltet: hinten,
  hinten-mitte, mitte, vorne-mitte, vorne – als Bruchteil gespeichert, passt sich also automatisch
  an die jeweils eingestellte Dicke an.
- **Eigenes Bild hochladen**: Rechtsklick mit leerer Hand (und ohne Material in der Nebenhand)
  öffnet einen Editor im Spiel – PNG/JPEG per nativem Datei-Dialog auswählen, hochladen
  (Content-Addressing per SHA-256, dasselbe Bild mehrfach hochzuladen belegt also nie doppelt
  Speicher), Ausschnitt/Zoom live per Regler im GUI anpassen. Durchgängig implementiert
  (Upload-Pipeline, Server-Speicherung, Client-Textur-Cache, Renderer, Editor-GUI) – **der erste
  echte Live-Test steht noch aus**, siehe Status-Abschnitt unten.
- Zwei Platzhalter-Schildmotive als Test-Content dabei (`picture_hogwarts_express_schild`,
  `picture_5972`), plus ein leeres Schild (`picture_blank`) als eigentlicher Ausgangspunkt für ein
  eigenes Bild.

### Status

Feature-vollständig für Material-Wahl, Dicke und Tiefenposition (alle live getestet und
funktionsfähig). Das Bild-Upload-Feature kompiliert sauber und jeder einzelne Baustein wurde
sorgfältig durchdacht, wurde aber noch nicht komplett end-to-end in einer echten Spielsitzung
durchgespielt – bitte als "sollte funktionieren" statt "bestätigt funktionierend" behandeln, bis
dieser erste Test stattgefunden hat. Die komplette Entwicklungsgeschichte inklusive zweier bereits
gefundener Absturz-Fixes aus dem (unterbrochenen) ersten Testlauf steht in
[ROADMAP.md](ROADMAP.md).

### Aus dem Quellcode bauen

Create, Ponder und Create Factory Abstractions sind **nicht** in diesem Repo enthalten (siehe
`.gitignore`) – sie werden nur zur Kompilierzeit für die Kontraption-Kompatibilität gebraucht.
Passende Jars vor dem Bauen in `libs/` legen:

- `create-*.jar` (getestet mit Create 6.0.10 für NeoForge 1.21.1)
- `ponder*.jar`
- `create_factory_abstractions*.jar`

### Weitere Projekte aus der CobbleCompanion-Familie

CopycatSign gehört nicht selbst zur CobbleCompanion-Familie (Create ist optional, gar keine
Cobblemon-Abhängigkeit), stammt aber vom selben Autor:

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) und seine
  Erweiterungen/Bundles
  ([CobbleDollars](https://github.com/siralusian/CobbleCompanion-CobbleDollars),
  [CobbleDollars/Create](https://github.com/siralusian/CobbleCompanion-CobbleDollars-Create),
  [CobbleDollars/CustomNPCs](https://github.com/siralusian/CobbleCompanion-CobbleDollars-CustomNPCs),
  [CobblemonWorker](https://github.com/siralusian/CobbleCompanion-CobblemonWorker),
  [Create/Let's Do](https://github.com/siralusian/CreateLetsDo),
  [AllInOne](https://github.com/siralusian/CobbleCompanion-AllInOne),
  [CobbleDollars-Bundle](https://github.com/siralusian/CobbleCompanion-CobbleDollarsBundle))
- [CreativeMenu](https://github.com/siralusian/CreativeMenu) — ein frei anpassbares
  Creative-Inventar-Menü.

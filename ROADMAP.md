# Copycat Sign – Roadmap

Eigenständige NeoForge-Mod (MC 1.21.1 / NeoForge 21.1.233). Frei platzierbare Bild-Schilder, die als
**Block** (nicht Entity) existieren, damit sie beim Zusammenbauen einer Create-Kontraption (z.B. Zug)
korrekt mitgenommen werden, statt wie Entity-basierte Gemälde-Mods (Immersive Paintings) dabei
zerstört/ausgestoßen zu werden. Verifiziert per Create-Quellcode-Analyse: Create ruft für jede
BlockEntity in einer Kontraption ganz normal `BlockEntityRenderHelper.renderBlockEntities(...)` auf
(gegen eine `VirtualRenderWorld`), unabhängig vom Flywheel-Visualisierungspfad - ein normaler
`BlockEntityRenderer` wird also automatisch mitgenommen.

Ursprung: als Teil von CobbleCompanion-Everything entstanden (Session 2026-08-03/04), dort erst live
getestet und mehrfach korrigiert, dann als eigenständiges Projekt ausgelagert, weil das eigentliche
Ziel (Create-Kompatibilität) ein größerer, in sich geschlossener Baustein ist. Erst wenn dieser Mod
komplett funktioniert, wird er wieder in CobbleCompanion Everything integriert.

## Phase 0 – Fundament (erledigt)

- Eigenständiges Gradle/NeoForge-Projekt (build.gradle, gradle.properties, settings.gradle, Wrapper),
  Create + ponder + create_factory_abstractions als lokale `libs/*.jar`-Abhängigkeit (nur für
  Kontraptions-Kompatibilität - der Mod selbst hat aktuell keine Create-spezifischen Hooks nötig)
- Mod-Grundgerüst (`CopycatSign`), `neoforge.mods.toml` (Create als `optional`-Abhängigkeit)
- Aus CobbleCompanion-Everything portierter, dort bereits live-getesteter Grundstock:
  `AbstractPictureBlock` / `PictureBlockSingle` / `PictureBlockItem` / `PicturePosition`
  (Paket `com.copycatsign.block`)
- Zwei Beispiel-Schilder als Platzhalter-Content (Hogwarts-Express-Schild 3x2, Lokschild 5972 1x1) -
  dienen nur zum Testen der Mechanik, nicht der eigentliche Zweck des Mods (der ist Feature 1+2 unten)

### Bereits gelöste technische Kernprobleme (aus der CobbleCompanion-Session, gelten hier unverändert)

- **1 physischer Block, übergroßes Modell**: Block-Model-Elemente dürfen laut Vanilla
  (`BlockElement.MIN_EXTENT`/`MAX_EXTENT`) Koordinaten von -16 bis 32 haben (1 Block über die eigene
  Zelle hinaus in jede Richtung) - Hitbox bleibt trotzdem exakt in der eigenen 0-16-Zelle
- **Rotationstabelle für alle 6 Facings** aus der echten `piston.json` verifiziert (nicht aus dem
  Gedächtnis geraten - Canonical-Modell zeigt nach Norden, x/y-Rotation pro Facing wie bei Vanilla-
  Kolben/Dropper)
- **Silhouetten-Extrusion statt Rahmen/Kanten-Textur**: Vanillas `ItemModelGenerator`-Algorithmus
  (der Item-3D-Look in der Hand) 1:1 nach PowerShell portiert - scannt die Alpha-Maske, erkennt
  zusammenhängende Kanten-Spans, baut pro Span eine hauchdünne "Stufe" mit passendem Textur-Ausschnitt.
  Für die Rückseite: separate Textur (Eichenholz-Maserung + Alpha-Maske des Vorderseiten-Bilds), damit
  Holz nur dort erscheint, wo auch wirklich Bild ist
- **Block-Modell-Textur-Fallen**: Flächen-`"texture"` MUSS `"#variable"` sein, kein direkter Pfad
  (sonst stiller Fallback auf die "fehlende Textur"); ungerade Texturbreiten/-höhen (z.B. 683px)
  können nicht gemippt werden und fliegen komplett aus dem Atlas; PowerShell `Out-File -Encoding utf8`
  schreibt eine UTF-8-BOM, die Minecrafts JSON-Parser stolpern lassen kann (`WriteAllText` mit
  `UTF8Encoding($false)` verwenden)
- **Tiefenposition statt fester Dicke**: Rechtsklick mit dem eigenen Item zykelt 5 feste
  Tiefenstufen (hinten/hinten-mitte/mitte/vorne-mitte/vorne), kostenlos (keine Item-Kosten, da nur
  Umpositionierung). Als Bruchteil (0.0-1.0) statt Festwert gespeichert, damit es sich automatisch an
  unterschiedliche Dicken (siehe Feature 3 unten) anpasst, ohne dass die Hitbox aus der eigenen Zelle
  herausragen kann

## Feature 1 – Wählbares Rahmenmaterial (Rückseite/Kanten) - erledigt

Vorbild: Create Copycat (`CopycatBlockEntity` + `CopycatModel`). Speichert einen `BlockState material`
in einer BlockEntity (erste BlockEntity in diesem Mod - aktuell rein blockstate-basiert), exponiert via
NeoForges `ModelData`-System an ein custom `BakedModel`, das zur Bake-Zeit das bereits gebackene Modell
des gewählten Materials abfragt und dessen Quads auf die eigene Silhouette croppt. Rechtsklick mit
einem Block-Item setzt das Material (verbraucht 1 Item wie normales Platzieren).

**Akzeptanz-Regeln folgen FramedBlocks, nicht Copycats simplerem Allow/Deny-Paar** (Nutzer-Entscheidung
2026-08-04). Aus `BlockCamoContainerFactory.isValidBlock` portiert - ein Material wird abgelehnt wenn:
1. es selbst ein Copycat-Sign-Block ist (Rekursion vermeiden)
2. es auf einer Blacklist-Tag-Liste steht
3. es eine eigene BlockEntity hat, UND Server-Config das generell verbietet, UND es nicht auf einer
   BlockEntity-Whitelist-Tag-Liste steht
4. es nicht "solid render" ist (Glasscheiben, Stufen, ...), UND es nicht auf einer "frameable"-Tag-Liste
   steht

Für uns: eigene Tags `copycatsign:material_blacklisted`, `copycatsign:material_blockentity_whitelisted`,
`copycatsign:material_frameable` + ein Server-Config-Schalter (`allowBlockEntityMaterials`), dieselbe
4-Schritte-Struktur.

Rendering-Technik: statt Copycats Quad-Cropping reicht bei uns ein reines UV-Remapping (Silhouette
bleibt immer gleich, nur die Textur wechselt) - `PictureBakedModel` (ein `BakedModelWrapper`) wird für
jede Blockstate-Variante über `ModelEvent.ModifyBakingResult` eingehängt.

**Zweite Korrektur (2026-08-05, Flackern beim Rückseiten-Material):** Flächen, deren 4 Ecken exakt
auf ganzzahligen Blockgrenzen liegen (z.B. die große Rückseiten-Fläche des 3x2-Hogwarts-Schilds:
-1.0/2.0 in X, 0.0/2.0 in Y), sind mit reinem `coord - floor(coord)` doppelt problematisch: (1)
numerisch instabil - Fließkomma-Rauschen kann eine Ecke knapp über, eine andere knapp unter die
Ganzzahl fallen lassen, wodurch dieselbe Fläche stark unterschiedliche UV-Werte je Ecke bekommt →
blickwinkelabhängiges Flackern; (2) selbst mit stabiler Rundung bekommen dann ALLE 4 Ecken denselben
Bruchteil (0.0) → die GPU interpoliert zwischen vier identischen UV-Werten → die ganze Fläche zeigt
nur einen einzelnen Texel statt zu kacheln. Fix in `PictureBakedModel.remapQuad`: pro Achse prüfen,
ob die Fläche mindestens 1 Block breit ist (`maxU-minU >= 1.0`) - falls ja, wird die Textur über die
EIGENE Ausdehnung der Fläche gestreckt (0→1, kein Kacheln - technisch ohnehin nicht möglich, da eine
einzelne Fläche nur 4 Eck-UVs hat und Minecrafts Atlas kein echtes Wrapping über eine Sprite-Grenze
hinaus unterstützt); falls nein (die meisten kleinen Kanten-Quads), wird weiter per absoluter
Weltposition gekachelt (mit stabiler Rundung, Toleranz 1e-4 wie in `ModelBlockRenderer#calculateShape`).

**Erste Korrektur (2026-08-05):** Die UV wird NICHT von der alten Textur-Position übernommen
(`newSprite.getU(oldSprite.getUOffset(u))` - erste Version, fehlerhaft), sondern frisch aus der
Vertex-XYZ-Position der Quad berechnet, gewrapped auf 1 Block (`coord - floor(coord)` als 0-1-Fraktion,
dann `newSprite.getU(fraction)`). Grund: unsere Kanten-Quads haben durch die Silhouetten-Extrusion
absichtlich winzige, sehr präzise UV-Ausschnitte (1 Pixel aus dem großen Quellbild) - übernimmt man
diese 1:1 auf eine neue 16x16-Material-Textur, wird ein einzelner Texel über die ganze Quad gestreckt
(sichtbar als "von links nach rechts eine Farbe, aber viele verschiedene Farben von unten nach oben").
Die geometriebasierte UV kachelt das Material stattdessen ganz normal, wie bei einer echten Wand.

## Feature 1b – Zwei getrennte Material-Slots (Rückseite vs. Kanten) - erledigt

FramedBlocks-`IFramedDoubleBlockEntity`-Vorbild: zwei unabhängige `ModelProperty<BlockState>`
(`BACK_MATERIAL`/`EDGE_MATERIAL`), zwei NBT-Felder in `PictureBlockEntity`. Rechtsklick mit einem
fremden Block-Item in der Hauptmi setzt das Rückseiten-Material, in der Nebenhand (Taste F zum
Tauschen) das Kanten-Material - unterscheidet sich per `InteractionHand hand`-Parameter, der schon
Teil des normalen Interact-Pakets ist.

**Sneak/Strg bewusst NICHT verwendet** (Nutzer-Entscheidung 2026-08-05): Sneak ist vanilla-Konvention
für "Block davor platzieren statt interagieren" (Truhen, Öfen, ...) - Wiederverwendung hier würde das
unterlaufen. Strg wäre möglich gewesen, aber im Gegensatz zu Sneak hat Minecraft dafür kein
automatisch synchronisiertes Server-Flag - hätte ein eigenes Netzwerk-Paket gebraucht, das den
Strg-Status laufend zum Server meldet. Nebenhand braucht keine neue Netzwerk-Logik.

Wichtige Erkenntnis: Vorderseite (Bild) UND Kanten-Quads referenzieren beide dieselbe `#front`-Textur
- eine Klassifizierung per Sprite-Identität (wie noch in Feature 1 für die Rückseite) kann die beiden
also nicht unterscheiden. Stattdessen klassifiziert `PictureBakedModel` jetzt rein über die
Quad-`Direction` relativ zu `FACING` (aus dem übergebenen `BlockState` gelesen, kein Vorab-Resolve
mehr nötig): Direction == FACING → Bild (nie angefasst), == FACING.getOpposite() → Rückseiten-Slot,
alles andere (senkrecht) → Kanten-Slot. Funktioniert unverändert für alle 6 Facings, da FACING pro
Aufruf aus dem tatsächlichen State gelesen wird statt einmalig vorberechnet.

**Offen/zurückgestellt** (Nutzer-Entscheidung 2026-08-05): Rückseiten-Material füllt aktuell die volle
Box statt der Bild-Silhouette zu folgen, weil die Silhouette nur über den Alpha-Kanal unserer eigenen
`_backing`-Textur kommt, nicht über echte Geometrie - ein beliebiges Fremdmaterial hat diesen Alpha-
Kanal nicht. Wird später angegangen, keine Priorität aktuell.

## Feature 2 – Mehrere Dicke-Varianten - erledigt (Architektur 2026-08-05 überarbeitet, siehe unten)

Nutzer-Entscheidung 2026-08-04: separate registrierte Blöcke statt zusätzlicher Klick-Ebene. 4 Stufen
registriert (Nutzer-Entscheidung 2026-08-05): "sehr dünn"=1px, "dünn"=2px (unverändert, alter
unsuffixter Block-Id, um bestehende Test-Platzierungen nicht zu brechen), "mittel"=4px, "dick"=6px -
jeweils für beide Schild-Motive, macht 6 neue Blöcke.

Modell-Generierung: keine komplette Neugenerierung nötig - die Silhouette (X/Y) ist unabhängig von der
Dicke, nur die Z-Tiefe pro Position ändert sich. Ein Skript hat die exakte Offset-Formel aus
`PicturePosition.offsetFor` gegen die echten Werte der bestehenden "dünn"-Modelle verifiziert (inkl.
Erkenntnis: der ursprüngliche Generator nutzte PowerShells `[Math]::Round()` mit Banker's Rounding
(round-half-to-even), NICHT Javas `Math.round()` (rundet .5 immer auf) - für Position `front_middle`
bei Dicke 2 weichen beide Formeln um 1 Einheit voneinander ab; die Modelle folgen konsequent
Banker's-Rounding, minimal inkonsistent mit der Java-Kollisionsform, aber unverändert seit der
Ur-Generierung und nicht Teil dieser Änderung). Auf Basis der verifizierten Formel wurden alle
Z-Werte in den bestehenden Positions-Modellen (per gezieltem Such-Ersetzen, nur `"from"`/`"to"`-3.
Wert) auf die neue Dicke umgerechnet, inklusive der 0,1-Einheiten-Nudge für Back/Front (siehe
Lichtfix oben) - jeweils 5 Positions-Dateien × 2 Schilder × 3 neue Dicken = 30 neue Modell-Dateien,
plus je 6 Blockstates/Item-Modelle/Loot-Tables. Texturen sind identisch zur "dünn"-Variante
(unverändert referenziert, keine neuen Textur-Dateien nötig).

**Wichtig für zukünftige Ergänzungen:** Alle 8 Blöcke (2 Motive × 4 Dicken) müssen konsistent an 3
Stellen eingetragen sein: `PictureBlocks.java` (Registrierung), `PictureBlockEntities.java`
(BlockEntityType-Builder - sonst Inkonsistenz beim BE-Erstellen), `CopycatSignClient.java`
(Modell-Wrapping fürs Material-Feature - sonst rendert das Material-System für diesen Block nicht).

### Architektur-Umbau (2026-08-05): Dicke wird Blockstate-Property statt separatem Block

Nutzer-Entscheidung, ausgelöst durch die Design-Frage vor Feature 3: da wir sowieso ein GUI zum
Bild-Hochladen bauen, macht es aus Spielersicht mehr Sinn, nur **einen** Block pro Motiv zu haben und
die Dicke später im GUI umzuschalten - analog dazu, wie `POSITION` schon heute per Rechtsklick
zyklisch geändert wird. `PictureThickness` ist jetzt ein Enum mit `EnumProperty<PictureThickness>
THICKNESS` (wie `POSITION`), `AbstractPictureBlock`/`PictureBlockSingle` haben keinen
Dicke-Konstruktor-Parameter mehr. Effekt: aus 8 Hogwarts/5972-Blöcken + potenziell 4
`picture_blank`-Blöcken wurden 3 Blöcke insgesamt (`picture_blank`, `picture_hogwarts_express_schild`,
`picture_5972`), jeweils mit 240 Blockstate-Varianten (4 Dicken × 60 Facing/Position/UpHint-
Kombinationen wie vorher). Die Modell-JSON-Dateien selbst blieben unverändert (liegen weiter in
eigenen `<sign>_<suffix>/`-Ordnern pro Dicke) - nur die Blockstate-Datei referenziert jetzt alle 4
Ordner statt vorher 4 separate Blockstate-Dateien je einen. Keine Rückwärtskompatibilität für
bereits platzierte Dicke-Varianten in der Testwelt (Nutzer-Entscheidung, Testwelt wird neu bestückt).

## Feature 3 – Eigenes Bild hochladen (custom Textur) - geplant, größter Brocken, noch nicht begonnen

Vorbild: Immersive Paintings (`ServerPaintingManager`/`ClientPaintingManager`), aber als **Block**
umgesetzt statt Entity (das ist der ganze Sinn dieses Mods). Kernunterschied: das Bild wird als
`DynamicTexture` zur Laufzeit registriert (`TextureManager.register`), außerhalb des normalen
Textur-Atlas - kann daher nicht über ein statisches JSON-Blockmodell gezeichnet werden, sondern braucht
einen `BlockEntityRenderer`, der die Vorderseite manuell mit der gebundenen Textur zeichnet, während
Rückseite/Kanten weiter über den normalen gebackenen Pfad laufen (Mischbetrieb).

Grobe Bausteine (noch nicht im Detail geplant):
- ~~Client→Server Bild-Upload~~ - erledigt (F3.1)
- ~~Server-seitige Speicherung~~ - erledigt (F3.1)
- ~~`BlockEntityRenderer` fürs Vorderseiten-Element~~ - erledigt (F3.3)

### F3.2 – BlockEntity-Datenmodell (erledigt 2026-08-06)

`PictureBlockEntity` um `imageHash` (SHA-256, siehe F3.1) + `panX`/`panY`/`zoom` (Fraktionen, Standard
zentriert/ungezoomt) erweitert. Bewusst KEINE `ModelProperty` fürs Bild selbst nötig (anders als bei
den Materialien) - ein `BlockEntityRenderer` bekommt die BlockEntity direkt übergeben, muss also nicht
über den ModelData-Umweg gehen. Eine neue `ModelProperty<Boolean> HAS_IMAGE`
(`PictureImageProperty`) wird trotzdem gebraucht, aber nur damit `PictureBakedModel` weiß, ob es seine
eigene "#front"-Quad unterdrücken soll (siehe F3.3).

### F3.3 – Client-Bild-Cache + DynamicTexture + BlockEntityRenderer (erledigt 2026-08-06)

`ClientImageManager`: Cache `Hash -> ResourceLocation` einer registrierten `DynamicTexture`, mit
Disk-Cache (`<Spielverzeichnis>/copycatsign/imagecache/`) und On-Demand-Nachladen (`ImageRequestPayload`
C2S, `ImageDataResponsePayload` S2C, gestückelt wie der Upload - `ChunkSender` fasst die gemeinsame
Chunking-Konstante beider Richtungen zusammen).

**Rendering-Strategie (wichtige Design-Entscheidung):** Statt Rotation/Position/Vertex-Reihenfolge für
die Front-Fläche selbst herzuleiten (fehleranfällig, nicht ohne Live-Test verifizierbar), holt sich
`PictureBlockEntityRenderer` die bereits korrekt gebackene Front-Quad des ganz normalen Modells
(`model.getQuads(state, null, ..., ModelData.EMPTY, null)`, gezielt mit `ModelData.EMPTY` um
`PictureBakedModel`s eigene Unterdrückung zu umgehen) und texturiert nur ihre UVs um - exakt dieselbe
UV-Fraktion-Technik wie beim Material-System (`PictureBakedModel.remapQuad`), nur mit Ziel
`VertexConsumer` statt `BakedQuad`. Dadurch übernimmt vanillas eigener Modell-Baker automatisch
korrekte Rotation/Position/Wicklung - kein eigener Rotations-Code nötig, der ohne Testmöglichkeit
falsch sein könnte. Pan/Zoom wird als Crop-Fenster (`u0,v0,u1,v1`) aus `panX/panY/zoom` berechnet und
linear auf die UV-Fraktion jeder Ecke gemappt.

`PictureBakedModel` unterdrückt seine eigene Front-Quad jetzt, wenn `HAS_IMAGE=true` UND es sich nicht
um eine `ModelData.EMPTY`-Anfrage handelt (Unterscheidung via `Boolean.TRUE.equals(...)` statt reinem
Unboxing, da `ModelData.EMPTY.get(...)` `null` liefert, nicht `false`).

**Noch keine sichtbare In-Game-Wirkung** - `imageHash` kann noch nirgends gesetzt werden (kommt mit
F3.4). Die eigentliche visuelle Korrektheit (Rotation stimmt, Bild sitzt an der richtigen Stelle,
Crop/Zoom-Mapping sieht vernünftig aus) ist entsprechend **noch nicht live verifiziert** - das sollte
der allererste Test sein, sobald F3.4 einen Weg bietet, `imageHash` tatsächlich zu setzen.

### F3.1 – Server-Speicherung + Upload-Pipeline (erledigt 2026-08-05)

Direkt nach Immersive-Paintings-Vorbild umgesetzt (Code dafür frisch dekompiliert und verifiziert,
nicht nur aus Erinnerung): `ImageUploadPayload` (C2S, gestückelt: `byte[] data, int segment, int
totalSegments`) + `SegmentManager` (reassembliert pro Spieler-UUID). Nach vollständigem Empfang:
Dekodierung via `ImageIO`, Validierung (max. Pixel-Dimension + max. Dateigröße, beides
`CopycatSignConfig`), SHA-256-Hash der PNG-Bytes als Identifier (Content-Addressing - gleiches Bild
zweimal hochgeladen kollabiert auf eine gespeicherte Kopie). Speicherung: `ServerImageStore`
(`SavedData`, Metadaten wie Uploader/Breite/Höhe) + `ImageFileCache` (Rohe PNG-Bytes als Datei unter
`<Welt>/copycatsign/images/<hash>.png` - bewusst NICHT durch NBT geschleift, Uploads können mehrere
MB groß sein). Antwort an den Spieler über `ImageUploadResultPayload` (S2C: Erfolg + Hash + Maße,
oder Fehler-Übersetzungsschlüssel).

**Netzwerk-Registrierung aufgeteilt** (wichtige Falle vermieden): das S2C-Payload wird NICHT von
`CopycatSignNetwork` (common) aus registriert, sondern in `CopycatSignClient` (Dist.CLIENT-gated) -
ein direkter Aufruf von common in client-gated Code hätte das Laden client-exklusiver Klassen auf
einem Dedicated Server erzwungen, trotz `@EventBusSubscriber`-Annotation auf der Zielklasse (die
Annotation verhindert nur FMLs eigene Listener-Registrierung, nicht das rohe Java-Klassenladen bei
einem direkten Methodenaufruf).

**Noch keine sichtbare In-Game-Wirkung** - es gibt noch keinen Ausl​öser (Command/GUI) zum tatsächlichen
Hochladen, das kommt mit F3.4. Der Client-Handler für die Erfolgs-Antwort loggt aktuell nur.

**Live im Spiel anpassbare Bild-Position/-Größe** (Nutzer-Entscheidung 2026-08-04, nicht nur eine
Konfiguration beim Anlegen eines neuen Schild-Typs durch uns): Spieler soll Ausschnitt/Zoom eines
bereits gewählten Bildes direkt am platzierten Block verändern können - braucht persistenten Zustand
pro Block (BlockEntity, siehe Feature 1) + eigene UI. Architektur-Detail noch offen, erst planen wenn
Feature 1+2 stehen und wir die BlockEntity/ModelData-Grundlagen am Laufen haben.

## Reihenfolge

1. ~~Feature 1 (Material-Wahl)~~ - erledigt, liefert die BlockEntity/ModelData-Grundlage, die Feature 3
   sowieso braucht
2. ~~Feature 1b (zwei getrennte Material-Slots)~~ - erledigt
3. ~~Feature 2 (Dicke-Varianten)~~ - erledigt
4. ~~Feature 3 (Custom-Bild-Upload + Live-Position/Zoom)~~ - Code komplett (F3.0-F3.4), **noch nicht
   live getestet** - das ist der nächste Schritt

### F3.4 – GUI-Screen (erledigt 2026-08-06, aber noch UNGETESTET)

`PictureEditorScreen`: Datei-Auswahl über `TinyFileDialogs` (LWJGL, Minecraft bringt das schon als
transitive Abhängigkeit mit - Vanilla selbst hat KEINEN Datei-Auswahl-Dialog, nur "Ordner extern
öffnen"), lokale Vorschau (eigene `DynamicTexture` unter einer festen Scratch-`ResourceLocation`,
unabhängig vom hash-basierten `ClientImageManager`-Cache), Hochladen-Button, 3 Pan/Zoom-Regler
(`AbstractSliderButton`-Unterklasse). Geöffnet per Rechtsklick mit leerer Hand
(`AbstractPictureBlock#useItemOn`) - Server schickt `OpenPictureEditorPayload` zurück an den
klickenden Spieler (Server-Rundweg bewusst gewählt, damit die BlockEntity-common-Klasse nie direkt
die client-only `Screen`-Klasse referenzieren muss). Nach erfolgreichem Upload wird das Bild
automatisch übernommen (`PictureEditPayload` mit `changeImage=true`), Pan/Zoom-Regler senden bei jeder
Änderung sofort ein Live-Update (`changeImage=false`). `PictureEditPayload` validiert serverseitig
Distanz (max. 8 Blöcke) und dass an der Position wirklich eine `PictureBlockEntity` sitzt.

**Registrierungs-Besonderheit:** Client-gebundene Payloads (`ImageUploadResultPayload`,
`ImageDataResponsePayload`, `OpenPictureEditorPayload`) werden NUR in der Dist.CLIENT-Klasse
registriert, nie zusätzlich in der gemeinsamen `CopycatSignNetwork` - das reicht aus, weil
"Registrierung" nur die EMPFANGENDE Seite betrifft (wie ein Payload dekodiert/behandelt wird); zum
SENDEN braucht der Server nur den `StreamCodec` der Payload-Klasse selbst, der unabhängig von der
Registrierung existiert.

**Wichtig - noch nicht live verifiziert:** Das gesamte Feature 3 (F3.0-F3.4) kompiliert sauber, wurde
aber noch nie tatsächlich im Spiel getestet. Bekannte Risikobereiche für den ersten Test:
- Sitzt das hochgeladene Bild an der richtigen Stelle/Ausrichtung? (Sollte durch die Wiederverwendung
  der bereits korrekt gebackenen Front-Quad-Geometrie funktionieren, siehe F3.3, aber ungetestet.)
- Funktioniert der native Datei-Dialog unter Windows reibungslos vom Hintergrund-Thread aus?
- Sieht das Pan/Zoom-Crop-Fenster sinnvoll aus (keine Seitenverhältnis-Korrektur bisher)?
- GUI-Layout (Button-/Regler-Positionen) - rein nach Berechnung gesetzt, nie gerendert gesehen.

### Zwei Bugs aus dem ersten Test gefixt (2026-08-06)

1. **RAM-Absturz beim Hochladen:** `runFileDialog` prüfte Dateigröße/Bildmaße erst NACHDEM die
   komplette Datei per `Files.readAllBytes` in den Heap geladen war - bei einer großen Bilddatei war
   der Speicher also schon weg, bevor die Prüfung überhaupt greifen konnte. Jetzt: Dateigröße per
   `Files.size()` (nur Metadaten, kein Lesen) VOR jedem Datei-Zugriff geprüft, Bildmaße per
   `ImageIO`-Header-Lesen (liest nur die paar Bytes Kopfdaten, nicht den vollen Pixel-Puffer) VOR dem
   vollständigen Einlesen/Dekodieren geprüft. Erst wenn beides besteht, wird die Datei überhaupt
   komplett gelesen.
2. **Leere-Hand-Konflikt (Bild-Editor vs. Kanten-Material):** `useItemOn` wird pro Hand einzeln
   aufgerufen (erst Hauptmi, dann nur bei PASS die Nebenmi). Der leere-Hand-Check für den Editor prüfte
   nur "ist DIESE Hand leer" - bei leerer Hauptmi + Material in der Nebenmi feuerte er sofort und die
   Nebenmi wurde nie geprüft, obwohl der Spieler eigentlich ein Kanten-Material setzen wollte. Fix:
   Eine leere Hauptmi öffnet den Editor nur noch, wenn die Nebenmi AUCH leer ist - sonst wird
   durchgereicht (PASS), damit der Nebenmi-Aufruf wie gewohnt das Material-Setzen übernehmen kann.

**Absturz blieb nach Fix 1 trotzdem bestehen** - die eigentliche Ursache lag woanders: Die
RunClient-Konfiguration (`build.gradle` -> `neoForge.runs.client`) hatte noch nie ein `-Xmx` gesetzt,
lief also mit dem JVM-Standard-Heap statt einer bewusst gewählten Grenze. Das reichte für reines
Blockplatzieren/Testen bisher aus, aber mit Bild-Dekodierung/`DynamicTexture` (Feature 3) kam genug
zusätzlicher Speicherdruck dazu, um über den Standard-Heap hinauszugehen. Fix: `jvmArgument '-Xmx4G'`
für `client`/`server`/`gameTestServer` in `build.gradle` ergänzt, danach `./gradlew neoForgeIdeSync`
laufen lassen, damit `.vscode/launch.json` bzw. die davon referenzierten
`build/moddev/*RunVmArgs.txt`-Dateien (die tatsächlich von VS Codes RunClient gelesen werden) neu
generiert wurden - ein reines Bearbeiten von `build.gradle` allein hätte nicht gereicht, diese
Dateien werden nur bei einem IDE-Sync neu geschrieben.

**Absturz blieb auch danach bestehen** - beide bisherigen Fixes waren zwar berechtigt, trafen aber
nicht die eigentliche Ursache. Timing-Analyse (Absturz 1-2s nach Schließen des Datei-Dialogs, also
noch VOR dem Hochladen-Klick) zeigte die echte Lücke: die Bildmaße-Prüfung in `runFileDialog` hängt an
`if (readers.hasNext())` - findet Javas `ImageIO` für das exakte Dateiformat keinen passenden Reader
(kommt bei manchen JPEG-Varianten/Smartphone-Kamera-Formaten vor), wird die gesamte Prüfung
STILLSCHWEIGEND übersprungen, die Datei landet ungeprüft beim eigentlichen Dekodieren. Das nutzt aber
`NativeImage.read` (STB-Dekoder, liest ein breiteres Formatspektrum als ImageIO), gelingt also trotzdem
- mit einem entsprechend riesigen decodierten Bild als Resultat. Fix: zweite, bedingungslose
Maße-Prüfung direkt nach dem Dekodieren in `onFilePicked`, die nicht von Format-Erkennung abhängt und
daher nicht auf dieselbe Weise übersprungen werden kann - `image.close()` + Ablehnung, bevor überhaupt
eine `DynamicTexture` (der eigentlich teure GPU-Upload-Schritt) erzeugt wird.

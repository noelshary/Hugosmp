# Hugo Auktion 1.2.2 (Minecraft 1.21.11, Fabric)

## Neu in 1.2.2
- Erstellen-Menü aufgeräumt: nur noch das Wichtige, keine Beispieltexte mehr

## 1.2.1

## Neu in 1.2.1
- Gesamtbetrag: Summe aller Zahlungen einer Auktion (auch zu niedrige) im HUD ("Gesamt $X"), im Live-Menü und im Chat am Ende
- Gewinnername kopieren: Im Chat steht am Ende "[Namen kopieren]" zum Anklicken, im Ergebnis-Menü gibt es den Button "Namen kopieren"

## 1.2.0

## Neu in 1.2.0
- Taste J: ohne laufende Auktion öffnet sich das Erstellen-Menü, mit laufender Auktion das **Live-Menü**
- Live-Menü: großes Item, Timer, Top 5 mit wachsenden Balken und Krone für Platz 1, Sofortkauf-Fortschritt, letzte Zahlungen, Funken bei jeder Zahlung
- Ergebnis-Ansicht mit Konfetti, wenn die Auktion bei offenem Menü endet
- Erstellen-Menü neu gestaltet (Karten, Glühen, Goldstaub, aktive Schnellwahl markiert)
- HUD: Lichtstreifen, schwebendes Item, aufsteigender Betrag, roter Puls in den letzten 10 Sekunden

# Version 1.1.0

## Neu in 1.1.0
- Neues HUD: Panel fährt von oben ein, Zeitbalken mit Farbwechsel, Timer pulsiert in den letzten 10 Sekunden
- Top 3 mit Gold/Silber/Bronze, das höchste Gebot zählt animiert hoch
- Bei jeder Zahlung: Rahmen blitzt auf, kleine Meldung fährt ein ("Neuer Führender", "Gebot erhöht", "Zu niedrig"), Sound
- Mindestgebot (Zahlungen darunter zählen nicht)
- Sofortkauf: Wer den Betrag zahlt, gewinnt sofort
- Dauer frei in Sekunden einstellbar (plus Schnellwahl 30 s / 1 / 3 / 5 / 10 Min)
- Siegerkarte mit Funken am Ende
- Klein im HUD: "Bieten per /pay Noel_shary"
- Einstellungen werden gemerkt

Beträge im Menü: `100k`, `1,5m`, `1.000.000`, `2.5m`.

## Bauen (ohne Java auf dem PC): GitHub Actions
1. Alle Dateien dieses Ordners in dein GitHub-Repo hochladen (inkl. Ordner `.github`).
2. Auf GitHub: Reiter **Actions** -> Workflow **build** -> fertig gelaufen anklicken.
3. Unten bei **Artifacts** `hugoauction-jar` herunterladen. Darin liegt die .jar.
4. Die .jar in `.minecraft/mods` legen (alte hugoauction-Jars löschen).

## Bauen auf dem PC
Java 21 und Gradle 9.2.1 installieren, dann im Ordner: `gradle build`.
Die Jar liegt danach in `build/libs/`.

## Config
`.minecraft/config/hugoauction-neu.json` (u.a. `payName`, `cumulative`, `announceInChat`, `sounds`, `paymentPattern`).
Standardtaste fürs Menü: J (unter Steuerung änderbar).

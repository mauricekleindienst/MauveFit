# Mauve Fit: Roadmap

Stand: Version 1.14.1 (November 2026). Sortiert nach Nutzen für das Training, nicht nach Aufwand.

## Erledigt in 1.6

- Supersätze, Satztypen (Drop-Satz, bis Versagen) mit RPE, Notizen pro Übung
- 1RM-Verlauf pro Übung, Vorlagen Upper/Lower, Push/Pull/Legs, Home ohne Geräte, Balance-Tipp des Coachs
- Ernährungstagebuch mit Tageszielen, Wasser-Tracker, 80 Rezepte mit Filtern, eigene Rezepte, Einkaufsliste teilen
- Körpermaße mit Verlauf, Muskel-Heatmap, Monatsrückblick, Startbildschirm-Widget
- Training von der Uhr starten, auch wenn das Handy im Hintergrund ist

## Erledigt in 1.7 und 1.8

- Geräteauswahl (nur Maschinen, ohne Körpergewicht ...), Alternativen pro Gerät, Plan-Menü per Gedrückthalten
- Supersätze auf der Uhr, Satztyp/RPE im Verlauf, Vorlagen Kraft-Basis und vier Grundübungen
- Ausführung im Training, Training teilen, Fortschrittsfotos, Akzentfarbe vom Hintergrundbild
- Trainingsblöcke über 4 oder 6 Wochen (Grundlage, Aufbau, Spitze, Entlastung)
- Erholung von der Uhr über Health Connect (Schlaf, Ruhepuls, HRV)
- Barcode-Scanner mit Open Food Facts

## Erledigt in 1.9 und 1.10

- Kachel für die Uhr, Wochen-Challenges, Streak-Joker, Rekorde als Bild teilen, CSV-Export
- Trends 2026: Auto-Regulation nach Erholung, Spracheingabe für Sätze, Cardio und Hybrid-Training (auch von der Uhr), zyklusbasiertes Training (optional, nur auf dem Gerät)
- Unit-Tests für die Trainingslogik (40 Tests)

## Erledigt in 1.11

- Drei Widgets in mehreren Größen: Nächstes Training (2x1 bis 4x3, Start-Knopf), Ernährung heute (Wasser-Knopf), Serie und Erholung
- App-Shortcuts am Icon: Training starten, Schnelltraining, Glas Wasser, Werkzeuge
- Werkzeuge: Intervall-Timer (EMOM, Tabata, eigene), 1RM-, Scheiben- und Aufwärmrechner
- Zielgewicht mit Tempo, Prognose und Warnung bei zu schnellem Tempo

## Erledigt in 1.12

- Neues Intro in 9 Schritten: Ziel, Erfahrung, Tage und Dauer, Ausstattung, Fokus, Einschränkungen, Körperdaten, Verbinden, Plan; Backup-Wiederherstellung direkt am Start
- Plan-Generator nutzt Trainingsdauer, Fokus-Muskeln und Gelenk-Einschränkungen
- Analysen: Kalender-Heatmap, Sätze pro Muskel gegen 10–20-Bereich, Kraftkurven mit Plateau-Erkennung, Einheiten, Rekorde, Ernährung, Körper, Anstrengung
- Wochenbericht montags als Benachrichtigung und Karte (80 Unit-Tests)

## Erledigt in 1.13

- Tablet- und Foldable-Layout: Navigationsleiste links, Startseite und Analysen zweispaltig, Übungen als Liste mit Detail, Training mit Animation daneben
- Live-Update unter Android 16 mit Fortschrittsbalken (Übungen, Pause)
- Absturzberichte lokal speichern, teilen oder löschen
- Barrierefreiheit: TalkBack-Beschriftungen für Sätze, Werte und Karten; getestet mit 130 % Schriftgröße

## Erledigt in 1.14

- Trainings und Körpergewicht in Health Connect schreiben (optional, letzte 90 Tage, Änderungen und Löschungen werden übernommen)

## Erledigt in 1.14.1

- Fehlerjagd: Leichte Woche, Blockphase und schlechte Erholung bleiben nicht mehr im nächsten Training hängen
- Datendatei wird bei einem Lesefehler aus der letzten guten Kopie wiederhergestellt statt geleert
- Widgets springen um Mitternacht auf den neuen Tag, Erinnerungen folgen Zeitzonenwechseln
- Uhr nutzt dieselbe Pausenregel wie das Handy, Cardio-Distanz ohne Doppelzählung

## Als Nächstes

- **Uhr-App auf echter Galaxy Watch testen.** Bisher nur gebaut, nicht auf einem Gerät oder Wear-Emulator gelaufen.

## Coach


## Ernährung


## Fortschritt


## Plattform

- **Health Connect:** Herzfrequenz der Uhr während des Trainings speichern.
- **Tile und Complication für die Uhr:** Wochenziel und Schnellstart direkt vom Zifferblatt.
- **Cloud-Backup optional** (Google Drive), weiterhin ohne Konto in der App.

## Design und Bedienung

- **Animierte Übergänge** zwischen Liste und Übungsdetail (Shared Element).
- **Barrierefreiheit:** TalkBack-Beschriftungen prüfen, große Schrift testen, Kontrast im hellen Modus messen.
- **Weitere Sprachen:** Der Datensatz hat Anleitungen in 10 Sprachen; Spanisch, Französisch, Italienisch, Polnisch und Türkisch lassen sich übernehmen.

## Technik

- **Tests:** Unit-Tests für Coach, Statistik und Ernährung; UI-Tests für den Trainingsablauf.
- **Play-Store-Vorbereitung:** eigener Release-Key, Datenschutzerklärung, Store-Grafiken, Lizenz der Animationen bei Gym visual klären.

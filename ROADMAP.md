# Mauve Fit: Roadmap

Stand: Version 1.6.0 (November 2026). Sortiert nach Nutzen für das Training, nicht nach Aufwand.

## Erledigt in 1.6

- Supersätze, Satztypen (Drop-Satz, bis Versagen) mit RPE, Notizen pro Übung
- 1RM-Verlauf pro Übung, Vorlagen Upper/Lower, Push/Pull/Legs, Home ohne Geräte, Balance-Tipp des Coachs
- Ernährungstagebuch mit Tageszielen, Wasser-Tracker, 80 Rezepte mit Filtern, eigene Rezepte, Einkaufsliste teilen
- Körpermaße mit Verlauf, Muskel-Heatmap, Monatsrückblick, Startbildschirm-Widget
- Training von der Uhr starten, auch wenn das Handy im Hintergrund ist

## Als Nächstes (1.7)

- **Supersätze auf der Uhr:** Satz abhaken von der Uhr folgt noch dem einfachen Pausentimer.
- **Satztyp- und RPE-Markierungen** auch im Verlauf und in der Zusammenfassung.
- **Vorlage 5/3/1 und Starting Strength.**
- **Uhr-App auf echter Galaxy Watch testen.** Bisher nur gebaut, nicht auf einem Gerät oder Wear-Emulator gelaufen.

## Coach

- **Periodisierung über Wochen:** Blöcke mit Aufbau, Intensität und Deload, automatisch geplant statt nur bei Stagnation.
- **Erholung aus Uhr-Daten:** Ruhepuls und Schlaf (Health Connect) fließen in den Tagesvorschlag ein.
- **Technik-Hinweise pro Übung** aus den Schritten des Datensatzes als kurze Merksätze im Training.

## Ernährung

- **Barcode-Scanner** über eine offene Lebensmitteldatenbank (Open Food Facts).

## Fortschritt

- **Fortschrittsfotos,** nur lokal gespeichert.

## Plattform

- **Health Connect:** Trainings und Gewicht synchronisieren, Herzfrequenz der Uhr speichern.
- **Pause-Timer als Live-Update** (Android 16 Progress-Benachrichtigung).
- **Tile und Complication für die Uhr:** Wochenziel und Schnellstart direkt vom Zifferblatt.
- **Cloud-Backup optional** (Google Drive), weiterhin ohne Konto in der App.
- **Tablet- und Foldable-Layout** mit zwei Spalten.

## Design und Bedienung

- **Animierte Übergänge** zwischen Liste und Übungsdetail (Shared Element).
- **Dynamische Farben** von Android als zusätzliche Akzentoption.
- **Barrierefreiheit:** TalkBack-Beschriftungen prüfen, große Schrift testen, Kontrast im hellen Modus messen.
- **Weitere Sprachen:** Der Datensatz hat Anleitungen in 10 Sprachen; Spanisch, Französisch, Italienisch, Polnisch und Türkisch lassen sich übernehmen.

## Technik

- **Tests:** Unit-Tests für Coach, Statistik und Ernährung; UI-Tests für den Trainingsablauf.
- **Play-Store-Vorbereitung:** eigener Release-Key, Datenschutzerklärung, Store-Grafiken, Lizenz der Animationen bei Gym visual klären.
- **Absturzberichte** lokal sammeln und optional exportieren.

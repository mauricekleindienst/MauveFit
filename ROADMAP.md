# Mauve Fit: Roadmap

Stand: Version 1.5.0 (November 2026). Sortiert nach Nutzen für das Training, nicht nach Aufwand.

## Als Nächstes (1.6)

- **Training von der Uhr starten, auch wenn das Handy im Hintergrund ist.** Android 12+ blockiert dann den Vordergrunddienst; die laufende Trainings-Benachrichtigung fehlt. Lösung: Start über eine Benachrichtigung mit Aktion oder über einen erlaubten Ausnahmepfad.
- **Uhr-App auf echter Galaxy Watch testen.** Bisher nur gebaut, nicht auf einem Gerät oder Wear-Emulator gelaufen.
- **Supersätze und Zirkel.** Zwei oder mehr Übungen abwechselnd, mit gemeinsamer Pause.
- **Satztypen:** Drop-Satz, bis Muskelversagen, RPE/RIR pro Satz eintragen; der Coach nutzt RPE für die Steigerung.
- **Notizen pro Übung,** die beim nächsten Mal oben im Training stehen (Sitzhöhe, Griff, Schmerzen).

## Coach

- **Periodisierung über Wochen:** Blöcke mit Aufbau, Intensität und Deload, automatisch geplant statt nur bei Stagnation.
- **Erholung aus Uhr-Daten:** Ruhepuls und Schlaf (Health Connect) fließen in den Tagesvorschlag ein.
- **Schwachstellen-Analyse:** Verhältnis Drücken zu Ziehen, Beine zu Oberkörper, mit konkreten Übungsvorschlägen.
- **Technik-Hinweise pro Übung** aus den Schritten des Datensatzes als kurze Merksätze im Training.
- **Trainingsplan-Vorlagen:** Upper/Lower, PPL 6x, 5/3/1, Starting Strength, Home ohne Geräte.

## Ernährung

- **Mahlzeiten-Tagebuch** mit Kalorien und Protein pro Tag, gegen die Tagesziele.
- **Barcode-Scanner** über eine offene Lebensmitteldatenbank (Open Food Facts).
- **Rezepte filtern:** vegetarisch, vegan, schnell (unter 15 min), Frühstück, Snack.
- **Eigene Rezepte anlegen** und in die Einkaufsliste übernehmen.
- **Einkaufsliste teilen** als Text an Messenger oder Notizen.

## Fortschritt

- **Diagramme pro Übung:** geschätztes 1RM und Volumen über Monate.
- **Körpermaße** (Taille, Brust, Arme) und Fortschrittsfotos, nur lokal gespeichert.
- **Monats- und Jahresrückblick** mit den wichtigsten Rekorden.
- **Heatmap** der trainierten Muskeln über die letzten 4 Wochen.

## Plattform

- **Health Connect:** Trainings und Gewicht synchronisieren, Herzfrequenz der Uhr speichern.
- **Widgets:** nächstes Training und Wochenziel auf dem Startbildschirm; Pause-Timer als Live-Benachrichtigung.
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

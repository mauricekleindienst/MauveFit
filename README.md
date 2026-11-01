# Mauve Fit

Android-Fitness-App (Kotlin, Jetpack Compose) mit Wear-OS-App für die Galaxy Watch.

- Intro: Ziel, Level, Trainingstage und Ausstattung; der Coach erstellt daraus den Plan.
- Coach: Hinweise zu Erholung, Wochenziel, Plateaus, Pausen und Rekorden; Tipp pro Übung im Training.
- Training im Fokus-Modus: Satz-Tracking, Pausentimer, Übung tauschen, Aufwärmsätze, Scheibenrechner.
- Schnelltraining nach Bereich und Dauer, Rekorde, Körpergewicht, Verlauf.
- Galaxy Watch (`wear/`): aktuelle Übung, Satz abhaken, Gewicht/Wdh. anpassen, Pausentimer mit Vibration, Puls.
  Synchronisiert über die Wear-OS-Datenschicht; beide APKs müssen mit demselben Schlüssel signiert sein.

Daten: [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) (MIT), gekürzt in
`app/src/main/assets/exercises.json`. Animationen © Gym visual (https://gymvisual.com/), zur Laufzeit geladen;
für eine Veröffentlichung braucht es eine eigene Lizenz von Gym visual.

Build: `gradlew :app:assembleRelease :wear:assembleRelease` (JDK aus Android Studio).
Watch installieren: Entwickleroptionen und ADB-Debugging über WLAN auf der Uhr aktivieren, dann
`adb connect <ip>:<port>` und `adb install MauveFit-Watch-1.0.apk`.

# Subnetz-Rechner (SubnetCalc)

Eine moderne Android-App zur IPv4-Subnetz-Berechnung für den IT-Alltag —
gebaut mit **Kotlin**, **Jetpack Compose** und **Material 3**.

## Funktionen

- **Eingabe von IP-Adresse und Netzmaske** — die Maske wahlweise als
  CIDR-Präfix (`24`, `/24`) oder in Punktnotation (`255.255.255.0`),
  mit Schnellauswahl-Chips für gängige Präfixe.
- **Live-Berechnung** aller wichtigen Kennwerte:
  - Netzadresse und Broadcast-Adresse
  - Erster und letzter nutzbarer Host
  - Anzahl nutzbarer Hosts und Gesamtadressen
  - Netzmaske, Wildcard-Maske und Binärdarstellung
  - Adresstyp (Privat/Öffentlich/Loopback/APIPA/CGN/…) und Adressklasse (A–E)
- **Alle möglichen Subnetze**: Das eingegebene Netz lässt sich per Slider in
  kleinere Subnetze aufteilen — die App listet jedes Subnetz mit Netzadresse,
  Host-Bereich und Broadcast auf (performant auch bei zehntausenden Subnetzen).
- Sonderfälle korrekt behandelt: `/31` (RFC 3021, Punkt-zu-Punkt) und `/32`
  (Hostroute).
- **Modernes Design**: Material 3, dynamische Farben (Android 12+),
  automatischer Hell-/Dunkelmodus, Edge-to-Edge.

## Aufbau

| Modul   | Inhalt                                                        |
|---------|---------------------------------------------------------------|
| `:core` | Reine Kotlin-Berechnungslogik (`SubnetCalculator`), mit Unit-Tests |
| `:app`  | Android-App (Jetpack Compose UI)                              |

## Bauen

Voraussetzungen: JDK 17, Android SDK (compileSdk 35).

```bash
./gradlew :core:test          # Unit-Tests der Berechnungslogik
./gradlew :app:assembleDebug  # Debug-APK bauen
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## CI

Bei jedem Push baut GitHub Actions die App, führt die Unit-Tests aus und lädt
die Debug-APK als Artefakt hoch (Actions → Lauf auswählen → Artifacts →
`SubnetCalc-debug`).

Mindest-Android-Version: 8.0 (API 26).

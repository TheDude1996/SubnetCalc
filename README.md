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
- **Alle möglichen Subnetze**: Das eingegebene Netz lässt sich aufteilen —
  wahlweise per Slider über die neue Präfixlänge **oder über die Anzahl
  benötigter Hosts pro Subnetz** (die App ermittelt dann das kleinste passende
  Subnetz). Jedes Subnetz wird mit Netzadresse, Host-Bereich und Broadcast
  aufgelistet (performant auch bei zehntausenden Subnetzen).
- Sonderfälle korrekt behandelt: `/31` (RFC 3021, Punkt-zu-Punkt) und `/32`
  (Hostroute).
- **Eigenes „Neon Ops“-Design**: durchgehend dunkle Mitternachtsblau-Töne mit
  Neon-Grün- und Neon-Pink-Akzenten — Verlaufshintergrund, leuchtende
  Gradient-Rahmen mit Puls-Animation, Monospace-Typografie und eine
  **Bit-Visualisierung**, die Netzanteil (grün) und Hostanteil (pink) der
  Adresse bitgenau zeigt. Material 3, Edge-to-Edge.

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
die signierte Release-APK als Artefakt hoch (Actions → Lauf auswählen →
Artifacts → `SubnetCalc-release`). Die Versionsnummer zählt mit jedem Lauf
hoch, sodass sich jede APK als Update über die vorherige installieren lässt.

Builds auf dem Standard-Branch und manuell gestartete Läufe werden zusätzlich als
[GitHub-Release](../../releases) mit der APK veröffentlicht.

Die Signatur stammt aus vier Repository-Secrets (Settings → Secrets and
variables → Actions): `KEYSTORE_BASE64` (Keystore, Base64-kodiert),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD`. Fehlen sie (etwa in
Forks), baut CI ersatzweise eine Debug-APK `SubnetCalc-debug`.

Mindest-Android-Version: 8.0 (API 26).

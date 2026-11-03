# Mauve Fit for iOS

A native SwiftUI port of the Android app, built entirely in GitHub Actions (no Mac needed).

## What is in it

- **Data**: the same `data.json` model as Android (`MauveFit/Data/Models.swift` mirrors
  `app/.../data/Models.kt`). Field names match, unknown fields are ignored and missing ones fall
  back to defaults, so an Android backup can be read. The file is written 300 ms after each
  change and atomically; a copy from the last good start is kept as `data.prev.json`, and a file
  that cannot be read is moved aside instead of being overwritten.
- **Exercise database**: `exercises.json`, `exercises_de.json` and `exercise_names_de.json` are
  not duplicated. A build phase copies them from `app/src/main/assets/` into the app bundle, so
  both apps always ship the same data. Stills load from the dataset's CDN.
- **Four tabs**
  - *Heute / Today*: the running workout or the next plan (the one rested longest), the week
    with goal and streak, and today's food.
  - *Training*: the starter plans (Push, Pull, Legs, Full body, Home), new and edited plans,
    free workout, the searchable exercise library with how-to steps in German or English.
  - *Essen / Food*: food diary per day against kcal and macro targets from body weight and goal,
    and water.
  - *Fortschritt / Progress*: totals, weekly volume chart, body weight log and chart, estimated
    1RM records, and the history with every set of every workout.
- **Workout screen**: sets with weight and reps (minus, plus, or tap to type), warm-up sets,
  add or remove sets and exercises, last time's numbers, a rest timer that survives the app being
  closed (stored as `restEndsAt`, like Android) with a local notification when it runs out, and
  finish or discard. The next start of a plan suggests one weight step more when every set hit
  its target.
- German and English, following the system or the setting (Today, gear icon).
- Dark "iron" palette with the sage accent, cards and pill buttons. No emojis, SF Symbols only.

## Still open

Compared with the Android app, these are not ported yet:

- Intro / onboarding (goal, level, days, setup, equipment, focus, limits) and the coach
  (generated plans, block periodisation, deload, readiness, swap suggestions)
- Quests, XP, coins, shop, titles and premium accents
- Widgets (streak, nutrition, next workout) and Live Activities for the rest timer
- Apple Watch app (the Android side has a Wear OS app)
- Supersets in the workout screen (the data field is kept), drop / failure set types and RPE
- Cardio sessions, Apple Health sync
- Recipes, meal prep, shopping list, barcode scanner and food lookup
- Insights, body measurements, progress photos, share images
- Exercise animations (GIFs; only the stills are shown), template packs
- Backup import / export (the format already matches) and the in-app updater
- Light theme and other accents
- App icon

## Building

CI does everything (`.github/workflows/ios.yml`): on every push to `main` that touches `ios/`
(or the exercise data, or the workflow) and on manual runs, a macOS runner installs
[XcodeGen](https://github.com/yonaskolb/XcodeGen), generates `MauveFit.xcodeproj` from
`project.yml`, runs the unit tests on an iOS simulator, and builds an unsigned Release
`MauveFit-unsigned.ipa`, uploaded as the workflow artifact **MauveFit-unsigned-ipa**.

The `.xcodeproj` is never edited or committed; change `project.yml` instead. On a Mac:

```bash
brew install xcodegen
cd ios && xcodegen generate && open MauveFit.xcodeproj
```

To get the latest ipa from Windows:

```bash
gh run download --repo mauricekleindienst/MauveFit --name MauveFit-unsigned-ipa
```

## Installing the unsigned ipa

iOS only runs signed apps, so the ipa has to be signed for your device when it is installed.
With a free Apple ID this works through a sideloading tool that signs it on your PC:

- **Sideloadly** (Windows): install iTunes and iCloud from Apple's website (not the Microsoft
  Store versions), connect the iPhone by USB, drop the ipa into Sideloadly, enter your Apple ID
  and press Start. On the phone, allow the developer under *Settings > General > VPN & Device
  Management*, and turn on *Developer Mode* (*Settings > Privacy & Security*) if iOS asks.
- **AltStore** (AltServer on Windows): install AltServer, install AltStore onto the phone with
  it, then open the ipa in AltStore on the phone ("My Apps", plus button). AltStore refreshes the
  signature over Wi-Fi while AltServer runs on the PC.

Limits of a free Apple ID: the signature expires after **7 days** (re-sign with the tool; your
data stays as long as the app is not deleted), at most **3 sideloaded apps** at a time, and no
push notifications or iCloud entitlements. The local rest notification works.

## What a paid Apple Developer account would add

The Apple Developer Program costs 99 USD a year and would allow:

- **TestFlight**: CI uploads each build to App Store Connect, and the app installs and updates
  from the TestFlight app, without a PC, valid for 90 days per build, for up to 10,000 testers.
- **Signing in CI**: a distribution certificate (`.p12`) and provisioning profile stored as
  GitHub secrets (for example `IOS_CERT_P12_BASE64`, `IOS_CERT_PASSWORD`,
  `IOS_PROFILE_BASE64`), imported into a temporary keychain in the workflow, and
  `xcodebuild archive` plus `-exportArchive` instead of the unsigned build. An App Store Connect
  API key (`.p8`, also as a secret) lets the workflow upload to TestFlight with
  `xcrun altool` or fastlane.
- Signatures valid for a year instead of 7 days, ad-hoc installs on up to 100 registered
  devices, and capabilities such as App Groups (needed for widgets sharing data with the app),
  HealthKit, push notifications and later the App Store.

None of this is set up yet; the workflow only builds the unsigned ipa.
